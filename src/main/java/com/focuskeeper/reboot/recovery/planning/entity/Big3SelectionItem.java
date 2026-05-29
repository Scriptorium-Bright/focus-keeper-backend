package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.planning.Big3ItemCompletionStatus;
import com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus;
import com.focuskeeper.reboot.recovery.planning.dto.Big3ItemResponse;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "big3_selection_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_big3_selection_items_order", columnNames = {"selection_id", "sort_order"})
        }
)
@Getter
/**
 * Big3Selection의 개별 선택 항목을 나타내는 자식 엔티티다.
 */
public class Big3SelectionItem {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selection_id", nullable = false)
    private Big3Selection selection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inbox_item_id", nullable = false)
    private InboxItem inboxItem;

    @OneToMany(mappedBy = "big3SelectionItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExecutionUnit> units = new ArrayList<>();

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "big3_status", nullable = false, length = 20)
    private Big3ItemCompletionStatus status;

    protected Big3SelectionItem() {
    }

    private Big3SelectionItem(
            String id,
            Big3Selection selection,
            InboxItem inboxItem,
            int sortOrder
    ) {
        this.id = id;
        this.selection = selection;
        this.inboxItem = inboxItem;
        this.sortOrder = sortOrder;
        this.status = Big3ItemCompletionStatus.NOT_STARTED;
    }

    /**
     * 선택 항목 row를 새로 생성한다.
     */
    public static Big3SelectionItem create(
            Big3Selection selection,
            InboxItem inboxItem,
            int sortOrder
    ) {
        return new Big3SelectionItem(UUID.randomUUID().toString(), selection, inboxItem, sortOrder);
    }

    /**
     * 같은 자리의 선택 항목을 다른 inbox item으로 교체한다.
     */
    public void replaceWith(InboxItem inboxItem, int sortOrder) {
        this.inboxItem = inboxItem;
        this.sortOrder = sortOrder;
    }

    /**
     * 선택 항목을 Big3 응답 형태로 변환한다.
     */
    public Big3ItemResponse toResponse() {
        return new Big3ItemResponse(id, inboxItem.getId(), inboxItem.getContent(), status.name());
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
