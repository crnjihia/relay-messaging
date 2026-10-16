package com.relay.repository;

import com.relay.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface MessageRepository extends ReactiveMongoRepository<Message, String> {

    Flux<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    @Query("{ 'conversationId': ?0, 'createdAt': { $lt: ?1 } }")
    Flux<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            String conversationId, Instant before, Pageable pageable);

    Mono<Long> countByConversationId(String conversationId);
}
