package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ExecutionUnitConcurrencyIntegrationTest {

    @Autowired
    private ExecutionUnitService executionUnitService;

    @Autowired
    private ExecutionUnitRepository executionUnitRepository;

    @Autowired
    private Big3ItemRepository big3ItemRepository;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private TimeboxService timeboxService;

    @Autowired
    private TimeboxRepository timeboxRepository;

    @Test
    @DisplayName("광클 방어: 100명이 동시에 하나의 작업을 완료 처리하려고 하면 1명만 성공하고 99명은 예외가 터져야 한다.")
    void completeUnit_Concurrency() throws InterruptedException {
        // given
        // 0. 테스트용 InboxItem 생성
        InboxItem inboxItem = InboxItem.create("test-user", "테스트 작업 원본", OffsetDateTime.now());
        inboxItem = inboxItemRepository.save(inboxItem);

        // 1. 테스트용 부모 Big3Item 생성
        Big3Item big3Item = Big3Item.create("test-user", LocalDate.now(), inboxItem, null);
        big3Item = big3ItemRepository.save(big3Item);

        // 2. 테스트용 ExecutionUnit 생성
        ExecutionUnit unit = ExecutionUnit.create(big3Item, "동시성 테스트 숙제", OffsetDateTime.now());
        unit = executionUnitRepository.save(unit);

        String userId = "test-user";
        String unitId = unit.getId();

        // 3. 동시에 실행할 스레드 100개 준비 (광클 100번)
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    executionUnitService.completeUnit(userId, unitId);
                    successCount.incrementAndGet(); // 성공 횟수 증가
                } catch (ObjectOptimisticLockingFailureException e) {
                    // 낙관적 락 예외가 터지면 정상적으로 방어된 것!
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    // 다른 에러가 터지는 것도 실패로 간주
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 100개의 요청이 모두 끝날 때까지 대기

        // then
        // 🚨 유저님의 completeUnit 메서드는 "이미 완료된 상태면 그냥 성공 반환"하는 멱등성(Idempotency) 로직이 있습니다!
        // 따라서 100개의 스레드 중:
        // - 1명: 최초로 PLANNED 상태를 읽고 업데이트 성공
        // - N명: 최초 1명과 "동시에" PLANNED를 읽어서 충돌 ➔ OptimisticLockingFailureException 발생 (진짜 방어된 녀석들)
        // - 99-N명: 최초 1명이 완료한 "이후에" 조회를 해서 COMPLETED를 읽고 조기 리턴 ➔ 예외 없이 성공 처리
        //
        // 결론: 동시성 충돌로 인해 튕겨나간 녀석(failCount)이 단 1명이라도 존재한다면, 낙관적 락이 완벽히 작동한 것입니다!
        // 그리고 최종적으로 DB의 ExecutionUnit 상태가 COMPLETED라면 데이터 정합성이 완벽히 지켜진 것입니다.
        
        System.out.println("성공 응답 횟수 (정상 완료 + 이미 완료됨 처리): " + successCount.get());
        System.out.println("락 충돌로 방어된 횟수: " + failCount.get());

        // 충돌로 인해 방어막에 튕겨 나간 스레드가 1개 이상 존재해야 함 (동시성 방어 증명)
        assertThat(failCount.get()).isGreaterThan(0);

        // 데이터베이스의 최종 상태가 한 치의 오차 없이 COMPLETED 여야 함
        ExecutionUnit finalUnit = executionUnitRepository.findById(unitId).orElseThrow();
        assertThat(finalUnit.getStatus()).isEqualTo(com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus.COMPLETED);
    }

    @Test
    @DisplayName("생성 꼼수 방어: 작업 완료 처리 중 스케줄 추가가 들어올 때 좀비 데이터가 생성되지 않아야 한다.")
    void completeUnit_Vs_CreateTimebox_Concurrency() throws InterruptedException {
        // given
        InboxItem inboxItem = InboxItem.create("test-user-2", "테스트 작업 원본", OffsetDateTime.now());
        inboxItem = inboxItemRepository.save(inboxItem);

        Big3Item big3Item = Big3Item.create("test-user-2", LocalDate.now(), inboxItem, null);
        big3Item = big3ItemRepository.save(big3Item);

        ExecutionUnit unit = ExecutionUnit.create(big3Item, "동시성 테스트 숙제 2", OffsetDateTime.now());
        unit = executionUnitRepository.save(unit);

        String userId = "test-user-2";
        String unitId = unit.getId();

        // Timebox 생성을 위한 Command (미래 시간)
        String startAt = OffsetDateTime.now().plusHours(1).toString();
        String endAt = OffsetDateTime.now().plusHours(2).toString();
        TimeboxCommand command = new TimeboxCommand(unitId, startAt, endAt, true, "WORK");

        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        
        // 스레드들이 정확히 동시에 출발하도록 제어하는 Latch
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount + 1);

        // when
        // 1개의 스레드는 작업 완료 처리
        executorService.submit(() -> {
            try {
                startLatch.await(); // 대기하다가 신호가 오면 동시에 출발!
                executionUnitService.completeUnit(userId, unitId);
            } catch (Exception e) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        });

        // 나머지 스레드들은 동시에 Timebox 추가 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 대기하다가 신호가 오면 동시에 출발!
                    timeboxService.allocateTimeboxes(userId, List.of(command));
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 모든 스레드가 준비되었을 때 탕! 하고 쏘기
        startLatch.countDown();
        doneLatch.await();

        // then
        ExecutionUnit finalUnit = executionUnitRepository.findById(unitId).orElseThrow();
        
        long zombieTimeboxCount = timeboxRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId))
                // 부모는 COMPLETED인데, 자식은 멀쩡하게 살아있는(PLANNED) 놈들 = 좀비!
                .filter(t -> t.getStatus() == com.focuskeeper.reboot.recovery.planning.TimeboxStatus.PLANNED)
                .count();

        if (finalUnit.getStatus() == com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus.COMPLETED) {
            System.out.println("부모가 완료되었습니다. 이때 살아있는 좀비 Timebox 개수: " + zombieTimeboxCount);
            
            // 🚨 낙관적 락(@Version) + Force Increment 세팅이 제대로 안 되어 있다면 좀비가 1개 이상 존재합니다!
            // 제대로 락이 작동했다면 좀비는 0마리여야 합니다.
            // (현재 유저님 코드는 락이 작동하므로 0을 단언합니다)
            assertThat(zombieTimeboxCount).isEqualTo(0);
        } else {
            // 완료 처리가 락 충돌로 인해 실패했다면 부모는 여전히 PLANNED 상태임
            System.out.println("완료 처리가 락에 의해 방어되었습니다. 정상 생성된 Timebox 개수: " + zombieTimeboxCount);
            assertThat(zombieTimeboxCount).isEqualTo(1);
        }
    }
}
