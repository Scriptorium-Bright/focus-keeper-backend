package com.focuskeeper.reboot.recovery.inbox.entity;

import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbox_items")
public class InboxItem {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InboxItem() {
    }

    private InboxItem(String id, String userId, String content, OffsetDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static InboxItem create(String userId, String content, OffsetDateTime createdAt) {
        return new InboxItem(UUID.randomUUID().toString(), userId, content, createdAt);
    }

    public InboxItemResponse toResponse() {
        return new InboxItemResponse(id, content, createdAt.toString());
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
