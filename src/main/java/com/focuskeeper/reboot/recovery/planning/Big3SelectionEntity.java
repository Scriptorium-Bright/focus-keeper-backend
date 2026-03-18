package com.focuskeeper.reboot.recovery.planning;

import com.focuskeeper.reboot.recovery.inbox.InboxItemDto;
import com.focuskeeper.reboot.recovery.inbox.InboxItemEntity;
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
public class Big3SelectionEntity {

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
    private List<Big3SelectionItemEntity> selectedItems = new ArrayList<>();

    protected Big3SelectionEntity() {
    }

    private Big3SelectionEntity(String id, String userId, LocalDate selectedDate, OffsetDateTime selectedAt) {
        this.id = id;
        this.userId = userId;
        this.selectedDate = selectedDate;
        this.selectedAt = selectedAt;
    }

    public static Big3SelectionEntity create(String userId, LocalDate selectedDate, OffsetDateTime selectedAt) {
        return new Big3SelectionEntity(UUID.randomUUID().toString(), userId, selectedDate, selectedAt);
    }

    public void replaceItems(List<InboxItemEntity> inboxItems, OffsetDateTime selectedAt) {
        this.selectedAt = selectedAt;
        selectedItems.clear();

        for (int index = 0; index < inboxItems.size(); index++) {
            selectedItems.add(Big3SelectionItemEntity.create(this, inboxItems.get(index), index));
        }
    }

    public Big3SelectionDto toDto() {
        List<InboxItemDto> items = selectedItems.stream()
                .sorted((left, right) -> Integer.compare(left.getSortOrder(), right.getSortOrder()))
                .map(Big3SelectionItemEntity::toInboxItemDto)
                .toList();
        return new Big3SelectionDto(userId, selectedDate, selectedAt, items);
    }

    public String getUserId() {
        return userId;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }
}
