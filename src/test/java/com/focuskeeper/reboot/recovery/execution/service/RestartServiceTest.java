package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.recovery.execution.constant.FailureReason;
import com.focuskeeper.reboot.recovery.execution.constant.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.constant.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RestartServiceTest {

    private static final Logger log = LoggerFactory.getLogger(RestartServiceTest.class);
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
    @Autowired
    private RestartEventRepository restartEventRepository;
    @Autowired
    private RestartService restartService;
    @Autowired
    private FailureEventRepository failureEventRepository;


    @Test
    void 재시작_요청에서_시작이_두개이상이면_안된다() {
        String userId = "restart-race-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        // 0. 테스트용 InboxItem 생성
        InboxItem inboxItem = InboxItem.create(userId, "테스트 작업 원본", OffsetDateTime.now());
        inboxItem = inboxItemRepository.save(inboxItem);

        // 1. 테스트용 부모 Big3Item 생성
        Big3Item big3Item = Big3Item.create(userId, LocalDate.now(), inboxItem, null);
        big3Item = big3ItemRepository.save(big3Item);

        // 2. 테스트용 ExecutionUnit 생성
        ExecutionUnit unit = ExecutionUnit.create(big3Item, "활성 Session 중복 생성 문제를 해결해봅시다", OffsetDateTime.now());
        unit = executionUnitRepository.save(unit);

        Timebox timebox = Timebox.create(userId, unit, TimeboxType.WORK, OffsetDateTime.now(), OffsetDateTime.now().plusHours(6L), true, OffsetDateTime.now());
        timebox = timeboxRepository.save(timebox);

        Timebox finalTimebox = timebox;
        log.info("timebox Id = {}", timebox.getId());


        RecoverySession recoverySession = RecoverySession.start(userId, timebox.getId(), OffsetDateTime.now());
        recoverySession.stopped(OffsetDateTime.now().plusMinutes(5L));
        RecoverySession savedSession = recoverySessionRepository.saveAndFlush(recoverySession);
        log.info("session Id = {}", savedSession.getId());


        FailureEvent failureEvent = FailureEvent.create(userId, savedSession.getId(), timebox.getId(), FailureReason.INTERRUPTION, "hihihi", OffsetDateTime.now());
        FailureEvent savedFailureEvent = failureEventRepository.saveAndFlush(failureEvent);
        log.info("failureEvent Id = {}", failureEvent.getId());
        Callable<Void> task = () -> {
            ready.countDown();
            awaitLatch(ready);
            awaitLatch(start);

            restartService.restart(userId, savedFailureEvent.getId());

            return null;
        };

        Future<Void> f1 = executor.submit(task);
        Future<Void> f2 = executor.submit(task);

        awaitLatch(ready);
        start.countDown();

        Throwable t1 = getThrowable(f1);
        Throwable t2 = getThrowable(f2);

        executor.shutdown();

        System.out.println("session total = " + recoverySessionRepository.count());
        System.out.println("restartEvent total = " + restartEventRepository.count());

        recoverySessionRepository.findAll()
                .forEach(s -> System.out.println("session = " + s));

        restartEventRepository.findAll()
                .forEach(e -> System.out.println("restartEvent = " + e));

        long activeSessionCount =
                recoverySessionRepository.countByUserIdAndStatus(
                        userId,
                        RecoverySessionStatus.STARTED
                );

        long restartEventCount =
                restartEventRepository.countByFailureEventId(failureEvent.getId());

        assertThat(activeSessionCount).isEqualTo(1);
        assertThat(restartEventCount).isEqualTo(1);

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
