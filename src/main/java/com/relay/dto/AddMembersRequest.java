package com.relay.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddMembersRequest(
        @NotEmpty(message = "Member IDs cannot be empty")
        List<String> memberIds
) {
}
