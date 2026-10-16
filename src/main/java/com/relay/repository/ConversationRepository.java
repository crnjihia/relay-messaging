package com.relay.repository;

import com.relay.domain.Conversation;
import com.relay.domain.ConversationType;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface ConversationRepository extends ReactiveMongoRepository<Conversation, String> {

    Flux<Conversation> findByMemberIdsContainingOrderByLastMessageAtDesc(String userId);

    @Query("{ 'type': ?0, 'memberIds': { $all: ?1, $size: 2 } }")
    Mono<Conversation> findDirectConversationBetween(ConversationType type, List<String> memberIds);
}
