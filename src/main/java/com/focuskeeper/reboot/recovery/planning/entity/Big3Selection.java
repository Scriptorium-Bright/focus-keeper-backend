package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.planning.dto.Big3SelectionResponse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "big3_selections",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_big3_selections_user_date", columnNames = {"user_id", "selected_date"})
        }
)
public class Big3Selection {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "selected_date", nullable = false)
    private LocalDate selectedDate;

    @Column(name = "selected_at", nullable = false)
    private OffsetDateTime selectedAt;

    @OneToMany(mappedBy = "selection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Big3SelectionItem> selectedItems = new ArrayList<>();

    protected Big3Selection() {
    }

    private Big3Selection(String id, String userId, LocalDate selectedDate, OffsetDateTime selectedAt) {
        this.id = id;
        this.userId = userId;
        this.selectedDate = selectedDate;
        this.selectedAt = selectedAt;
    }

    public static Big3Selection create(String userId, LocalDate selectedDate, OffsetDateTime selectedAt) {
        return new Big3Selection(UUID.randomUUID().toString(), userId, selectedDate, selectedAt);
    }

    public void replaceItems(List<InboxItem> inboxItems, OffsetDateTime selectedAt) {
        this.selectedAt = selectedAt;
        selectedItems.clear();

        for (int index = 0; index < inboxItems.size(); index++) {
            selectedItems.add(Big3SelectionItem.create(this, inboxItems.get(index), index));
        }
    }

    public Big3SelectionResponse toResponse() {
        List<InboxItemResponse> items = selectedItems.stream()
                .sorted((left, right) -> Integer.compare(left.getSortOrder(), right.getSortOrder()))
                .map(Big3SelectionItem::toInboxItemResponse)
                .toList();
        return new Big3SelectionResponse(userId, selectedDate, selectedAt, items);
    }

    public String getUserId() {
        return userId;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }
}
