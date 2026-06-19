package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.recovery.execution.constant.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.constant.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecoverySessionServiceTest {

    @Autowired
    private RecoverySessionRepository recoverySessionRepository;
    @Autowired
    private TimeboxRepository timeboxRepository;
    @Autowired
    private ExecutionUnitRepository executionUnitRepository;
    @Autowired
    private Big3ItemRepository big3ItemRepository;
    @Autowired
    private DailyBig3EntryRepository big3EntryRepository;
    @Autowired
    private DailyBig3BoardRepository dailyBig3BoardRepository;
    @Autowired
    private InboxItemRepository inboxItemRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 동시에_세션_시작하면_하나는_실패해야_한다() throws InterruptedException, ExecutionException {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch bothChecked = new CountDownLatch(2);
        CountDownLatch proceedInsert = new CountDownLatch(1);

        // 0. 테스트용 InboxItem 생성
        InboxItem inboxItem = InboxItem.create("test-user", "테스트 작업 원본", OffsetDateTime.now());
        inboxItem = inboxItemRepository.save(inboxItem);

        // 1. 테스트용 부모 Big3Item 생성
        Big3Item big3Item = Big3Item.create("test-user", LocalDate.now(), inboxItem, null);
        big3Item = big3ItemRepository.save(big3Item);

        // 2. 테스트용 ExecutionUnit 생성
        ExecutionUnit unit = ExecutionUnit.create(big3Item, "활성 Session 중복 생성 문제를 해결해봅시다", OffsetDateTime.now());
        unit = executionUnitRepository.save(unit);

        Timebox timebox = Timebox.create("test-user", unit, TimeboxType.WORK, OffsetDateTime.now(), OffsetDateTime.now().plusHours(6L), false, OffsetDateTime.now());
        timebox = timeboxRepository.save(timebox);

        Timebox finalTimebox = timebox;

        String timeboxId = timebox.getId();

        Callable<Void> task = () -> {
            transactionTemplate.executeWithoutResult(status -> {
                boolean exists = recoverySessionRepository.existsByUserIdAndStatus(
                        "test-user",
                        RecoverySessionStatus.STARTED
                );

                assertThat(exists).isFalse();

                bothChecked.countDown();
                awaitLatch(bothChecked);

                awaitLatch(proceedInsert);

                RecoverySession start = RecoverySession.start(
                        "test-user",
                        timeboxId,
                        OffsetDateTime.now()
                );

                recoverySessionRepository.saveAndFlush(start);
            });

            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);

        awaitLatch(bothChecked);
        proceedInsert.countDown();

        Throwable t1 = getThrowable(f1);
        Throwable t2 = getThrowable(f2);

        executor.shutdown();

        long successCount = Stream.of(t1, t2)
                .filter(Objects::isNull)
                .count();
        long failCount = Stream.of(t1, t2)
                .filter(Objects::nonNull)
                .count();
        Throwable failure = Stream.of(t1, t2)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();

        assertThat(successCount).isEqualTo(1);
        assertThat(failCount).isEqualTo(1);
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);

        long startedCount = recoverySessionRepository.countByUserIdAndStatus(
                "test-user",
                RecoverySessionStatus.STARTED
        );

        assertThat(startedCount).isEqualTo(1);

    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private Throwable getThrowable(Future<?> future) {
        try {
            future.get();
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        }
    }

}
