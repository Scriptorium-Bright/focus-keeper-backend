package com.focuskeeper.reboot.recovery.planning;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.InboxItemEntity;
import com.focuskeeper.reboot.recovery.inbox.InboxItemRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class Big3Service {

    private final InboxItemRepository inboxItemRepository;
    private final Big3SelectionRepository big3SelectionRepository;

    public Big3Service(
            InboxItemRepository inboxItemRepository,
            Big3SelectionRepository big3SelectionRepository
    ) {
        this.inboxItemRepository = inboxItemRepository;
        this.big3SelectionRepository = big3SelectionRepository;
    }

    @Transactional
    public Big3SelectionResponse selectTodayBig3(String userId, List<String> itemIds) {
        List<String> uniqueItemIds = deduplicate(itemIds);
        if (uniqueItemIds.size() != itemIds.size()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("itemIds", "중복된 itemId는 허용되지 않습니다.")
            );
        }

        List<InboxItemEntity> selectedItems = findInboxItemsInRequestOrder(userId, uniqueItemIds);
        if (selectedItems.size() != uniqueItemIds.size()) {
            Set<String> selectedItemIds = selectedItems.stream()
                    .map(InboxItemEntity::getId)
                    .collect(HashSet::new, Set::add, Set::addAll);
            List<String> missingItemIds = uniqueItemIds.stream()
                    .filter(id -> !selectedItemIds.contains(id))
                    .toList();

            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    Map.of("missingItemIds", missingItemIds)
            );
        }

        LocalDate selectedDate = LocalDate.now();
        OffsetDateTime selectedAt = OffsetDateTime.now();
        Big3SelectionEntity selection = big3SelectionRepository.findByUserIdAndSelectedDate(userId, selectedDate)
                .orElseGet(() -> Big3SelectionEntity.create(userId, selectedDate, selectedAt));
        selection.replaceItems(selectedItems, selectedAt);

        return big3SelectionRepository.save(selection).toResponse();
    }

    public Big3SelectionResponse getTodayBig3OrThrow(String userId) {
        return big3SelectionRepository.findByUserIdAndSelectedDate(userId, LocalDate.now())
                .map(Big3SelectionEntity::toResponse)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "userId", userId,
                                "selectedDate", LocalDate.now().toString()
                        )
                ));
    }

    private List<String> deduplicate(List<String> itemIds) {
        return new ArrayList<>(new LinkedHashSet<>(itemIds));
    }

    private List<InboxItemEntity> findInboxItemsInRequestOrder(String userId, List<String> itemIds) {
        Map<String, InboxItemEntity> indexedItems = new LinkedHashMap<>();
        inboxItemRepository.findAllByUserIdAndIdIn(userId, itemIds)
                .forEach(item -> indexedItems.put(item.getId(), item));

        return itemIds.stream()
                .map(indexedItems::get)
                .filter(item -> item != null)
                .toList();
    }
}
