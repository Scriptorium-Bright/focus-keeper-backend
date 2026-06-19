package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.planning.constant.ExecutionUnitStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "execution_units")
@Getter
/**
 * Big3 항목 아래에서 실제 timebox에 배정할 수 있는 실행 단위다.
 */
public class ExecutionUnit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "big3_item_id", nullable = false)
    private Big3Item big3Item;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExecutionUnitStatus status;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @OneToMany(mappedBy = "executionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Timebox> timeboxes = new ArrayList<>();

    @Version
    private Long version;

    protected ExecutionUnit() {
    }

    private ExecutionUnit(
            Big3Item big3Item,
            String title,
            ExecutionUnitStatus status,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt
    ) {
        this.big3Item = big3Item;
        this.title = title;
        this.status = status;
        this.completedAt = completedAt;
        initializeCreatedAt(createdAt);
    }

    public static ExecutionUnit create(Big3Item big3Item, String title, OffsetDateTime createdAt) {
        return new ExecutionUnit(
                big3Item,
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

    public String getBig3ItemId() {
        return big3Item.getId();
    }

    public ExecutionUnitStatus getStatus() {
        return status == null ? ExecutionUnitStatus.PLANNED : status;
    }

}
