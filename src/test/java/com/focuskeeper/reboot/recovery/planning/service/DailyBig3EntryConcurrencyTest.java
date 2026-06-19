package com.focuskeeper.reboot.recovery.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Board;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3BoardRepository;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3EntryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class DailyBig3EntryConcurrencyTest {

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private Big3ItemRepository big3ItemRepository;

    @Autowired
    private DailyBig3BoardRepository dailyBig3BoardRepository;

    @Autowired
    private DailyBig3EntryRepository dailyBig3EntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void sameActiveSlotAllowsOneCommitAndRejectsOneInsert() throws Exception {
        String userId = "entry-race-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        DailyBig3Board board = dailyBig3BoardRepository.save(
                DailyBig3Board.create(userId, LocalDate.now(), now)
        );
        Big3Item firstItem = saveBig3Item(userId, "첫 번째 작업", now);
        Big3Item secondItem = saveBig3Item(userId, "두 번째 작업", now);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> first = executor.submit(insertActiveEntry(
                    ready, start, board.getId(), firstItem.getId(), now
            ));
            Future<Void> second = executor.submit(insertActiveEntry(
                    ready, start, board.getId(), secondItem.getId(), now
            ));

            await(ready);
            start.countDown();

            Throwable firstFailure = failureOf(first);
            Throwable secondFailure = failureOf(second);

            long successCount = Stream.of(firstFailure, secondFailure)
                    .filter(Objects::isNull)
                    .count();
            List<Throwable> failures = Stream.of(firstFailure, secondFailure)
                    .filter(Objects::nonNull)
                    .toList();

            assertThat(successCount).isEqualTo(1);
            assertThat(failures)
                    .singleElement()
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(
                    dailyBig3EntryRepository
                            .findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc(board.getId())
            ).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Big3Item saveBig3Item(String userId, String content, OffsetDateTime now) {
        InboxItem inboxItem = inboxItemRepository.save(InboxItem.create(userId, content, now));
        return big3ItemRepository.save(Big3Item.create(userId, LocalDate.now(), inboxItem, now));
    }

    private Callable<Void> insertActiveEntry(
            CountDownLatch ready,
            CountDownLatch start,
            String boardId,
            String itemId,
            OffsetDateTime now
    ) {
        return () -> {
            ready.countDown();
            await(start);
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                    INSERT INTO daily_big3_entries (
                        id,
                        daily_big3_board_id,
                        big3_item_id,
                        slot_order,
                        selection_source,
                        selected_at,
                        removed_at,
                        created_at,
                        updated_at
                    )
                    VALUES (?, ?, ?, 1, 'NEW', ?, NULL, ?, ?)
                    """,
                    UUID.randomUUID().toString(),
                    boardId,
                    itemId,
                    now,
                    now,
                    now
            ));
            return null;
        };
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrency barrier timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency barrier interrupted", exception);
        }
    }

    private Throwable failureOf(Future<?> future) {
        try {
            future.get(10, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException exception) {
            return exception.getCause();
        } catch (Exception exception) {
            return exception;
        }
    }
}
