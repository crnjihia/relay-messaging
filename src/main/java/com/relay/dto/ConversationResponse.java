package com.relay.dto;

import com.relay.domain.Conversation;
import com.relay.domain.ConversationType;

import java.time.Instant;
import java.util.List;

public record ConversationResponse(
        String id,
        ConversationType type,
        String name,
        List<String> memberIds,
        List<UserResponse> members,
        String createdBy,
        Instant createdAt,
        Instant lastMessageAt,
        MessageResponse lastMessage,
        long unreadCount
) {
    public static ConversationResponse fromDomain(Conversation c) {
        return fromDomain(c, null, null, 0);
    }

    public static ConversationResponse fromDomain(Conversation c, List<UserResponse> members, MessageResponse lastMessage, long unreadCount) {
        if (c == null) {
            return null;
        }
        return new ConversationResponse(
                c.getId(),
                c.getType(),
                c.getName(),
                c.getMemberIds(),
                members,
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getLastMessageAt(),
                lastMessage,
                unreadCount
        );
    }
}
