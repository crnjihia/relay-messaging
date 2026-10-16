package com.relay.service;

import com.relay.domain.ReadReceipt;
import com.relay.dto.ReadReceiptResponse;
import com.relay.exception.ForbiddenException;
import com.relay.exception.ResourceNotFoundException;
import com.relay.repository.ConversationRepository;
import com.relay.repository.ReadReceiptRepository;
import com.relay.websocket.RedisPubSubBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class ReadReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReadReceiptService.class);

    private final ReadReceiptRepository readReceiptRepository;
    private final ConversationRepository conversationRepository;
    private final RedisPubSubBridge redisPubSubBridge;

    public ReadReceiptService(ReadReceiptRepository readReceiptRepository,
                              ConversationRepository conversationRepository,
                              RedisPubSubBridge redisPubSubBridge) {
        this.readReceiptRepository = readReceiptRepository;
        this.conversationRepository = conversationRepository;
        this.redisPubSubBridge = redisPubSubBridge;
    }

    public Mono<ReadReceiptResponse> markRead(String userId, String conversationId, String messageId) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Conversation not found: " + conversationId)))
                .flatMap(conversation -> {
                    if (!conversation.getMemberIds().contains(userId)) {
                        return Mono.error(new ForbiddenException("User is not a member of conversation: " + conversationId));
                    }

                    return readReceiptRepository.findByConversationIdAndUserId(conversationId, userId)
                            .defaultIfEmpty(new ReadReceipt(null, conversationId, userId, messageId, Instant.now()))
                            .flatMap(receipt -> {
                                receipt.setLastReadMessageId(messageId);
                                receipt.setReadAt(Instant.now());
                                return readReceiptRepository.save(receipt);
                            })
                            .flatMap(saved -> {
                                ReadReceiptResponse response = ReadReceiptResponse.fromDomain(saved);
                                return redisPubSubBridge.publishReceipt(conversationId, response)
                                        .thenReturn(response);
                            });
                });
    }

    public Flux<ReadReceiptResponse> getConversationReceipts(String conversationId, String userId) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Conversation not found: " + conversationId)))
                .flatMapMany(conversation -> {
                    if (!conversation.getMemberIds().contains(userId)) {
                        return Flux.error(new ForbiddenException("User is not a member of conversation: " + conversationId));
                    }
                    return readReceiptRepository.findByConversationId(conversationId)
                            .map(ReadReceiptResponse::fromDomain);
                });
    }
}
