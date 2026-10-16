package com.relay.dto;

import com.relay.domain.ReadReceipt;

import java.time.Instant;

public record ReadReceiptResponse(
        String id,
        String conversationId,
        String userId,
        String lastReadMessageId,
        Instant readAt
) {
    public static ReadReceiptResponse fromDomain(ReadReceipt r) {
        if (r == null) {
            return null;
        }
        return new ReadReceiptResponse(
                r.getId(),
                r.getConversationId(),
                r.getUserId(),
                r.getLastReadMessageId(),
                r.getReadAt()
        );
    }
}
