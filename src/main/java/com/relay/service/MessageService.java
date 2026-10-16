package com.relay.service;

import com.relay.domain.Message;
import com.relay.domain.User;
import com.relay.domain.UserStatus;
import com.relay.dto.MessageResponse;
import com.relay.dto.SendMessageRequest;
import com.relay.exception.ForbiddenException;
import com.relay.exception.RelayException;
import com.relay.exception.ResourceNotFoundException;
import com.relay.repository.ConversationRepository;
import com.relay.repository.MessageRepository;
import com.relay.repository.UserRepository;
import com.relay.websocket.RedisPubSubBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int MAX_TEXT_BYTES = 10 * 1024; // 10 KB

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final RateLimiterService rateLimiterService;
    private final RedisPubSubBridge redisPubSubBridge;
    private final PushNotificationService pushNotificationService;
    private final PresenceService presenceService;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          UserRepository userRepository,
                          RateLimiterService rateLimiterService,
                          RedisPubSubBridge redisPubSubBridge,
                          PushNotificationService pushNotificationService,
                          PresenceService presenceService) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.rateLimiterService = rateLimiterService;
        this.redisPubSubBridge = redisPubSubBridge;
        this.pushNotificationService = pushNotificationService;
        this.presenceService = presenceService;
    }

    public Mono<MessageResponse> sendMessage(String senderId, String conversationId, SendMessageRequest request) {
        // Enforce rate limit: 20 messages/min per user
        return rateLimiterService.checkMessageRateLimit(senderId)
                .then(Mono.defer(() -> {
                    // Check message size limit: 10 KB text
                    if (request.content() != null && request.content().getBytes().length > MAX_TEXT_BYTES) {
                        return Mono.error(new RelayException("Message text exceeds 10 KB limit"));
                    }

                    return conversationRepository.findById(conversationId)
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Conversation not found: " + conversationId)))
                            .flatMap(conversation -> {
                                if (!conversation.getMemberIds().contains(senderId)) {
                                    return Mono.error(new ForbiddenException("Sender is not a member of this conversation"));
                                }

                                return userRepository.findById(senderId)
                                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Sender not found: " + senderId)))
                                        .flatMap(sender -> {
                                            Instant now = Instant.now();
                                            Message message = new Message();
                                            message.setConversationId(conversationId);
                                            message.setSenderId(senderId);
                                            message.setContent(request.content());
                                            message.setContentType(request.resolvedContentType());
                                            message.setAttachments(request.attachments());
                                            message.setReplyToId(request.replyToId());
                                            message.setCreatedAt(now);

                                            return messageRepository.save(message)
                                                    .flatMap(savedMsg -> {
                                                        conversation.setLastMessageAt(now);
                                                        return conversationRepository.save(conversation).thenReturn(savedMsg);
                                                    })
                                                    .flatMap(savedMsg -> {
                                                        MessageResponse response = MessageResponse.fromDomain(savedMsg, sender.getDisplayName());

                                                        // Publish to Redis channel chat:conversation:{conversationId} for multi-instance fan-out
                                                        return redisPubSubBridge.publishMessage(conversationId, response)
                                                                .then(dispatchOfflinePushNotifications(conversation.getMemberIds(), sender, savedMsg))
                                                                .thenReturn(response);
                                                    });
                                        });
                            });
                }));
    }

    public Flux<MessageResponse> getMessages(String conversationId, String userId, String before, int limit) {
        int pageLimit = Math.max(1, Math.min(limit, 100));

        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Conversation not found: " + conversationId)))
                .flatMapMany(conversation -> {
                    if (!conversation.getMemberIds().contains(userId)) {
                        return Flux.error(new ForbiddenException("User is not a member of conversation: " + conversationId));
                    }

                    if (before == null || before.isBlank()) {
                        return messageRepository.findByConversationIdOrderByCreatedAtDesc(
                                conversationId, PageRequest.of(0, pageLimit))
                                .flatMap(this::enrichMessage);
                    }

                    // Resolve 'before' parameter: either ISO timestamp or message ID
                    return resolveCursorTimestamp(before)
                            .flatMapMany(beforeInstant ->
                                    messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                                            conversationId, beforeInstant, PageRequest.of(0, pageLimit))
                                            .flatMap(this::enrichMessage)
                            );
                });
    }

    private Mono<Instant> resolveCursorTimestamp(String cursor) {
        try {
            return Mono.just(Instant.parse(cursor));
        } catch (Exception e) {
            // Assume it's a message ID
            return messageRepository.findById(cursor)
                    .map(Message::getCreatedAt)
                    .defaultIfEmpty(Instant.now());
        }
    }

    private Mono<MessageResponse> enrichMessage(Message message) {
        return userRepository.findById(message.getSenderId())
                .map(u -> MessageResponse.fromDomain(message, u.getDisplayName()))
                .defaultIfEmpty(MessageResponse.fromDomain(message, "Unknown"));
    }

    private Mono<Void> dispatchOfflinePushNotifications(List<String> memberIds, User sender, Message message) {
        // Find all other members of the conversation
        return userRepository.findByIdIn(memberIds)
                .filter(member -> !member.getId().equals(sender.getId()))
                .flatMap(member ->
                        presenceService.getUserStatus(member.getId())
                                .flatMap(status -> {
                                    if (status != UserStatus.ONLINE) {
                                        log.debug("Member {} is {}, checking push notification dispatch", member.getUsername(), status);
                                        return pushNotificationService.sendPushIfOffline(member, message, sender.getDisplayName());
                                    }
                                    return Mono.empty();
                                })
                )
                .then();
    }
}
