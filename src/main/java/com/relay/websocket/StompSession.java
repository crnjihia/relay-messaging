package com.relay.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StompSession {

    private static final Logger log = LoggerFactory.getLogger(StompSession.class);
    private static final int BOUNDED_BUFFER_SIZE = 256;

    private final String sessionId;
    private final String userId;
    private final String username;
    private final WebSocketSession webSocketSession;

    // SubscriptionId -> Destination
    private final Map<String, String> subscriptionIdToDestination = new ConcurrentHashMap<>();
    // Destination -> Set of SubscriptionIds
    private final Map<String, Set<String>> destinationToSubscriptionIds = new ConcurrentHashMap<>();

    // Reactive sink with bounded buffer
    private final Sinks.Many<StompFrame> frameSink;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public StompSession(String sessionId, String userId, String username, WebSocketSession webSocketSession) {
        this(sessionId, userId, username, webSocketSession, Sinks.many().multicast().onBackpressureBuffer(BOUNDED_BUFFER_SIZE, false));
    }

    public StompSession(String sessionId, String userId, String username, WebSocketSession webSocketSession, Sinks.Many<StompFrame> frameSink) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.username = username;
        this.webSocketSession = webSocketSession;
        this.frameSink = frameSink;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    public Flux<StompFrame> getFrameStream() {
        return frameSink.asFlux();
    }

    public void addSubscription(String subscriptionId, String destination) {
        subscriptionIdToDestination.put(subscriptionId, destination);
        destinationToSubscriptionIds.computeIfAbsent(destination, k -> ConcurrentHashMap.newKeySet()).add(subscriptionId);
        log.debug("Session {} ({}) subscribed [{}] to {}", sessionId, username, subscriptionId, destination);
    }

    public void removeSubscription(String subscriptionId) {
        String dest = subscriptionIdToDestination.remove(subscriptionId);
        if (dest != null) {
            Set<String> subIds = destinationToSubscriptionIds.get(dest);
            if (subIds != null) {
                subIds.remove(subscriptionId);
                if (subIds.isEmpty()) {
                    destinationToSubscriptionIds.remove(dest);
                }
            }
            log.debug("Session {} ({}) unsubscribed [{}] from {}", sessionId, username, subscriptionId, dest);
        }
    }

    public Set<String> getSubscriptionIdsForDestination(String destination) {
        Set<String> set = destinationToSubscriptionIds.get(destination);
        return set != null ? set : Set.of();
    }

    public boolean isSubscribedTo(String destination) {
        Set<String> set = destinationToSubscriptionIds.get(destination);
        return set != null && !set.isEmpty();
    }

    /**
     * Emits a STOMP frame with backpressure handling:
     * - Drops non-critical frames (e.g. presence) if buffer overflows.
     * - Bounded buffer for critical frames (messages, read receipts); closes session on overflow.
     */
    public boolean emit(StompFrame frame, boolean isCritical) {
        if (isClosed.get()) {
            return false;
        }

        Sinks.EmitResult result = frameSink.tryEmitNext(frame);

        if (result.isSuccess()) {
            return true;
        }

        if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
            if (!isCritical) {
                // Drop non-critical event (presence heartbeat/status) for slow consumer
                log.warn("Backpressure: Dropped non-critical event ({}) for slow consumer session {} (user {})",
                        frame.command(), sessionId, username);
                return false;
            } else {
                // Bounded buffer exceeded for critical message - disconnect slow consumer
                log.error("Backpressure: Bounded buffer (size {}) exceeded for critical frame on session {} (user {}). Terminating session.",
                        BOUNDED_BUFFER_SIZE, sessionId, username);
                close(CloseStatus.POLICY_VIOLATION);
                return false;
            }
        }

        log.debug("Failed to emit frame to session {}: {}", sessionId, result);
        return false;
    }

    public void close(CloseStatus status) {
        if (isClosed.compareAndSet(false, true)) {
            frameSink.tryEmitComplete();
            if (webSocketSession != null && webSocketSession.isOpen()) {
                webSocketSession.close(status).subscribe();
            }
        }
    }
}
