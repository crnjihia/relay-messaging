package com.relay.config;

import com.relay.domain.Conversation;
import com.relay.domain.Message;
import com.relay.domain.ReadReceipt;
import com.relay.domain.User;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.bson.Document;
import reactor.core.publisher.Mono;

@Configuration
@EnableReactiveMongoRepositories(basePackages = "com.relay.repository")
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    private final ReactiveMongoTemplate mongoTemplate;

    public MongoConfig(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void ensureIndexes() {
        // Users indexes
        mongoTemplate.indexOps(User.class)
                .ensureIndex(new Index().on("username", Sort.Direction.ASC).unique())
                .onErrorResume(err -> Mono.empty())
                .subscribe();

        mongoTemplate.indexOps(User.class)
                .ensureIndex(new Index().on("email", Sort.Direction.ASC).unique())
                .onErrorResume(err -> Mono.empty())
                .subscribe();

        // Conversations indexes
        mongoTemplate.indexOps(Conversation.class)
                .ensureIndex(new Index().on("memberIds", Sort.Direction.ASC))
                .onErrorResume(err -> Mono.empty())
                .subscribe();

        mongoTemplate.indexOps(Conversation.class)
                .ensureIndex(new Index().on("lastMessageAt", Sort.Direction.DESC))
                .onErrorResume(err -> Mono.empty())
                .subscribe();

        // Messages compound index on (conversationId, createdAt DESC)
        Document messageCompoundKeys = new Document("conversationId", 1).append("createdAt", -1);
        mongoTemplate.indexOps(Message.class)
                .ensureIndex(new CompoundIndexDefinition(messageCompoundKeys).named("conv_created_idx"))
                .onErrorResume(err -> Mono.empty())
                .subscribe();

        // ReadReceipts unique compound index on (conversationId, userId)
        Document receiptCompoundKeys = new Document("conversationId", 1).append("userId", 1);
        mongoTemplate.indexOps(ReadReceipt.class)
                .ensureIndex(new CompoundIndexDefinition(receiptCompoundKeys).named("conv_user_unique_idx").unique())
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }
}
