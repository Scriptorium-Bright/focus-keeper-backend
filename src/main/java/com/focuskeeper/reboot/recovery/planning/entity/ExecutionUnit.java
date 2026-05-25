package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "execution_units")
/**
 * Big3 항목 아래에서 실제 timebox에 배정할 수 있는 실행 단위다.
 */
public class ExecutionUnit {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "big3_selection_item_id", nullable = false)
    private Big3SelectionItem big3SelectionItem;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionUnitStatus status;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ExecutionUnit() {
    }

    private ExecutionUnit(
            String id,
            Big3SelectionItem big3SelectionItem,
            String title,
            ExecutionUnitStatus status,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.big3SelectionItem = big3SelectionItem;
        this.title = title;
        this.status = status;
        this.completedAt = completedAt;
        this.createdAt = createdAt;
    }

    public static ExecutionUnit create(Big3SelectionItem big3SelectionItem, String title, OffsetDateTime createdAt) {
        return new ExecutionUnit(
                UUID.randomUUID().toString(),
                big3SelectionItem,
                title,
                ExecutionUnitStatus.PLANNED,
                null,
                createdAt
        );
    }

    public void rename(String title) {
        this.title = title;
    }

    public void complete(OffsetDateTime completedAt) {
        this.status = ExecutionUnitStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public String getId() {
        return id;
    }

    public String getBig3SelectionItemId() {
        return big3SelectionItem.getId();
    }

    public String getTitle() {
        return title;
    }

    public ExecutionUnitStatus getStatus() {
        return status == null ? ExecutionUnitStatus.PLANNED : status;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
