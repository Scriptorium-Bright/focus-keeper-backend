package com.focuskeeper.reboot.recovery.planning.service;

import static com.focuskeeper.reboot.recovery.planning.constant.Big3ItemStatus.EXPIRED;
import static com.focuskeeper.reboot.recovery.planning.constant.Big3ItemStatus.OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class Big3CarryoverConcurrencyReproductionTest {

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private Big3ItemRepository big3ItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("P-05: derived_from_item_id unique가 없으면 같은 source item에서 carryover item이 중복 생성된다")
    void reproduceDuplicateCarryoverItemsFromSameSourceItem() throws Exception {
        String userId = "carryover-race-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        InboxItem inboxItem = inboxItemRepository.save(
                InboxItem.create(userId, "지난주 이어하기 대상", now.minusWeeks(2))
        );
        LocalDate lastWeekStart = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
        Big3Item sourceItem = Big3Item.create(userId, lastWeekStart, inboxItem, now.minusWeeks(1));
        sourceItem.expire(now.minusDays(1));
        sourceItem = big3ItemRepository.save(sourceItem);

        CountDownLatch bothChecked = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            String sourceItemId = sourceItem.getId();
            for (int index = 0; index < 2; index++) {
                executor.submit(() -> {
                    await(start);
                    transactionTemplate.executeWithoutResult(status -> {
                        boolean alreadyContinued = big3ItemRepository.existsByDerivedFromItem_Id(sourceItemId);
                        assertThat(alreadyContinued).isFalse();
                        bothChecked.countDown();
                        await(bothChecked);

                        Big3Item reloadedSource = big3ItemRepository.findById(sourceItemId).orElseThrow();
                        Big3Item derivedItem = Big3Item.create(userId, now.toLocalDate(), inboxItem, now);
                        derivedItem.putDerivedFromItem(reloadedSource);
                        big3ItemRepository.save(derivedItem);
                    });
                });
            }

            start.countDown();
            await(bothChecked);
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            Long duplicateCount = jdbcTemplate.queryForObject(
                    "select count(*) from big3_items where derived_from_item_id = ? and status = ?",
                    Long.class,
                    sourceItem.getId(),
                    OPEN.name()
            );
            assertThat(duplicateCount).isEqualTo(2);
            assertThat(sourceItem.getStatus()).isEqualTo(EXPIRED);
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
