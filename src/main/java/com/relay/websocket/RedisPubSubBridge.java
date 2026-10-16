package com.relay.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.config.RedisConfig;
import com.relay.dto.MessageResponse;
import com.relay.dto.PresenceEvent;
import com.relay.dto.ReadReceiptResponse;
import com.relay.service.PresenceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

@Component
public class RedisPubSubBridge {

    private static final Logger log = LoggerFactory.getLogger(RedisPubSubBridge.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final StompSessionRegistry sessionRegistry;
    private final PresenceService presenceService;
    private final ObjectMapper objectMapper;

    private final Counter messagesSentCounter;
    private final Counter messagesReceivedCounter;
    private final Timer pubSubLatencyTimer;

    public RedisPubSubBridge(ReactiveStringRedisTemplate redisTemplate,
                             ReactiveRedisMessageListenerContainer listenerContainer,
                             StompSessionRegistry sessionRegistry,
                             @Lazy PresenceService presenceService,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.sessionRegistry = sessionRegistry;
        this.presenceService = presenceService;
        this.objectMapper = objectMapper;

        this.messagesSentCounter = Counter.builder("relay.messages.sent.total")
                .description("Total messages published via Redis Pub/Sub")
                .register(meterRegistry);
        this.messagesReceivedCounter = Counter.builder("relay.messages.received.total")
                .description("Total messages received from Redis Pub/Sub")
                .register(meterRegistry);
        this.pubSubLatencyTimer = Timer.builder("relay.redis.pubsub.latency")
                .description("Latency of message publishing to Redis")
                .register(meterRegistry);
    }

    @PostConstruct
    public void initListeners() {
        // 1. Subscribe to Chat Conversation messages across instances: chat:conversation:*
        listenerContainer.receive(new PatternTopic(RedisConfig.CHAT_CONVERSATION_CHANNEL_PREFIX + "*"))
                .doOnNext(this::handleConversationMessage)
                .doOnError(err -> log.error("Error in conversation listener pipeline: {}", err.getMessage()))
                .subscribe();

        // 2. Subscribe to Read Receipts: chat:receipt:*
        listenerContainer.receive(new PatternTopic(RedisConfig.CHAT_RECEIPT_CHANNEL_PREFIX + "*"))
                .doOnNext(this::handleReceiptMessage)
                .doOnError(err -> log.error("Error in receipt listener pipeline: {}", err.getMessage()))
                .subscribe();

        // 3. Subscribe to Presence updates: presence:updates
        listenerContainer.receive(new ChannelTopic(RedisConfig.PRESENCE_UPDATES_CHANNEL))
                .doOnNext(this::handlePresenceMessage)
                .doOnError(err -> log.error("Error in presence listener pipeline: {}", err.getMessage()))
                .subscribe();

        // 4. Subscribe to Redis Keyspace Expired events: __keyevent@*__:expired
        listenerContainer.receive(new PatternTopic(RedisConfig.KEYSPACE_EXPIRED_PATTERN))
                .doOnNext(this::handleKeyspaceExpiration)
                .doOnError(err -> log.error("Error in keyspace expiration pipeline: {}", err.getMessage()))
                .subscribe();

        log.info("Initialized ReactiveRedisMessageListenerContainer for chat fan-out, presence, and keyspace expiration.");
    }

    public Mono<Void> publishMessage(String conversationId, MessageResponse message) {
        long start = System.currentTimeMillis();
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(message))
                .flatMap(json -> redisTemplate.convertAndSend(RedisConfig.CHAT_CONVERSATION_CHANNEL_PREFIX + conversationId, json))
                .doOnSuccess(cnt -> {
                    messagesSentCounter.increment();
                    pubSubLatencyTimer.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
                    log.debug("Published message {} to Redis channel for conv {}", message.id(), conversationId);
                })
                .then();
    }

    public Mono<Void> publishReceipt(String conversationId, ReadReceiptResponse receipt) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(receipt))
                .flatMap(json -> redisTemplate.convertAndSend(RedisConfig.CHAT_RECEIPT_CHANNEL_PREFIX + conversationId, json))
                .then();
    }

    private void handleConversationMessage(ReactiveSubscription.Message<String, String> message) {
        try {
            messagesReceivedCounter.increment();
            String channel = message.getChannel();
            String conversationId = channel.substring(RedisConfig.CHAT_CONVERSATION_CHANNEL_PREFIX.length());
            String json = message.getMessage();

            // Fan-out to local STOMP clients subscribed to this conversation topic
            String topicDestination = "/topic/conversations/" + conversationId;
            sessionRegistry.broadcastToTopic(topicDestination, json, true);

            // Also fan-out to user destination /user/queue/messages if client is subscribed there
            MessageResponse resp = objectMapper.readValue(json, MessageResponse.class);
            sessionRegistry.sendToUser(resp.senderId(), "/user/queue/messages", json, true);
        } catch (Exception e) {
            log.error("Failed to fan-out conversation message: {}", e.getMessage(), e);
        }
    }

    private void handleReceiptMessage(ReactiveSubscription.Message<String, String> message) {
        try {
            String channel = message.getChannel();
            String conversationId = channel.substring(RedisConfig.CHAT_RECEIPT_CHANNEL_PREFIX.length());
            String json = message.getMessage();

            // Fan-out to local topic subscribers
            sessionRegistry.broadcastToTopic("/topic/conversations/" + conversationId, json, true);

            // Also fan-out to user receipt destination
            ReadReceiptResponse receipt = objectMapper.readValue(json, ReadReceiptResponse.class);
            sessionRegistry.sendToUser(receipt.userId(), "/user/queue/receipts", json, true);
        } catch (Exception e) {
            log.error("Failed to fan-out receipt message: {}", e.getMessage(), e);
        }
    }

    private void handlePresenceMessage(ReactiveSubscription.Message<String, String> message) {
        try {
            String json = message.getMessage();
            // Fan-out non-critical presence update to all local sessions subscribed to /user/queue/presence
            for (StompSession session : sessionRegistry.getAllSessions()) {
                if (session.isSubscribedTo("/user/queue/presence")) {
                    for (String subId : session.getSubscriptionIdsForDestination("/user/queue/presence")) {
                        StompFrame frame = StompFrame.builder(StompCommand.MESSAGE)
                                .header("subscription", subId)
                                .header("destination", "/user/queue/presence")
                                .header("content-type", "application/json")
                                .body(json)
                                .build();
                        // Presence updates are non-critical -> drop on slow consumers
                        session.emit(frame, false);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fan-out presence message: {}", e.getMessage(), e);
        }
    }

    private void handleKeyspaceExpiration(ReactiveSubscription.Message<String, String> message) {
        String expiredKey = message.getMessage();
        presenceService.handleExpiredKey(expiredKey).subscribe();
    }
}
