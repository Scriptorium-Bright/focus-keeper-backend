package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.*;
import com.focuskeeper.reboot.recovery.planning.service.TimeboxService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecoverySessionServiceTest {

    private static final Logger log = LoggerFactory.getLogger(RecoverySessionServiceTest.class);
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

        System.out.println("t1 = " + t1);
        System.out.println("t2 = " + t2);

        long count = recoverySessionRepository.count();
        System.out.println("recoverySession count = " + count);

        executor.shutdown();

        long failCount = Stream.of(t1, t2)
                .filter(Objects::nonNull)
                .count();

        /**
         *
         * Unique Index를 적용하기 전, 사용자가 동시에 Active Session이 없음을 확인하고, 두 트랜잭션이 동시에 Started를 누르면 활성화된 Session이 중복생성됨을 알 수 있음
         *
         */

        assertThat(failCount).isEqualTo(0);

        long startedCount = recoverySessionRepository.countByUserIdAndStatus(
                "test-user",
                RecoverySessionStatus.STARTED
        );

        log.info("started Count = {}", startedCount);

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

/**
 * Partitial Unique Index 적용시
 * 2026-06-10T22:45:42.430+09:00  INFO 25187 --- [pool-2-thread-2] org.hibernate.orm.jdbc.batch             : HHH100503: On release of batch it still contained JDBC statements
 * 2026-06-10T22:45:42.430+09:00 ERROR 25187 --- [pool-2-thread-2] org.hibernate.orm.jdbc.batch             : HHH100501: Exception executing batch [java.sql.BatchUpdateException: Batch entry 0 insert into recovery_session (created_at,ended_at,recovery_end_reason,started_at,status,timebox_id,user_id,version,id) values (('2026-06-10 13:45:42.423742+00'),(NULL),(NULL),('2026-06-10 13:45:42.423742+00'),('STARTED'),('ae3c5e10-350f-44fa-a8c0-32e4e6bc0a15'),('test-user'),('0'::int8),('75061d5f-59ee-440a-9969-994867191c52')) was aborted: ERROR: duplicate key value violates unique constraint "uq_recovery_session_active"
 *   Detail: Key (user_id)=(test-user) already exists.  Call getNextException to see other errors in the batch.], SQL: insert into recovery_session (created_at,ended_at,recovery_end_reason,started_at,status,timebox_id,user_id,version,id) values (?,?,?,?,?,?,?,?,?)
 * 2026-06-10T22:45:42.431+09:00  WARN 25187 --- [pool-2-thread-2] o.h.engine.jdbc.spi.SqlExceptionHelper   : SQL Error: 0, SQLState: 23505
 * 2026-06-10T22:45:42.431+09:00 ERROR 25187 --- [pool-2-thread-2] o.h.engine.jdbc.spi.SqlExceptionHelper   : Batch entry 0 insert into recovery_session (created_at,ended_at,recovery_end_reason,started_at,status,timebox_id,user_id,version,id) values (('2026-06-10 13:45:42.423742+00'),(NULL),(NULL),('2026-06-10 13:45:42.423742+00'),('STARTED'),('ae3c5e10-350f-44fa-a8c0-32e4e6bc0a15'),('test-user'),('0'::int8),('75061d5f-59ee-440a-9969-994867191c52')) was aborted: ERROR: duplicate key value violates unique constraint "uq_recovery_session_active"
 *   Detail: Key (user_id)=(test-user) already exists.  Call getNextException to see other errors in the batch.
 * 2026-06-10T22:45:42.431+09:00 ERROR 25187 --- [pool-2-thread-2] o.h.engine.jdbc.spi.SqlExceptionHelper   : ERROR: duplicate key value violates unique constraint "uq_recovery_session_active"
 *   Detail: Key (user_id)=(test-user) already exists.
 * t1 = null
 * t2 = org.springframework.dao.DataIntegrityViolationException: could not execute batch [Batch entry 0 insert into recovery_session (created_at,ended_at,recovery_end_reason,started_at,status,timebox_id,user_id,version,id) values (('2026-06-10 13:45:42.423742+00'),(NULL),(NULL),('2026-06-10 13:45:42.423742+00'),('STARTED'),('ae3c5e10-350f-44fa-a8c0-32e4e6bc0a15'),('test-user'),('0'::int8),('75061d5f-59ee-440a-9969-994867191c52')) was aborted: ERROR: duplicate key value violates unique constraint "uq_recovery_session_active"
 *   Detail: Key (user_id)=(test-user) already exists.  Call getNextException to see other errors in the batch.] [insert into recovery_session (created_at,ended_at,recovery_end_reason,started_at,status,timebox_id,user_id,version,id) values (?,?,?,?,?,?,?,?,?)]; SQL [insert into recovery_session (created_at,ended_at,recovery_end_reason,started_at,status,timebox_id,user_id,version,id) values (?,?,?,?,?,?,?,?,?)]; constraint [uq_recovery_session_active]
 * recoverySession count = 1
 *
 * Partitial Unique Index 미적용
 * t1 = null
 * t2 = null
 * recoverySession count = 2
 * 2026-06-10T22:47:25.796+09:00  INFO 25565 --- [    Test worker] c.f.r.r.e.s.RecoverySessionServiceTest   : started Count = 2
 *
 * Expected :1L
 * Actual   :2L
 * <Click to see difference>
 *
 * org.opentest4j.AssertionFailedError:
 * expected: 1L
 *  but was: 2L
 * 	at java.base/jdk.internal.reflect.DirectConstructorHandleAccessor.newInstance(DirectConstructorHandleAccessor.java:62)
 *
 *
 */