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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.focuskeeper.reboot.recovery.planning.Big3ItemStatus.*;

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
        OffsetDateTime selectedAt = OffsetDateTime.now();
        LocalDate selectedDate = selectedAt.toLocalDate();

        // 1. 입력 검증 및 InboxItem 가져오기
        List<InboxItem> selectedInboxItems = validateAndFetchInboxItems(userId, itemIds);

        // 2. 오늘의 보드(Board) 가져오거나 생성
        DailyBig3Board board = resolveDailyBoard(userId, selectedDate, selectedAt);
        Set<String> activeBoardItemIds = dailyBig3EntryRepository
                .findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc(board.getId())
                .stream()
                .map(entry -> entry.getBig3Item().getId())
                .collect(Collectors.toSet());

        // 3. 작업(Big3Item) 준비 및 OPEN 상태 검증
        Set<Big3Item> newItems = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Big3Item> selectedBig3Items = resolveOrCreateBig3Items(
                userId,
                selectedDate,
                selectedAt,
                selectedInboxItems,
                newItems,
                activeBoardItemIds
        );

        // 4. 기존 보드 비우고 새 작업들로 채우기
        List<DailyBig3Entry> savedEntries = replaceBoardEntries(board, selectedBig3Items, newItems, selectedAt);

        return DailyBig3BoardResponse.from(board, savedEntries);
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
     * 이전에 하지못했던 (금주 big3) 를 다시 할 수 있게 넘겨준다.
     * @param userId
     * @param big3ItemId
     * @return
     */
/*    @Transactional
    public DailyBig3BoardResponse carryOverBig3Item(String userId, String big3ItemId) {

        OffsetDateTime nowAt = OffsetDateTime.now();
        LocalDate today = nowAt.toLocalDate();

        Big3Item big3Item = big3ItemRepository.findByIdAndUserId(big3ItemId, userId)
                .orElseThrow(() -> new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                Map.of(
                        "big3ItemId", big3ItemId,
                        "userId", userId
                )
        ));

        if(big3Item.getStatus() == COMPLETED ||big3Item.getStatus() == ABANDONED) {
            throw new BusinessException(ErrorCode.SYSTEM_INTERNAL_ERROR, "이미 완료되거나, 포기된 작업입니다.");
        }

        LocalDate curr = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate thisWeek = big3Item.getWeekStart().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        if(!thisWeek.isEqual(curr)) {
            throw new BusinessException(ErrorCode.SYSTEM_INTERNAL_ERROR, "이번주 작업이 아닙니다.");
        }

        DailyBig3Board dailyBig3Board = resolveDailyBoard(userId, today, big3Item.getCreatedAt());

        List<DailyBig3Entry> activeEntries = dailyBig3EntryRepository.findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc(dailyBig3Board.getId());

        if(activeEntries.size() >= 3) {
            throw new BusinessException(ErrorCode.SYSTEM_INTERNAL_ERROR, "big3의 개수는 3개까지 가능합니다.");
        }

        for (DailyBig3Entry activeEntry : activeEntries) {
            if(activeEntry.getBig3Item().getId().equals(big3ItemId)) {
                throw new BusinessException(
                        ErrorCode.COMMON_BAD_REQUEST,
                        Map.of("message", "이미 오늘 보드에 추가된 작업입니다.")
                );
            }
        }

        int nextSlotOrder = activeEntries.size() + 1;
        DailyBig3Entry newEntry = DailyBig3Entry.create(
                dailyBig3Board,
                big3Item,
                nextSlotOrder,
                SelectionSource.CARRYOVER,
                nowAt
        );
        dailyBig3EntryRepository.save(newEntry);
        // 응답을 위해 리스트에 추가
        List<DailyBig3Entry> updatedEntries = new ArrayList<>(activeEntries);
        updatedEntries.add(newEntry);


        return DailyBig3BoardResponse.from(dailyBig3Board, updatedEntries);

    }*/

    /**
     * 지난주에 하지못했던 Big3를 이번주로 넘긴다.
     * @param userId
     * @param big3ItemIds
     */
    @Transactional
    public DailyBig3BoardResponse continueLastWeekWork(String userId, List<String> big3ItemIds) {

        OffsetDateTime selectedAt = OffsetDateTime.now();
        LocalDate today = selectedAt.toLocalDate();
        LocalDate currentWeekStart = selectedAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastWeekStart = currentWeekStart.minusWeeks(1);

        List<String> uniqueIds = big3ItemIds.stream().distinct().toList();
        if (uniqueIds.size() != big3ItemIds.size()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "중복된 itemId는 허용되지 않습니다."
            );
        }

        List<Big3Item> big3Items = big3ItemRepository.findAllByIdInAndUserId(uniqueIds, userId);

        if (big3Items.size() != big3ItemIds.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        List<Big3Item> newBig3Items = new ArrayList<>();

        for (Big3Item big3Item : big3Items) {

            validateExpired(big3Item);
            validateIsLastWeekItem(big3Item, lastWeekStart);
            validateNotContinuedYet(big3Item);

            Big3Item newBig3Item = Big3Item.create(userId, today, big3Item.getOriginInboxItem(), selectedAt);
            newBig3Item.putDerivedFromItem(big3Item);

            newBig3Items.add(newBig3Item);

        }

        big3ItemRepository.saveAll(newBig3Items);

        DailyBig3Board dailyBig3Board = resolveDailyBoard(userId, today, selectedAt);

        List<DailyBig3Entry> activeEntries = dailyBig3EntryRepository.findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc(dailyBig3Board.getId());

        if (activeEntries.size() + newBig3Items.size() > 3) {
            throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, "보드 자리가 꽉 차서 다 넘길 수 없습니다.");
        }

        int nextSlotOrder = activeEntries.size() + 1;
        List<DailyBig3Entry> newEntries = new ArrayList<>();
        for (Big3Item newItem : newBig3Items) {
            DailyBig3Entry newEntry = DailyBig3Entry.create(
                    dailyBig3Board,
                    newItem,
                    nextSlotOrder++, // 1, 2, 3 순서대로 증가
                    SelectionSource.NEW, // 이전 주에서 넘어온 거지만 이번 주 입장에서는 NEW
                    selectedAt
            );
            newEntries.add(newEntry);
        }
        dailyBig3EntryRepository.saveAll(newEntries);

        return DailyBig3BoardResponse.from(dailyBig3Board, newEntries);
    }

    private void validateNotContinuedYet(Big3Item big3Item) {
        if (big3ItemRepository.existsByDerivedFromItem_Id(big3Item.getId())) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "이미 이번 주로 이어간 작업입니다."
            );
        }
    }

    private void validateIsLastWeekItem(Big3Item big3Item, LocalDate lastWeekStart) {
        if(!big3Item.getWeekStart().equals(lastWeekStart))  {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "지난 주 작업에 대해서만 가능합니다."
            );
        }
    }

    private void validateExpired(Big3Item big3Item) {
        if(big3Item.getStatus() != EXPIRED) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "만료된 작업만 이어갈 수 있습니다."
            );
        }
    }

    /**
     * 지난 주 작업을 만료시킨다.
     * scheduling vs batch
     */
    @Transactional
    public void expireLastWeekTasks() {
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate currentWeekStart = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<Big3Item> big3Items = big3ItemRepository.findAllByStatusAndWeekStartBefore(OPEN, currentWeekStart);

        for (Big3Item big3Item : big3Items) {
            big3Item.expire(now);
        }
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

    private List<InboxItem> validateAndFetchInboxItems(String userId, List<String> itemIds) {
        List<String> uniqueItemIds = deduplicate(itemIds);
        validateNoDuplicateItemIds(uniqueItemIds, itemIds);

        List<InboxItem> selectedItems = findInboxItemsInRequestOrder(userId, uniqueItemIds);
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
        return selectedItems;
    }

    private DailyBig3Board resolveDailyBoard(String userId, LocalDate selectedDate, OffsetDateTime selectedAt) {
        DailyBig3Board dailyBig3Board = dailyBig3BoardRepository.findByUserIdAndSelectedDate(userId, selectedDate)
                .orElseGet(() -> DailyBig3Board.create(userId, selectedDate, selectedAt));
        return dailyBig3BoardRepository.save(dailyBig3Board);
    }

    private List<Big3Item> resolveOrCreateBig3Items(
            String userId, LocalDate selectedDate, OffsetDateTime selectedAt,
            List<InboxItem> selectedItems, Set<Big3Item> newItems,
            Set<String> activeBoardItemIds
    ) {
        LocalDate weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<String> uniqueItemIds = selectedItems.stream().map(InboxItem::getId).toList();

        Map<String, Big3Item> weeklyItemsByInboxId = big3ItemRepository
                .findAllByUserIdAndWeekStartAndOriginInboxItem_IdIn(userId, weekStart, uniqueItemIds)
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getOriginInboxItem().getId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

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

        // 이미 이번 주에 생성되었던 작업(Carryover 대상)이라면, 상태가 OPEN인지 반드시 검증한다.
        for (Big3Item item : selectedBig3Items) {
            boolean completedOnCurrentBoard =
                    item.getStatus() == COMPLETED && activeBoardItemIds.contains(item.getId());
            if (!newItems.contains(item) && item.getStatus() != OPEN && !completedOnCurrentBoard) {
                throw new BusinessException(
                        ErrorCode.COMMON_BAD_REQUEST,
                        Map.of(
                                "itemId", item.getId(),
                                "message", "이미 완료되거나 만료된 작업은 이번 주 보드에 다시 올릴 수 없습니다."
                        )
                );
            }
        }

        big3ItemRepository.saveAll(newItems);
        return selectedBig3Items;
    }

    private List<DailyBig3Entry> replaceBoardEntries(
            DailyBig3Board savedBoard, List<Big3Item> selectedBig3Items,
            Set<Big3Item> newItems, OffsetDateTime selectedAt
    ) {
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

        return dailyBig3EntryRepository.saveAll(replacementEntries);
    }


}
