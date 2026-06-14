package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.planning.constant.SelectionSource;
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
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.OffsetDateTime;

@Entity
@Getter
@Table(name = "daily_big3_entries")
public class DailyBig3Entry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_big3_board_id", nullable = false)
    private DailyBig3Board dailyBig3Board;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "big3_item_id", nullable = false)
    private Big3Item big3Item;

    @Column(name = "slot_order", nullable = false)
    private int slotOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_source", nullable = false, length = 20)
    private SelectionSource selectionSource;

    @Column(name = "selected_at", nullable = false)
    private OffsetDateTime selectedAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    protected DailyBig3Entry() {
    }

    private DailyBig3Entry(
            DailyBig3Board dailyBig3Board,
            Big3Item big3Item,
            int slotOrder,
            SelectionSource selectionSource,
            OffsetDateTime selectedAt
    ) {
        this.dailyBig3Board = dailyBig3Board;
        this.big3Item = big3Item;
        this.slotOrder = slotOrder;
        this.selectionSource = selectionSource;
        this.selectedAt = selectedAt;
        initializeCreatedAt(selectedAt);
    }

    public static DailyBig3Entry create(
            DailyBig3Board dailyBig3Board,
            Big3Item big3Item,
            int slotOrder,
            SelectionSource selectionSource,
            OffsetDateTime selectedAt
    ) {
        return new DailyBig3Entry(dailyBig3Board, big3Item, slotOrder, selectionSource, selectedAt);
    }

    public void remove(OffsetDateTime removedAt) {
        if (this.removedAt == null) {
            this.removedAt = removedAt;
        }
    }

    public boolean isActive() {
        return removedAt == null;
    }
}
