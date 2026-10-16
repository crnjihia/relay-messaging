package com.relay.dto;

import com.relay.domain.User;
import com.relay.domain.UserStatus;

import java.time.Instant;

public record UserResponse(
        String id,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        UserStatus status,
        Instant lastSeenAt,
        Instant createdAt
) {
    public static UserResponse fromDomain(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getStatus(),
                user.getLastSeenAt(),
                user.getCreatedAt()
        );
    }
}
