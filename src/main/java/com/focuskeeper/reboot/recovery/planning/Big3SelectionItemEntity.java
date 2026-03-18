package com.focuskeeper.reboot.recovery.planning;

import com.focuskeeper.reboot.recovery.inbox.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.InboxItemEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "big3_selection_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_big3_selection_items_order", columnNames = {"selection_id", "sort_order"})
        }
)
public class Big3SelectionItemEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selection_id", nullable = false)
    private Big3SelectionEntity selection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inbox_item_id", nullable = false)
    private InboxItemEntity inboxItem;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Big3SelectionItemEntity() {
    }

    private Big3SelectionItemEntity(
            String id,
            Big3SelectionEntity selection,
            InboxItemEntity inboxItem,
            int sortOrder
    ) {
        this.id = id;
        this.selection = selection;
        this.inboxItem = inboxItem;
        this.sortOrder = sortOrder;
    }

    public static Big3SelectionItemEntity create(
            Big3SelectionEntity selection,
            InboxItemEntity inboxItem,
            int sortOrder
    ) {
        return new Big3SelectionItemEntity(UUID.randomUUID().toString(), selection, inboxItem, sortOrder);
    }

    public InboxItem toInboxItem() {
        return inboxItem.toDomain();
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
