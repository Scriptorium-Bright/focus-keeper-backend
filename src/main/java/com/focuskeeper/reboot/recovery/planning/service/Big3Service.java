package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.SelectionSource;
import com.focuskeeper.reboot.recovery.planning.dto.DailyBig3BoardResponse;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Board;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Entry;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3BoardRepository;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3EntryRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
    private final DailyBig3BoardRepository dailyBig3BoardRepository;
    private final DailyBig3EntryRepository dailyBig3EntryRepository;
    private final Big3ItemRepository big3ItemRepository;

    public Big3Service(
            InboxItemRepository inboxItemRepository,
            DailyBig3BoardRepository dailyBig3BoardRepository,
            DailyBig3EntryRepository dailyBig3EntryRepository,
            Big3ItemRepository big3ItemRepository
    ) {
        this.inboxItemRepository = inboxItemRepository;
        this.dailyBig3BoardRepository = dailyBig3BoardRepository;
        this.dailyBig3EntryRepository = dailyBig3EntryRepository;
        this.big3ItemRepository = big3ItemRepository;
    }

    /**
     * 사용자가 고른 inbox item들을 오늘의 Big3로 확정한다.
     *
     * 요청 순서를 보존하면서 중복, 타인 항목, 존재하지 않는 항목을 검증하고
     * 오늘 날짜의 selection row를 생성 또는 갱신한다.
     */
    @Transactional
    public DailyBig3BoardResponse selectTodayBig3(String userId, List<String> itemIds) {
        // 중복 제거
        List<String> uniqueItemIds = deduplicate(itemIds);

        validateNoDuplicateItemIds(uniqueItemIds,itemIds);

        // Big3는 단순 집합이 아니라 사용자가 고른 slot 순서를 포함하므로 요청 순서를 보존한다.
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

        // 현재 시각 / 현재 날짜를 받아옴 (DailyBig3Board에서 사용자가 이 날짜 이시간에 골랐다를 저장하기 때문)
        LocalDate selectedDate = LocalDate.now();
        OffsetDateTime selectedAt = OffsetDateTime.now();
        LocalDate weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        DailyBig3Board dailyBig3Board = dailyBig3BoardRepository.findByUserIdAndSelectedDate(userId, selectedDate)
                .orElseGet(() -> DailyBig3Board.create(userId, selectedDate, selectedAt));
        DailyBig3Board savedBoard = dailyBig3BoardRepository.save(dailyBig3Board);

        Map<String, Big3Item> weeklyItemsByInboxId = big3ItemRepository
                .findAllByUserIdAndWeekStartAndOriginInboxItem_IdIn(userId, weekStart, uniqueItemIds)
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getOriginInboxItem().getId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        Set<Big3Item> newItems = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Big3Item> selectedBig3Items = selectedItems.stream()
                .map(inboxItem -> weeklyItemsByInboxId.computeIfAbsent(
                        inboxItem.getId(),
                        ignored -> {
                            Big3Item newItem = Big3Item.create(userId, selectedDate, inboxItem, selectedAt);
                            newItems.add(newItem);
                            return newItem;
                        }
                ))
                .toList();
        big3ItemRepository.saveAll(newItems);

        List<DailyBig3Entry> activeEntries =
                dailyBig3EntryRepository.findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc(
                        savedBoard.getId()
                );
        activeEntries.forEach(entry -> entry.remove(selectedAt));
        dailyBig3EntryRepository.saveAll(activeEntries);

        List<DailyBig3Entry> replacementEntries = new ArrayList<>();
        for (int index = 0; index < selectedBig3Items.size(); index++) {
            Big3Item big3Item = selectedBig3Items.get(index);
            SelectionSource source = newItems.contains(big3Item)
                    ? SelectionSource.NEW
                    : SelectionSource.CARRYOVER;
            replacementEntries.add(DailyBig3Entry.create(
                    savedBoard,
                    big3Item,
                    index + 1,
                    source,
                    selectedAt
            ));
        }

        List<DailyBig3Entry> savedEntries = dailyBig3EntryRepository.saveAll(replacementEntries);
        return DailyBig3BoardResponse.from(savedBoard, savedEntries);
    }

    /**
     * 오늘 날짜 기준으로 이미 선택된 Big3를 조회한다.
     */
    public DailyBig3BoardResponse getTodayBig3(String userId) {
        LocalDate selectedDate = LocalDate.now();
        DailyBig3Board dailyBig3Board = dailyBig3BoardRepository
                .findByUserIdAndSelectedDate(userId, selectedDate)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "userId", userId,
                                "selectedDate", selectedDate.toString()
                        )
                ));
        List<DailyBig3Entry> activeEntries =
                dailyBig3EntryRepository.findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc(
                        dailyBig3Board.getId()
                );
        return DailyBig3BoardResponse.from(dailyBig3Board, activeEntries);
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
