package com.relay.service;

import com.relay.domain.Conversation;
import com.relay.domain.ConversationType;
import com.relay.dto.ConversationResponse;
import com.relay.dto.CreateConversationRequest;
import com.relay.dto.MessageResponse;
import com.relay.dto.UserResponse;
import com.relay.exception.ForbiddenException;
import com.relay.exception.RelayException;
import com.relay.exception.ResourceNotFoundException;
import com.relay.repository.ConversationRepository;
import com.relay.repository.MessageRepository;
import com.relay.repository.ReadReceiptRepository;
import com.relay.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ReadReceiptRepository readReceiptRepository;
    private final UserRepository userRepository;
    private final RateLimiterService rateLimiterService;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               ReadReceiptRepository readReceiptRepository,
                               UserRepository userRepository,
                               RateLimiterService rateLimiterService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.readReceiptRepository = readReceiptRepository;
        this.userRepository = userRepository;
        this.rateLimiterService = rateLimiterService;
    }

    public Mono<ConversationResponse> createConversation(String creatorId, CreateConversationRequest request) {
        // Enforce rate limit: 5 conversations/day created per user
        return rateLimiterService.checkConversationCreationRateLimit(creatorId)
                .then(Mono.defer(() -> {
                    Set<String> memberSet = new HashSet<>(request.memberIds());
                    memberSet.add(creatorId);
                    List<String> finalMembers = new ArrayList<>(memberSet);

                    if (request.type() == ConversationType.DM) {
                        if (finalMembers.size() != 2) {
                            return Mono.error(new RelayException("Direct conversations (DM) must have exactly 2 distinct members"));
                        }

                        // Check if DM between these two members already exists
                        return conversationRepository.findDirectConversationBetween(ConversationType.DM, finalMembers)
                                .flatMap(this::enrichConversation)
                                .switchIfEmpty(createNewConversation(creatorId, request, finalMembers));
                    } else {
                        if (request.name() == null || request.name().isBlank()) {
                            return Mono.error(new RelayException("Group conversations must have a name"));
                        }
                        return createNewConversation(creatorId, request, finalMembers);
                    }
                }));
    }

    private Mono<ConversationResponse> createNewConversation(String creatorId, CreateConversationRequest request, List<String> memberIds) {
        return userRepository.findByIdIn(memberIds)
                .collectList()
                .flatMap(foundUsers -> {
                    if (foundUsers.size() != memberIds.size()) {
                        return Mono.error(new RelayException("One or more member IDs do not exist"));
                    }

                    Conversation conversation = new Conversation();
                    conversation.setType(request.type());
                    conversation.setName(request.type() == ConversationType.DM ? null : request.name().trim());
                    conversation.setMemberIds(memberIds);
                    conversation.setCreatedBy(creatorId);
                    conversation.setCreatedAt(Instant.now());
                    conversation.setLastMessageAt(Instant.now());

                    return conversationRepository.save(conversation)
                            .flatMap(this::enrichConversation);
                });
    }

    public Flux<ConversationResponse> listUserConversations(String userId) {
        return conversationRepository.findByMemberIdsContainingOrderByLastMessageAtDesc(userId)
                .flatMap(this::enrichConversation);
    }

    public Mono<Conversation> getConversationEntity(String conversationId, String userId) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Conversation not found: " + conversationId)))
                .flatMap(conversation -> {
                    if (!conversation.getMemberIds().contains(userId)) {
                        return Mono.error(new ForbiddenException("User is not a member of conversation: " + conversationId));
                    }
                    return Mono.just(conversation);
                });
    }

    public Mono<ConversationResponse> getConversation(String conversationId, String userId) {
        return getConversationEntity(conversationId, userId)
                .flatMap(this::enrichConversation);
    }

    public Mono<ConversationResponse> addMembers(String conversationId, String requesterId, List<String> newMemberIds) {
        return getConversationEntity(conversationId, requesterId)
                .flatMap(conv -> {
                    if (conv.getType() == ConversationType.DM) {
                        return Mono.error(new RelayException("Cannot add members to a Direct Message (DM) conversation"));
                    }

                    return userRepository.findByIdIn(newMemberIds)
                            .collectList()
                            .flatMap(foundUsers -> {
                                if (foundUsers.size() != newMemberIds.size()) {
                                    return Mono.error(new RelayException("One or more user IDs to add do not exist"));
                                }

                                Set<String> updatedMembers = new HashSet<>(conv.getMemberIds());
                                updatedMembers.addAll(newMemberIds);
                                conv.setMemberIds(new ArrayList<>(updatedMembers));

                                return conversationRepository.save(conv)
                                        .flatMap(this::enrichConversation);
                            });
                });
    }

    public Mono<ConversationResponse> removeMember(String conversationId, String requesterId, String targetUserId) {
        return getConversationEntity(conversationId, requesterId)
                .flatMap(conv -> {
                    if (conv.getType() == ConversationType.DM) {
                        return Mono.error(new RelayException("Cannot remove members from a Direct Message (DM)"));
                    }

                    if (!requesterId.equals(targetUserId) && !requesterId.equals(conv.getCreatedBy())) {
                        return Mono.error(new ForbiddenException("Only conversation creator or the member themselves can remove a member"));
                    }

                    List<String> members = new ArrayList<>(conv.getMemberIds());
                    members.remove(targetUserId);
                    if (members.isEmpty()) {
                        return conversationRepository.delete(conv).then(Mono.empty());
                    }

                    conv.setMemberIds(members);
                    return conversationRepository.save(conv)
                            .flatMap(this::enrichConversation);
                });
    }

    private static final MessageResponse EMPTY_MESSAGE =
            new MessageResponse(null, null, null, null, null, null, null, null, null, null, null);

    private Mono<ConversationResponse> enrichConversation(Conversation conv) {
        Mono<List<UserResponse>> membersMono = userRepository.findByIdIn(conv.getMemberIds())
                .map(UserResponse::fromDomain)
                .collectList();

        Mono<MessageResponse> lastMessageMono = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                        conv.getId(), PageRequest.of(0, 1))
                .next()
                .map(MessageResponse::fromDomain)
                .defaultIfEmpty(EMPTY_MESSAGE);

        return Mono.zip(membersMono, lastMessageMono)
                .map(tuple -> {
                    List<UserResponse> members = tuple.getT1();
                    MessageResponse msg = tuple.getT2();
                    MessageResponse lastMsg = (msg != null && msg != EMPTY_MESSAGE && msg.id() != null) ? msg : null;
                    return ConversationResponse.fromDomain(conv, members, lastMsg, 0);
                });
    }
}
