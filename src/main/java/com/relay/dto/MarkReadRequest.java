package com.relay.dto;

import jakarta.validation.constraints.NotBlank;

public record MarkReadRequest(
        @NotBlank(message = "Message ID is required")
        String messageId
) {
}
