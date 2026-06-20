package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.planning.constant.Big3ItemCompletionStatus;
import com.focuskeeper.reboot.recovery.planning.constant.Big3ItemStatus;
import com.focuskeeper.reboot.recovery.planning.constant.ExecutionUnitStatus;
import com.focuskeeper.reboot.recovery.planning.dto.Big3ItemResponse;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static com.focuskeeper.reboot.recovery.planning.constant.Big3ItemStatus.*;

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

    /**
     * DB에 저장되는 lifecycle 상태다. OPEN, COMPLETED, ABANDONED, EXPIRED.
     *
     * <p>화면에 표시하는 진행 상태(NOT_STARTED, IN_PROGRESS, COMPLETED)는
     * {@link #getCompletionStatus()}로 매번 계산하며 DB에 저장하지 않는다.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Big3ItemStatus status;

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
        this.status = Big3ItemStatus.OPEN;
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

    public void expire(OffsetDateTime now) {

        if(this.status != OPEN) {
            throw new IllegalArgumentException("OPEN 상태만 만료 가능합니다.");
        }

        this.status = EXPIRED;
        this.expiredAt = now;
    }

    // high
    public void abandon(OffsetDateTime now) {
        if(this.status != OPEN) {
            throw new IllegalArgumentException("OPEN 상태만 만료 가능합니다.");
        }

        this.status = ABANDONED;
        this.abandonedAt = now;
    }


    public void putDerivedFromItem(Big3Item item) {
        this.derivedFromItem = item;
    }

    /**
     * 하위 ExecutionUnit 상태로부터 화면 표시용 진행 상태를 계산한다.
     * DB에 저장하지 않는 파생값이다.
     */
    public Big3ItemCompletionStatus getCompletionStatus() {
        if (this.units.isEmpty()) {
            return Big3ItemCompletionStatus.NOT_STARTED;
        }

        boolean allCompleted = this.units.stream()
                .allMatch(unit -> unit.getStatus() == ExecutionUnitStatus.COMPLETED);

        return allCompleted ? Big3ItemCompletionStatus.COMPLETED : Big3ItemCompletionStatus.IN_PROGRESS;
    }

    /**
     * 하위 unit roll-up 결과가 COMPLETED이면 lifecycle을 OPEN → COMPLETED로 전이한다.
     * 이미 COMPLETED/ABANDONED/EXPIRED인 item에는 아무 일도 하지 않는다.
     */
    // medium
    private void updateStatusFromUnits() {
        if (this.status != Big3ItemStatus.OPEN) {
            return;
        }

        if (getCompletionStatus() == Big3ItemCompletionStatus.COMPLETED) {
            this.status = Big3ItemStatus.COMPLETED;
            this.completedAt = OffsetDateTime.now();
        }

    }

    public void addExecutionUnit(ExecutionUnit unit) {
        units.add(unit);
        updateStatusFromUnits();
    }

        public void refreshCompletionStatusFromUnits() {
        updateStatusFromUnits();
    }

    /**
     * 선택 항목을 Big3 응답 형태로 변환한다.
     */
    public Big3ItemResponse toResponse() {
        return new Big3ItemResponse(id, originInboxItem.getId(), titleSnapshot, getCompletionStatus().name());
    }

}
