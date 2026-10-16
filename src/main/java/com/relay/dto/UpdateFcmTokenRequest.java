package com.relay.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateFcmTokenRequest(
        @NotBlank(message = "FCM token is required")
        String fcmToken
) {
}
