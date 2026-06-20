package com.focuskeeper.reboot.recovery.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Board;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3BoardRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
public class Big3ItemConcurrencyReproductionTest {

    private static final Logger log = LoggerFactory.getLogger(Big3ItemConcurrencyReproductionTest.class);

    @Autowired
    private Big3Service big3Service;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private Big3ItemRepository big3ItemRepository;

    @Autowired
    private DailyBig3BoardRepository dailyBig3BoardRepository;

    @Test
    @DisplayName("P-02: DB Unique Index가 없어 동시에 요청 시 동일한 Big3Item이 여러 개 중복 생성되는 문제 재현 테스트")
    void reproduce_big3Item_duplication_on_concurrent_requests() throws InterruptedException {
        // given
        String userId = UUID.randomUUID().toString();
        InboxItem inboxItem = InboxItem.create(userId, "방 청소하기 (동시성 테스트)", OffsetDateTime.now());
        inboxItemRepository.save(inboxItem);
        
        // 보드 생성 시 충돌(Unique Constraint)로 인해 하나의 스레드가 죽는 것을 방지하기 위해 보드 미리 생성
        DailyBig3Board board = DailyBig3Board.create(userId, LocalDate.now(), OffsetDateTime.now());
        dailyBig3BoardRepository.save(board);

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기
                    
                    // 보드 생성 제약조건을 피하고, 순수하게 Big3Item 중복 생성 취약점(P-02)만 고립시켜서 테스트하기 위해
                    // private 메서드인 resolveOrCreateBig3Items를 리플렉션으로 직접 호출합니다.
                    Set<Big3Item> newItems = Collections.newSetFromMap(new IdentityHashMap<>());
                    ReflectionTestUtils.invokeMethod(
                            big3Service,
                            "resolveOrCreateBig3Items",
                            userId, LocalDate.now(), OffsetDateTime.now(), List.of(inboxItem), newItems, Set.of()
                    );
                } catch (Exception e) {
                    log.error("동시성 테스트 중 예외 발생", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(); // 모든 스레드가 준비되었는지 확인
        startLatch.countDown(); // startLatch를 0으로 만들어 동시에 출발시킴!
        doneLatch.await(); // 모든 스레드의 실행이 끝날 때까지 대기

        // then
        // 특정 userId와 inboxItemId 조합으로 생성된 Big3Item 목록 조회
        java.time.LocalDate weekStart = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        List<Big3Item> createdItems = big3ItemRepository.findAllByUserIdAndWeekStartAndOriginInboxItem_IdIn(
                userId, weekStart, List.of(inboxItem.getId())
        );

        // 중복 생성 버그(P-02)가 수정되지 않았으므로, 2개의 스레드가 던진 요청이 모두 성공하여
        // 동일한 주간에 같은 InboxItem을 바라보는 Big3Item이 2개(중복) 생성된다.
        // 만약 추후에 DB Unique 제약조건 등을 통해 문제가 수정된다면 예외가 발생하여 1개만 생성될 것이고, 이 테스트는 실패하게 된다.
        assertThat(createdItems).hasSize(2);
    }
}
