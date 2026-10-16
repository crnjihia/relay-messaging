package com.relay.dto;

import com.relay.domain.ConversationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateConversationRequest(
        @NotNull(message = "Conversation type is required")
        ConversationType type,

        String name,

        @NotNull(message = "Member IDs are required")
        @Size(min = 1, message = "At least one member ID must be provided")
        List<String> memberIds
) {
}
