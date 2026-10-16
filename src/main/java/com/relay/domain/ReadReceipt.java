package com.relay.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.Instant;

@Document(collection = "read_receipts")
@CompoundIndex(name = "conv_user_unique_idx", def = "{'conversationId': 1, 'userId': 1}", unique = true)
public class ReadReceipt implements Serializable {

    @Id
    private String id;

    private String conversationId;

    private String userId;

    private String lastReadMessageId;

    private Instant readAt = Instant.now();

    public ReadReceipt() {
    }

    public ReadReceipt(String id, String conversationId, String userId, String lastReadMessageId, Instant readAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.userId = userId;
        this.lastReadMessageId = lastReadMessageId;
        this.readAt = readAt != null ? readAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLastReadMessageId() {
        return lastReadMessageId;
    }

    public void setLastReadMessageId(String lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }
}
