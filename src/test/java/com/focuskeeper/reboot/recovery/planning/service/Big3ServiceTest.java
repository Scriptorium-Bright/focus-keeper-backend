package com.focuskeeper.reboot.recovery.planning.service;

import static com.focuskeeper.reboot.recovery.planning.constant.Big3ItemStatus.EXPIRED;
import static com.focuskeeper.reboot.recovery.planning.constant.Big3ItemStatus.OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.constant.SelectionSource;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Board;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Entry;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3BoardRepository;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3EntryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class Big3ServiceTest {

    private static final String USER_ID = "continue-user";
    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.parse("2026-06-10T10:00:00+09:00");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);
    private static final LocalDate CURRENT_WEEK_START = LocalDate.of(2026, 6, 8);
    private static final LocalDate LAST_WEEK_START = LocalDate.of(2026, 6, 1);

    private final InboxItemRepository inboxItemRepository = mock(InboxItemRepository.class);
    private final DailyBig3BoardRepository dailyBig3BoardRepository = mock(DailyBig3BoardRepository.class);
    private final DailyBig3EntryRepository dailyBig3EntryRepository = mock(DailyBig3EntryRepository.class);
    private final Big3ItemRepository big3ItemRepository = mock(Big3ItemRepository.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final Big3Service big3Service = new Big3Service(
            inboxItemRepository,
            dailyBig3BoardRepository,
            dailyBig3EntryRepository,
            big3ItemRepository,
            transactionTemplate
    );

    @Test
    void weeklySweepExpiresPastOpenItemsAtCurrentTime() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Integer> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(big3ItemRepository.expirePastOpenItem(
                FIXED_NOW,
                OPEN.name(),
                EXPIRED.name(),
                CURRENT_WEEK_START,
                100_000
        )).thenReturn(42);

        int processedItems;
        try (MockedStatic<OffsetDateTime> mockedTime = mockStatic(OffsetDateTime.class)) {
            mockedTime.when(OffsetDateTime::now).thenReturn(FIXED_NOW);

            processedItems = big3Service.expireLastWeekTasks();
        }

        assertThat(processedItems).isEqualTo(42);
        verify(big3ItemRepository).expirePastOpenItem(
                FIXED_NOW,
                OPEN.name(),
                EXPIRED.name(),
                CURRENT_WEEK_START,
                100_000
        );
    }

    @Test
    void continueLastWeekWorkCreatesNewItemOnTodaysBoard() {
        Big3Item sourceItem = createExpiredItem("source-item", LAST_WEEK_START);
        when(big3ItemRepository.findAllByIdInAndUserId(List.of("source-item"), USER_ID))
                .thenReturn(List.of(sourceItem));
        when(big3ItemRepository.existsByDerivedFromItem_Id("source-item")).thenReturn(false);
        when(dailyBig3BoardRepository.findByUserIdAndSelectedDate(USER_ID, TODAY))
                .thenReturn(Optional.empty());
        when(dailyBig3BoardRepository.save(any(DailyBig3Board.class)))
                .thenAnswer(invocation -> {
                    DailyBig3Board board = invocation.getArgument(0);
                    ReflectionTestUtils.setField(board, "id", "today-board");
                    return board;
                });
        when(dailyBig3EntryRepository
                .findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc("today-board"))
                .thenReturn(List.of());

        AtomicReference<List<Big3Item>> savedItems = new AtomicReference<>();
        when(big3ItemRepository.saveAll(anyList()))
                .thenAnswer(invocation -> {
                    List<Big3Item> items = invocation.getArgument(0);
                    savedItems.set(items);
                    return items;
                });

        AtomicReference<List<DailyBig3Entry>> savedEntries = new AtomicReference<>();
        when(dailyBig3EntryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> {
                    List<DailyBig3Entry> entries = invocation.getArgument(0);
                    savedEntries.set(entries);
                    return entries;
                });

        try (MockedStatic<OffsetDateTime> mockedTime = mockStatic(OffsetDateTime.class)) {
            mockedTime.when(OffsetDateTime::now).thenReturn(FIXED_NOW);

            big3Service.continueLastWeekWork(USER_ID, List.of("source-item"));
        }

        assertThat(savedItems.get()).hasSize(1);
        Big3Item continuedItem = savedItems.get().getFirst();
        assertThat(continuedItem.getStatus()).isEqualTo(OPEN);
        assertThat(continuedItem.getWeekStart()).isEqualTo(CURRENT_WEEK_START);
        assertThat(continuedItem.getDerivedFromItem()).isSameAs(sourceItem);
        assertThat(sourceItem.getStatus()).isEqualTo(EXPIRED);

        assertThat(savedEntries.get()).hasSize(1);
        DailyBig3Entry entry = savedEntries.get().getFirst();
        assertThat(entry.getDailyBig3Board().getSelectedDate()).isEqualTo(TODAY);
        assertThat(entry.getBig3Item()).isSameAs(continuedItem);
        assertThat(entry.getSelectionSource()).isEqualTo(SelectionSource.NEW);
    }

    @Test
    void continueLastWeekWorkFailsWhenExistingActiveEntryMakesNewEntryStartAtSlotTwo() {
        Big3Item sourceItem = createExpiredItem("source-item", LAST_WEEK_START);
        DailyBig3Board board = DailyBig3Board.create(USER_ID, TODAY, FIXED_NOW);
        ReflectionTestUtils.setField(board, "id", "today-board");
        Big3Item activeItem = createItem("active-item", CURRENT_WEEK_START);
        DailyBig3Entry activeEntry = DailyBig3Entry.create(
                board,
                activeItem,
                1,
                SelectionSource.NEW,
                FIXED_NOW.minusHours(1)
        );

        when(big3ItemRepository.findAllByIdInAndUserId(List.of("source-item"), USER_ID))
                .thenReturn(List.of(sourceItem));
        when(big3ItemRepository.existsByDerivedFromItem_Id("source-item")).thenReturn(false);
        when(big3ItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dailyBig3BoardRepository.findByUserIdAndSelectedDate(USER_ID, TODAY))
                .thenReturn(Optional.of(board));
        when(dailyBig3BoardRepository.save(board)).thenReturn(board);
        when(dailyBig3EntryRepository
                .findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc("today-board"))
                .thenReturn(List.of(activeEntry));

        try (MockedStatic<OffsetDateTime> mockedTime = mockStatic(OffsetDateTime.class)) {
            mockedTime.when(OffsetDateTime::now).thenReturn(FIXED_NOW);

            assertThatThrownBy(() -> big3Service.continueLastWeekWork(USER_ID, List.of("source-item")))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SYSTEM_INTERNAL_ERROR);
                        assertThat(exception.getDetails()).isEqualTo("slot의 순서가 잘못되었습니다.");
                    });
        }
    }

    @Test
    void continueLastWeekWorkRejectsOpenItem() {
        Big3Item openItem = createItem("open-item", LAST_WEEK_START);
        when(big3ItemRepository.findAllByIdInAndUserId(List.of("open-item"), USER_ID))
                .thenReturn(List.of(openItem));

        assertContinueFails(
                List.of("open-item"),
                "만료된 작업만 이어갈 수 있습니다."
        );

        verify(big3ItemRepository, never()).saveAll(anyList());
        verifyNoInteractions(dailyBig3BoardRepository, dailyBig3EntryRepository);
    }

    @Test
    void continueLastWeekWorkRejectsExpiredItemOutsideLastWeek() {
        Big3Item olderExpiredItem = createExpiredItem(
                "older-expired-item",
                LAST_WEEK_START.minusWeeks(1)
        );
        when(big3ItemRepository.findAllByIdInAndUserId(List.of("older-expired-item"), USER_ID))
                .thenReturn(List.of(olderExpiredItem));

        assertContinueFails(
                List.of("older-expired-item"),
                "지난 주 작업에 대해서만 가능합니다."
        );

        verify(big3ItemRepository, never()).saveAll(anyList());
        verifyNoInteractions(dailyBig3BoardRepository, dailyBig3EntryRepository);
    }

    @Test
    void continueLastWeekWorkRejectsAlreadyContinuedItem() {
        Big3Item sourceItem = createExpiredItem("continued-source-item", LAST_WEEK_START);
        when(big3ItemRepository.findAllByIdInAndUserId(List.of("continued-source-item"), USER_ID))
                .thenReturn(List.of(sourceItem));
        when(big3ItemRepository.existsByDerivedFromItem_Id("continued-source-item")).thenReturn(true);

        assertContinueFails(
                List.of("continued-source-item"),
                "이미 이번 주로 이어간 작업입니다."
        );

        verify(big3ItemRepository, never()).saveAll(anyList());
        verifyNoInteractions(dailyBig3BoardRepository, dailyBig3EntryRepository);
    }

    @Test
    void continueLastWeekWorkRejectsDuplicateItemIdsBeforeRepositoryLookup() {
        assertContinueFails(
                List.of("source-item", "source-item"),
                "중복된 itemId는 허용되지 않습니다."
        );

        verifyNoInteractions(
                inboxItemRepository,
                big3ItemRepository,
                dailyBig3BoardRepository,
                dailyBig3EntryRepository
        );
    }

    private void assertContinueFails(List<String> itemIds, String expectedDetails) {
        try (MockedStatic<OffsetDateTime> mockedTime = mockStatic(OffsetDateTime.class)) {
            mockedTime.when(OffsetDateTime::now).thenReturn(FIXED_NOW);

            assertThatThrownBy(() -> big3Service.continueLastWeekWork(USER_ID, itemIds))
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMON_BAD_REQUEST);
                        assertThat(exception.getDetails()).isEqualTo(expectedDetails);
                    });
        }
    }

    private Big3Item createExpiredItem(String id, LocalDate selectedDate) {
        Big3Item item = createItem(id, selectedDate);
        item.expire(FIXED_NOW.minusDays(1));
        return item;
    }

    private Big3Item createItem(String id, LocalDate selectedDate) {
        InboxItem inboxItem = InboxItem.create(
                USER_ID,
                "지난주 작업",
                FIXED_NOW.minusWeeks(2)
        );
        Big3Item item = Big3Item.create(
                USER_ID,
                selectedDate,
                inboxItem,
                FIXED_NOW.minusWeeks(1)
        );
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
