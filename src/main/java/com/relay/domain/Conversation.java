package com.relay.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "conversations")
public class Conversation implements Serializable {

    @Id
    private String id;

    private ConversationType type = ConversationType.DM;

    private String name;

    @Indexed
    private List<String> memberIds = new ArrayList<>();

    private String createdBy;

    private Instant createdAt = Instant.now();

    @Indexed
    private Instant lastMessageAt;

    public Conversation() {
    }

    public Conversation(String id, ConversationType type, String name, List<String> memberIds,
                        String createdBy, Instant createdAt, Instant lastMessageAt) {
        this.id = id;
        this.type = type != null ? type : ConversationType.DM;
        this.name = name;
        this.memberIds = memberIds != null ? new ArrayList<>(memberIds) : new ArrayList<>();
        this.createdBy = createdBy;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.lastMessageAt = lastMessageAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ConversationType getType() {
        return type;
    }

    public void setType(ConversationType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds != null ? new ArrayList<>(memberIds) : new ArrayList<>();
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }
}
