package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.dto.Big3SelectionResponse;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Selection;
import com.focuskeeper.reboot.recovery.planning.repository.Big3SelectionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
/**
 * Brain Dump 후보 중 오늘의 Big3를 선택하고 조회하는 서비스다.
 *
 * planning의 첫 단계로서, 이후 timebox 배정이 허용되는 항목의 경계를 이 서비스가 만든다.
 */
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

    /**
     * 사용자가 고른 inbox item들을 오늘의 Big3로 확정한다.
     *
     * 요청 순서를 보존하면서 중복, 타인 항목, 존재하지 않는 항목을 검증하고
     * 오늘 날짜의 selection row를 생성 또는 갱신한다.
     */
    @Transactional
    public Big3SelectionResponse selectTodayBig3(String userId, List<String> itemIds) {
        List<String> uniqueItemIds = deduplicate(itemIds);

        if (uniqueItemIds.size() != itemIds.size()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("itemIds", "중복된 itemId는 허용되지 않습니다.")
            );
        }

        List<InboxItem> selectedItems = findInboxItemsInRequestOrder(userId, uniqueItemIds);

        // 요청한 itemIds 중 실제로 조회되지 않은 ID를 찾아, 존재하지 않거나 사용자 소유가 아닌 항목을 명확히 드러낸다.
        if (selectedItems.size() != uniqueItemIds.size()) {
            Set<String> selectedItemIds = selectedItems.stream()
                    .map(InboxItem::getId)
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
        Big3Selection selection = big3SelectionRepository.findByUserIdAndSelectedDate(userId, selectedDate)
                .orElseGet(() -> Big3Selection.create(userId, selectedDate, selectedAt));
        selection.replaceItems(selectedItems, selectedAt);

        return big3SelectionRepository.save(selection).toResponse();
    }

    /**
     * 오늘 날짜 기준으로 이미 선택된 Big3를 조회한다.
     */
    public Big3SelectionResponse getTodayBig3(String userId) {
        return big3SelectionRepository.findByUserIdAndSelectedDate(userId, LocalDate.now())
                .map(Big3Selection::toResponse)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "userId", userId,
                                "selectedDate", LocalDate.now().toString()
                        )
                ));
    }

    /**
     * 입력 순서를 보존한 채 itemId 중복만 제거한다.
     */
    private List<String> deduplicate(List<String> itemIds) {
        return new ArrayList<>(new LinkedHashSet<>(itemIds));
    }

    /**
     * DB 조회 결과를 요청 순서대로 다시 정렬해 반환한다.
     */
    private List<InboxItem> findInboxItemsInRequestOrder(String userId, List<String> itemIds) {
        Map<String, InboxItem> indexedItems = new LinkedHashMap<>();
        inboxItemRepository.findAllByUserIdAndIdIn(userId, itemIds)
                .forEach(item -> indexedItems.put(item.getId(), item));

        return itemIds.stream()
                .map(indexedItems::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
