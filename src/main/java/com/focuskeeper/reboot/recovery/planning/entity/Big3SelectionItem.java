package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
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

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
    }

    public static Big3SelectionItem create(
            Big3Selection selection,
            InboxItem inboxItem,
            int sortOrder
    ) {
        return new Big3SelectionItem(UUID.randomUUID().toString(), selection, inboxItem, sortOrder);
    }

    public void replaceWith(InboxItem inboxItem, int sortOrder) {
        this.inboxItem = inboxItem;
        this.sortOrder = sortOrder;
    }

    public InboxItemResponse toInboxItemResponse() {
        return inboxItem.toResponse();
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
