package com.focuskeeper.reboot.recovery.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpringBootTest
class ExecutionUnitMaxCountConcurrencyReproductionTest {

    @Autowired
    private ExecutionUnitService executionUnitService;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private Big3ItemRepository big3ItemRepository;

    @SpyBean
    private ExecutionUnitRepository executionUnitRepository;

    @Test
    @DisplayName("P-11: 최대 5개 검증이 count 후 insert라서 동시 생성 시 6개까지 저장될 수 있다")
    void reproduceExecutionUnitMaxCountWriteSkew() throws Exception {
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

        CountDownLatch bothPassedValidation = new CountDownLatch(2);
        doAnswer(invocation -> {
            ExecutionUnit executionUnit = invocation.getArgument(0);
            if (executionUnit.getTitle().startsWith("race-")) {
                bothPassedValidation.countDown();
                await(bothPassedValidation);
            }
            return invocation.callRealMethod();
        }).when(executionUnitRepository).save(any(ExecutionUnit.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() ->
                    executionUnitService.singleInsertUnit(userId, big3Item.getId(), "race-1")
            );
            Future<?> second = executor.submit(() ->
                    executionUnitService.singleInsertUnit(userId, big3Item.getId(), "race-2")
            );

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            List<ExecutionUnit> savedUnits =
                    executionUnitRepository.findAllByBig3Item_IdAndBig3Item_UserIdOrderByCreatedAtAsc(
                            big3Item.getId(),
                            userId
                    );
            assertThat(savedUnits).hasSize(6);
            assertThat(savedUnits)
                    .extracting(ExecutionUnit::getTitle)
                    .contains("race-1", "race-2");
        } finally {
            executor.shutdownNow();
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
