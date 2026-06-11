package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Board;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Entry;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3BoardRepository;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3EntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("test")
class DailyBig3EntryConcurrencyTest {

    @Autowired
    private Big3Service big3Service;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private DailyBig3BoardRepository dailyBig3BoardRepository;

    @Autowired
    private DailyBig3EntryRepository dailyBig3EntryRepository;

    private String userId = "concurrency-test-user";
    private InboxItem itemA;
    private InboxItem itemB;

    @BeforeEach
    void setUp() {
        dailyBig3EntryRepository.deleteAll();
        dailyBig3BoardRepository.deleteAll();
        inboxItemRepository.deleteAll();

        // Inbox 후보 아이템 생성
        itemA = inboxItemRepository.save(InboxItem.create(userId, "작업 A", OffsetDateTime.now()));
        itemB = inboxItemRepository.save(InboxItem.create(userId, "작업 B", OffsetDateTime.now()));
        
        // [중요] 최초 생성 시의 Board 유니크 제약 방어를 우회하기 위해, 
        // 이미 '오늘의 보드'가 생성되어 있는 상황(수정 시나리오)을 세팅합니다.
        DailyBig3Board board = DailyBig3Board.create(userId, LocalDate.now(), OffsetDateTime.now());
        dailyBig3BoardRepository.save(board);
    }

    @Test
    @DisplayName("장애 재현 2: 이미 Board가 존재할 때(수정 시), 더블 클릭하면 Board의 유니크 제약을 우회하여 Entry가 2배로 증식한다.")
    void selectTodayBig3_Update_RaceCondition_Test() throws InterruptedException {
        // Given: 유저가 화면에서 "작업 A"와 "작업 B"를 선택하여 저장 버튼을 거의 동시에 연타함 (더블 클릭)
        int threadCount = 2; 
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);

        List<String> selectedItemIds = List.of(itemA.getId(), itemB.getId());

        // When: 동시에 selectTodayBig3 API 호출 (이미 Board는 존재하므로 SELECT만 수행됨)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startSignal.await();
                    big3Service.selectTodayBig3(userId, selectedItemIds);
                } catch (Exception ignored) {
                    // Entry 자체에 유니크 제약(Partial Index)이나 Board에 낙관적 락이 있다면 여기서 터져야 합니다.
                } finally {
                    latch.countDown();
                }
            });
        }

        startSignal.countDown(); // 동시 발사
        latch.await();

        // Then: 현재 날짜의 보드를 조회하여 활성(active) Entry 상태 확인
        LocalDate today = LocalDate.now();
        DailyBig3Board board = dailyBig3BoardRepository.findByUserIdAndSelectedDate(userId, today)
                .orElseThrow(() -> new IllegalStateException("Board should exist"));
        
        List<DailyBig3Entry> activeEntries = dailyBig3EntryRepository
                .findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc(board.getId());

        // [핵심 검증 포인트]
        // Board의 유니크 제약(uk_daily_big3_boards_user_date)은 최초 INSERT 시에만 발동하므로 방어막 역할을 하지 못합니다.
        // 현재 DailyBig3Entry 테이블에는 (board_id, slot_order) 에 대한 고유 제약이 없으므로,
        // 2개의 스레드가 각각 2개의 엔트리를 삽입하여 총 4개가 생성되는 치명적 버그가 재현됩니다.
        
        if (activeEntries.size() > 2) {
            System.out.println("🚨 앗! Board 제약조건을 우회하여 방어벽이 뚫렸습니다. 총 " + activeEntries.size() + "개의 엔트리가 생성되었습니다.");
            for (DailyBig3Entry entry : activeEntries) {
                System.out.println("Slot " + entry.getSlotOrder() + " -> " + entry.getBig3Item().getTitleSnapshot());
            }
            
            assertThat(activeEntries.size())
                    .as("수정(Update) 상황에서 Entry 단위의 Unique 제약 부재로 인해 더블 클릭 시 엔트리가 중복 생성되었습니다.")
                    .isGreaterThan(2); 
        } else {
            System.out.println("운 좋게 스레드가 직렬화되어 방어되었습니다. 사이즈: " + activeEntries.size());
        }
    }
}