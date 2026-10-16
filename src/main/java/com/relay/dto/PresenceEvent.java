package com.relay.dto;

import com.relay.domain.UserStatus;

import java.io.Serializable;
import java.time.Instant;

public record PresenceEvent(
        String userId,
        UserStatus status,
        Instant timestamp
) implements Serializable {
    public static PresenceEvent of(String userId, UserStatus status) {
        return new PresenceEvent(userId, status, Instant.now());
    }
}
