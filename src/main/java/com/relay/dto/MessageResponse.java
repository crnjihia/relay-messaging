package com.relay.dto;

import com.relay.domain.Attachment;
import com.relay.domain.ContentType;
import com.relay.domain.Message;

import java.time.Instant;
import java.util.List;

public record MessageResponse(
        String id,
        String conversationId,
        String senderId,
        String senderName,
        String content,
        ContentType contentType,
        List<Attachment> attachments,
        String replyToId,
        Instant createdAt,
        Instant editedAt,
        Instant deletedAt
) {
    public static MessageResponse fromDomain(Message m) {
        return fromDomain(m, null);
    }

    public static MessageResponse fromDomain(Message m, String senderName) {
        if (m == null) {
            return null;
        }
        return new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                senderName,
                m.getContent(),
                m.getContentType(),
                m.getAttachments(),
                m.getReplyToId(),
                m.getCreatedAt(),
                m.getEditedAt(),
                m.getDeletedAt()
        );
    }
}
