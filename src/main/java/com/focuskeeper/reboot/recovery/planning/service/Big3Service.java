package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.Big3ItemCompletionStatus;
import com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus;
import com.focuskeeper.reboot.recovery.planning.dto.Big3ItemResponse;
import com.focuskeeper.reboot.recovery.planning.dto.Big3SelectionResponse;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Selection;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.repository.Big3SelectionRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
        // 중복 제거
        List<String> uniqueItemIds = deduplicate(itemIds);

        validateNoDuplicateItemIds(uniqueItemIds,itemIds);

        // Q. 요청 순서를 보장하는 이유
        // A. Big3는 단순 집합이 아니라 사용자가 고른 우선순위/표시 순서까지 포함한 데이터다.
        //    실제로 selection item은 sortOrder로 저장되고 응답도 그 순서로 나가므로,
        //    DB 조회 결과를 요청 순서대로 다시 맞춰야 한다.
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

        // 현재 시각 / 현재 날짜를 받아옴 (Big3Selection에서 사용자가 이 날짜 이시간에 골랐다를 저장하기 때문)
        LocalDate selectedDate = LocalDate.now();
        OffsetDateTime selectedAt = OffsetDateTime.now();

        Big3Selection selection = big3SelectionRepository.findByUserIdAndSelectedDate(userId, selectedDate)
                .orElseGet(() -> Big3Selection.create(userId, selectedDate, selectedAt));

        selection.replaceItems(selectedItems, selectedAt); // 이 부분은 조금 이해가 안 감 -> › big3를 이미 고른 상태여도 다시 선택할 수 있으니까 replace

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
        // 순서가 중요할 때 LinkedHashMap을 사용 (HashMap은 순서 보장 X / LinkedHashMap은 Insertion-order (데이터를 넣은 순서대로)) (엄밀한 정의에서의 descend / ascend sort가 아님, order임)
        Map<String, InboxItem> indexedItems = new LinkedHashMap<>();
        inboxItemRepository.findAllByUserIdAndIdIn(userId, itemIds)
                .forEach(item -> indexedItems.put(item.getId(), item));

        return itemIds.stream()
                .map(indexedItems::get)
                .filter(Objects::nonNull)
                .toList();
    }

    // 중복에 대한 exception
    private void validateNoDuplicateItemIds(List<String> uniqueItemIds, List<String> itemIds) {
        if (uniqueItemIds.size() != itemIds.size()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("itemIds", "중복된 itemId는 허용되지 않습니다.")
            );
        }
    }


}
