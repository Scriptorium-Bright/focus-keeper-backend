package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.planning.Big3ItemCompletionStatus;
import com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus;
import com.focuskeeper.reboot.recovery.planning.dto.Big3ItemResponse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "big3_items")
@Getter
/**
 * 같은 주 안에서 유지되는 실제 Big3 작업 엔티티다.
 */
public class Big3Item extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_inbox_item_id", nullable = false)
    private InboxItem originInboxItem;

    @OneToMany(mappedBy = "big3Item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExecutionUnit> units = new ArrayList<>();

    @OneToMany(mappedBy = "big3Item")
    private List<DailyBig3Entry> big3Entries = new ArrayList<>();

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "title_snapshot", nullable = false, length = 200)
    private String titleSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Big3ItemCompletionStatus status;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt; // 완료시각
    @Column(name = "abandoned_at")
    private OffsetDateTime abandonedAt; // 포기시각
    @Column(name = "expired_at")
    private OffsetDateTime expiredAt; // 주간 reset 만료시각

    /**
     * 다음 주에 같은 작업을 새 item으로 이어갈 때 직전 주 item을 추적한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "derived_from_item_id")
    private Big3Item derivedFromItem;

    @Version
    @Column(nullable = false)
    private long version;

    protected Big3Item() {
    }

    private Big3Item(
            String userId,
            LocalDate selectedDate,
            InboxItem inboxItem,
            OffsetDateTime createdAt
    ) {
        this.userId = userId;
        this.weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        this.originInboxItem = inboxItem;
        this.titleSnapshot = inboxItem.getContent();
        this.status = Big3ItemCompletionStatus.NOT_STARTED;
        initializeCreatedAt(createdAt);
    }

    /**
     * Inbox 후보로부터 해당 주의 Big3 작업을 생성한다.
     */
    public static Big3Item create(
            String userId,
            LocalDate selectedDate,
            InboxItem inboxItem,
            OffsetDateTime createdAt
    ) {
        return new Big3Item(userId, selectedDate, inboxItem, createdAt);
    }

    /**
     * 선택 항목을 Big3 응답 형태로 변환한다.
     */
    public Big3ItemResponse toResponse() {
        return new Big3ItemResponse(id, originInboxItem.getId(), titleSnapshot, status.name());
    }

    public void updateStatusFromUnits() {
        if (this.units.isEmpty()) {
            this.status = Big3ItemCompletionStatus.NOT_STARTED;
            return;
        }

        boolean allCompleted = this.units.stream()
                .allMatch(unit -> unit.getStatus() == ExecutionUnitStatus.COMPLETED);

        this.status = allCompleted ? Big3ItemCompletionStatus.COMPLETED : Big3ItemCompletionStatus.IN_PROGRESS;

    }

}
