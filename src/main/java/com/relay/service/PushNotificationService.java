package com.relay.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import com.relay.domain.Message;
import com.relay.domain.User;
import com.relay.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    private static final Duration PUSH_THROTTLE_DURATION = Duration.ofSeconds(30);

    private final FirebaseApp firebaseApp;
    private final UserRepository userRepository;
    private final ReactiveStringRedisTemplate redisTemplate;

    public PushNotificationService(FirebaseApp firebaseApp,
                                   UserRepository userRepository,
                                   ReactiveStringRedisTemplate redisTemplate) {
        this.firebaseApp = firebaseApp;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    public Mono<Void> sendPushIfOffline(User recipient, Message message, String senderDisplayName) {
        if (recipient == null || recipient.getFcmToken() == null || recipient.getFcmToken().isBlank()) {
            return Mono.empty();
        }

        String throttleKey = "fcm:throttle:" + recipient.getId() + ":" + message.getConversationId();

        return redisTemplate.opsForValue().setIfAbsent(throttleKey, "1", PUSH_THROTTLE_DURATION)
                .flatMap(wasSet -> {
                    if (Boolean.FALSE.equals(wasSet)) {
                        log.debug("Push notification throttled for user {} in conversation {}", recipient.getId(), message.getConversationId());
                        return Mono.empty();
                    }

                    return dispatchNotification(recipient, message, senderDisplayName);
                });
    }

    private Mono<Void> dispatchNotification(User recipient, Message message, String senderDisplayName) {
        return Mono.fromCallable(() -> {
            String title = (senderDisplayName != null && !senderDisplayName.isBlank())
                    ? senderDisplayName : "New Message";
            String body = message.getContent() != null ? message.getContent() : "[Attachment]";

            if (firebaseApp == null) {
                log.info("[MOCK FCM PUSH] To: {} ({}) | Title: '{}' | Body: '{}' | Conv: {}",
                        recipient.getUsername(), recipient.getFcmToken(), title, body, message.getConversationId());
                return null;
            }

            try {
                com.google.firebase.messaging.Message fcmMsg = com.google.firebase.messaging.Message.builder()
                        .setToken(recipient.getFcmToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putData("conversationId", message.getConversationId())
                        .putData("messageId", message.getId())
                        .putData("senderId", message.getSenderId())
                        .build();

                String response = FirebaseMessaging.getInstance(firebaseApp).send(fcmMsg);
                log.info("FCM push successfully sent to {}: responseId={}", recipient.getUsername(), response);
            } catch (FirebaseMessagingException e) {
                log.warn("FCM push failed for user {}: {}", recipient.getUsername(), e.getMessage());
                MessagingErrorCode errorCode = e.getMessagingErrorCode();
                if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    log.warn("Invalidating unregistered/invalid FCM token for user {}", recipient.getId());
                    invalidateUserFcmToken(recipient.getId());
                }
            } catch (Exception e) {
                log.error("Unexpected error sending FCM notification: {}", e.getMessage(), e);
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void invalidateUserFcmToken(String userId) {
        userRepository.findById(userId)
                .flatMap(user -> {
                    user.setFcmToken(null);
                    return userRepository.save(user);
                })
                .subscribe(
                        u -> log.info("Successfully cleared invalid FCM token for user {}", userId),
                        err -> log.error("Failed to clear FCM token for user {}: {}", userId, err.getMessage())
                );
    }
}
