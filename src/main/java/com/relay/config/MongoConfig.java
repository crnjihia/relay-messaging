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
                .subscribe(idx -> log.debug("Created index: {}", idx),
                        err -> log.debug("Index ensure notice for users.username: {}", err.getMessage()));

        mongoTemplate.indexOps(User.class)
                .ensureIndex(new Index().on("email", Sort.Direction.ASC).unique())
                .subscribe(idx -> log.debug("Created index: {}", idx),
                        err -> log.debug("Index ensure notice for users.email: {}", err.getMessage()));

        // Conversations indexes
        mongoTemplate.indexOps(Conversation.class)
                .ensureIndex(new Index().on("memberIds", Sort.Direction.ASC))
                .subscribe(idx -> log.debug("Created index: {}", idx),
                        err -> log.debug("Index ensure notice for conversations.memberIds: {}", err.getMessage()));

        mongoTemplate.indexOps(Conversation.class)
                .ensureIndex(new Index().on("lastMessageAt", Sort.Direction.DESC))
                .subscribe(idx -> log.debug("Created index: {}", idx),
                        err -> log.debug("Index ensure notice for conversations.lastMessageAt: {}", err.getMessage()));

        // Messages compound index on (conversationId, createdAt DESC)
        Document messageCompoundKeys = new Document("conversationId", 1).append("createdAt", -1);
        mongoTemplate.indexOps(Message.class)
                .ensureIndex(new CompoundIndexDefinition(messageCompoundKeys).named("conv_created_idx"))
                .subscribe(idx -> log.info("Created compound index: {}", idx),
                        err -> log.debug("Index ensure notice for messages: {}", err.getMessage()));

        // ReadReceipts unique compound index on (conversationId, userId)
        Document receiptCompoundKeys = new Document("conversationId", 1).append("userId", 1);
        mongoTemplate.indexOps(ReadReceipt.class)
                .ensureIndex(new CompoundIndexDefinition(receiptCompoundKeys).named("conv_user_unique_idx").unique())
                .subscribe(idx -> log.info("Created unique compound index: {}", idx),
                        err -> log.debug("Index ensure notice for read_receipts: {}", err.getMessage()));
    }
}
