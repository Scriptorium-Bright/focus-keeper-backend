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
/**
 * 특정 사용자가 특정 날짜에 선택한 오늘의 Big3 헤더 엔티티다.
 */
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

    /**
     * 오늘의 Big3 선택 헤더를 새로 만든다.
     */
    public static Big3Selection create(String userId, LocalDate selectedDate, OffsetDateTime selectedAt) {
        return new Big3Selection(UUID.randomUUID().toString(), userId, selectedDate, selectedAt);
    }

    /**
     * 기존 선택 항목을 새 inbox item 목록으로 교체한다.
     *
     * 공통 prefix는 재사용하고, 남는 항목은 제거하고, 부족한 항목은 새로 추가해
     * 자식 컬렉션을 통째로 갈아엎지 않고도 today selection을 갱신할 수 있게 한다.
     */
    public void replaceItems(List<InboxItem> inboxItems, OffsetDateTime selectedAt) {
        this.selectedAt = selectedAt;
        selectedItems.sort((left, right) -> Integer.compare(left.getSortOrder(), right.getSortOrder()));

        int sharedSize = Math.min(selectedItems.size(), inboxItems.size());
        for (int index = 0; index < sharedSize; index++) {
            selectedItems.get(index).replaceWith(inboxItems.get(index), index);
        }

        while (selectedItems.size() > inboxItems.size()) {
            selectedItems.remove(selectedItems.size() - 1);
        }

        for (int index = sharedSize; index < inboxItems.size(); index++) {
            selectedItems.add(Big3SelectionItem.create(this, inboxItems.get(index), index));
        }
    }

    /**
     * 엔티티를 외부 응답 DTO로 변환한다.
     */
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
