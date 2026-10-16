package com.relay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    public static final String CHAT_CONVERSATION_CHANNEL_PREFIX = "chat:conversation:";
    public static final String CHAT_RECEIPT_CHANNEL_PREFIX = "chat:receipt:";
    public static final String PRESENCE_UPDATES_CHANNEL = "presence:updates";
    public static final String KEYSPACE_EXPIRED_PATTERN = "__keyevent@*__:expired";

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    @Bean
    public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(
            ReactiveRedisConnectionFactory factory) {
        return new ReactiveRedisMessageListenerContainer(factory);
    }

    @Bean
    public ChannelTopic presenceUpdatesTopic() {
        return new ChannelTopic(PRESENCE_UPDATES_CHANNEL);
    }

    @Bean
    public PatternTopic chatConversationPatternTopic() {
        return new PatternTopic(CHAT_CONVERSATION_CHANNEL_PREFIX + "*");
    }

    @Bean
    public PatternTopic chatReceiptPatternTopic() {
        return new PatternTopic(CHAT_RECEIPT_CHANNEL_PREFIX + "*");
    }

    @Bean
    public PatternTopic keyspaceExpiredPatternTopic() {
        return new PatternTopic(KEYSPACE_EXPIRED_PATTERN);
    }
}
