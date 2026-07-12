package com.focuskeeper.reboot.recovery.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ExecutionUnitMaxCountConcurrencyReproductionTest {

    @Autowired
    private ExecutionUnitService executionUnitService;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private Big3ItemRepository big3ItemRepository;

    @Autowired
    private ExecutionUnitRepository executionUnitRepository;

    @Test
    @DisplayName("P-11: 부모 row lock이 동시 생성을 직렬화해 최대 5개를 유지한다")
    void preventsExecutionUnitMaxCountWriteSkew() throws Exception {
        String userId = "execution-unit-max-race-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        InboxItem inboxItem = inboxItemRepository.save(
                InboxItem.create(userId, "실행 단위 개수 경합", now)
        );
        Big3Item big3Item = big3ItemRepository.save(
                Big3Item.create(userId, LocalDate.now(), inboxItem, now)
        );
        for (int index = 0; index < 4; index++) {
            executionUnitRepository.save(
                    ExecutionUnit.create(big3Item, "existing-" + index, now)
            );
        }

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                await(start);
                return executionUnitService.singleInsertUnit(userId, big3Item.getId(), "race-1");
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                await(start);
                return executionUnitService.singleInsertUnit(userId, big3Item.getId(), "race-2");
            });

            await(ready);
            start.countDown();
            Throwable firstFailure = getThrowable(first);
            Throwable secondFailure = getThrowable(second);

            assertThat(Stream.of(firstFailure, secondFailure).filter(failure -> failure == null).count())
                    .isEqualTo(1);
            assertThat(Stream.of(firstFailure, secondFailure).filter(failure -> failure != null).count())
                    .isEqualTo(1);
            assertThat(Stream.of(firstFailure, secondFailure)
                    .filter(failure -> failure != null)
                    .findFirst()
                    .orElseThrow())
                    .isInstanceOf(BusinessException.class);

            List<ExecutionUnit> savedUnits =
                    executionUnitRepository.findAllByBig3Item_IdAndBig3Item_UserIdOrderByCreatedAtAsc(
                            big3Item.getId(),
                            userId
                    );
            assertThat(savedUnits).hasSize(5);
            assertThat(savedUnits)
                    .extracting(ExecutionUnit::getTitle)
                    .anyMatch(title -> title.equals("race-1") || title.equals("race-2"));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Throwable getThrowable(Future<?> future) {
        try {
            future.get(10, TimeUnit.SECONDS);
            return null;
        } catch (java.util.concurrent.ExecutionException exception) {
            return exception.getCause();
        } catch (Exception exception) {
            return exception;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrency barrier timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency barrier interrupted", exception);
        }
    }
}
