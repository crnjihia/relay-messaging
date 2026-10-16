package com.relay.repository;

import com.relay.domain.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {

    Mono<User> findByUsernameIgnoreCase(String username);

    Mono<User> findByEmailIgnoreCase(String email);

    Mono<Boolean> existsByUsernameIgnoreCase(String username);

    Mono<Boolean> existsByEmailIgnoreCase(String email);

    Flux<User> findByIdIn(Collection<String> ids);
}
