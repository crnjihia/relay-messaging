package com.relay.domain;

import java.io.Serializable;
import java.time.Instant;

public record PresenceInfo(
        String userId,
        UserStatus status,
        Instant lastHeartbeat
) implements Serializable {
    public static PresenceInfo online(String userId) {
        return new PresenceInfo(userId, UserStatus.ONLINE, Instant.now());
    }

    public static PresenceInfo away(String userId) {
        return new PresenceInfo(userId, UserStatus.AWAY, Instant.now());
    }

    public static PresenceInfo offline(String userId) {
        return new PresenceInfo(userId, UserStatus.OFFLINE, Instant.now());
    }
}
