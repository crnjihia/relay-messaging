package com.relay.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "messages")
@CompoundIndex(name = "conv_created_idx", def = "{'conversationId': 1, 'createdAt': -1}")
public class Message implements Serializable {

    @Id
    private String id;

    private String conversationId;

    private String senderId;

    private String content;

    private ContentType contentType = ContentType.TEXT;

    private List<Attachment> attachments = new ArrayList<>();

    private String replyToId;

    private Instant createdAt = Instant.now();

    private Instant editedAt;

    private Instant deletedAt;

    public Message() {
    }

    public Message(String id, String conversationId, String senderId, String content,
                   ContentType contentType, List<Attachment> attachments, String replyToId,
                   Instant createdAt, Instant editedAt, Instant deletedAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.content = content;
        this.contentType = contentType != null ? contentType : ContentType.TEXT;
        this.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
        this.replyToId = replyToId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.editedAt = editedAt;
        this.deletedAt = deletedAt;
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

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments != null ? new ArrayList<>(attachments) : new ArrayList<>();
    }

    public String getReplyToId() {
        return replyToId;
    }

    public void setReplyToId(String replyToId) {
        this.replyToId = replyToId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Instant editedAt) {
        this.editedAt = editedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
