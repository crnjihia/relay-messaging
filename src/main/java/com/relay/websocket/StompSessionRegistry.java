package com.relay.websocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StompSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(StompSessionRegistry.class);

    // sessionId -> StompSession
    private final Map<String, StompSession> sessions = new ConcurrentHashMap<>();
    // userId -> Set<sessionId>
    private final Map<String, Set<String>> userSessionIndex = new ConcurrentHashMap<>();

    public StompSessionRegistry(MeterRegistry meterRegistry) {
        // Register active sessions gauge
        Gauge.builder("relay.websocket.sessions.active", sessions, Map::size)
                .description("Active STOMP WebSocket sessions connected to this instance")
                .register(meterRegistry);
    }

    public void register(StompSession session) {
        sessions.put(session.getSessionId(), session);
        userSessionIndex.computeIfAbsent(session.getUserId(), k -> ConcurrentHashMap.newKeySet())
                .add(session.getSessionId());
        log.info("Registered STOMP session: id={}, user={} (total local sessions: {})",
                session.getSessionId(), session.getUsername(), sessions.size());
    }

    public StompSession unregister(String sessionId) {
        StompSession removed = sessions.remove(sessionId);
        if (removed != null) {
            Set<String> userSessions = userSessionIndex.get(removed.getUserId());
            if (userSessions != null) {
                userSessions.remove(sessionId);
                if (userSessions.isEmpty()) {
                    userSessionIndex.remove(removed.getUserId());
                }
            }
            log.info("Unregistered STOMP session: id={}, user={} (remaining local sessions: {})",
                    sessionId, removed.getUsername(), sessions.size());
        }
        return removed;
    }

    public StompSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public List<StompSession> getSessionsForUser(String userId) {
        Set<String> sessionIds = userSessionIndex.get(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<StompSession> result = new ArrayList<>();
        for (String sId : sessionIds) {
            StompSession s = sessions.get(sId);
            if (s != null) {
                result.add(s);
            }
        }
        return result;
    }

    public Collection<StompSession> getAllSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public boolean isUserLocallyConnected(String userId) {
        Set<String> set = userSessionIndex.get(userId);
        return set != null && !set.isEmpty();
    }

    /**
     * Broadcasts a frame to all local sessions subscribed to the given topic destination.
     */
    public void broadcastToTopic(String destination, String payload, boolean isCritical) {
        for (StompSession session : sessions.values()) {
            Set<String> subIds = session.getSubscriptionIdsForDestination(destination);
            for (String subId : subIds) {
                StompFrame frame = StompFrame.builder(StompCommand.MESSAGE)
                        .header("subscription", subId)
                        .header("message-id", UUID.randomUUID().toString())
                        .header("destination", destination)
                        .header("content-type", "application/json")
                        .body(payload)
                        .build();

                session.emit(frame, isCritical);
            }
        }
    }

    /**
     * Sends a frame to a specific user destination across all sessions of that user.
     * e.g., destination = "/user/queue/messages"
     */
    public void sendToUser(String userId, String userDestination, String payload, boolean isCritical) {
        List<StompSession> userSessions = getSessionsForUser(userId);
        for (StompSession session : userSessions) {
            Set<String> subIds = session.getSubscriptionIdsForDestination(userDestination);
            for (String subId : subIds) {
                StompFrame frame = StompFrame.builder(StompCommand.MESSAGE)
                        .header("subscription", subId)
                        .header("message-id", UUID.randomUUID().toString())
                        .header("destination", userDestination)
                        .header("content-type", "application/json")
                        .body(payload)
                        .build();

                session.emit(frame, isCritical);
            }
        }
    }
}
