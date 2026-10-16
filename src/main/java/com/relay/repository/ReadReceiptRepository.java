package com.relay.repository;

import com.relay.domain.ReadReceipt;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ReadReceiptRepository extends ReactiveMongoRepository<ReadReceipt, String> {

    Mono<ReadReceipt> findByConversationIdAndUserId(String conversationId, String userId);

    Flux<ReadReceipt> findByConversationId(String conversationId);
}
