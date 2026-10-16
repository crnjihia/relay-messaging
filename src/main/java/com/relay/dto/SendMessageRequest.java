package com.relay.dto;

import com.relay.domain.Attachment;
import com.relay.domain.ContentType;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SendMessageRequest(
        String conversationId,

        @Size(max = 10240, message = "Message content must not exceed 10 KB")
        String content,

        ContentType contentType,

        List<Attachment> attachments,

        String replyToId
) {
    public ContentType resolvedContentType() {
        return contentType != null ? contentType : ContentType.TEXT;
    }
}
