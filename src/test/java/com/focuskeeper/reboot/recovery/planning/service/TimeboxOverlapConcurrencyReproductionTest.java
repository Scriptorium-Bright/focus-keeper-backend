package com.focuskeeper.reboot.recovery.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.constant.SelectionSource;
import com.focuskeeper.reboot.recovery.planning.entity.*;
import com.focuskeeper.reboot.recovery.planning.repository.*;
import com.focuskeeper.reboot.recovery.planning.validation.TimeboxOverlapValidator;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("postgres")
@SpringBootTest
/*@EnabledIfEnvironmentVariable(
        named = "TIMEBOX_OVERLAP_CONCURRENCY_TEST_ENABLED",
        matches = "true"
)*/
class TimeboxOverlapConcurrencyReproductionTest {

    private static final Logger log =
            LoggerFactory.getLogger(TimeboxOverlapConcurrencyReproductionTest.class);
    private static final String OVERLAP_CONSTRAINT =
            "ex_recovery_timeboxes_user_planned_period";

    @Autowired
    private TimeboxService timeboxService;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private DailyBig3BoardRepository dailyBig3BoardRepository;

    @Autowired
    private Big3ItemRepository big3ItemRepository;

    @Autowired
    private ExecutionUnitRepository executionUnitRepository;

    @Autowired
    private TimeboxRepository timeboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

/*    @Autowired
    private GuardRepository guardRepository;*/

    @SpyBean
    private TimeboxOverlapValidator timeboxOverlapValidator;

    // Run against a disposable PostgreSQL database:
// TIMEBOX_OVERLAP_CONCURRENCY_TEST_ENABLED=true \
// ./gradlew test --tests '*TimeboxOverlapConcurrencyReproductionTest' \
//   -Dspring.profiles.active=local
    @Test
    @DisplayName("allocateTimeboxes 동시 요청의 겹침 발생 또는 DB exclusion constraint 방어를 검증한다")
    void concurrentAllocateTimeboxesVerifiesOverlapConstraintBehavior() throws Exception {
        String userId = "timebox-overlap-" + UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        List<ExecutionUnit> executionUnits = saveExecutionUnits(userId, createdAt);
        OffsetDateTime firstStart = createdAt.plusDays(1).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime firstEnd = firstStart.plusHours(1);
        OffsetDateTime secondStart = firstStart.plusMinutes(30);
        OffsetDateTime secondEnd = secondStart.plusHours(1);
//        guardRepository.save(new TimeboxGuard(userId));

        TimeboxCommand firstCommand = command(
                executionUnits.get(0).getId(),
                firstStart,
                firstEnd
        );
        TimeboxCommand secondCommand = command(
                executionUnits.get(1).getId(),
                secondStart,
                secondEnd
        );

        CountDownLatch bothValidated = new CountDownLatch(2);
        CountDownLatch proceedToSave = new CountDownLatch(1);
        doAnswer(invocation -> {
            List<?> existingTimeboxes = invocation.getArgument(0);
            List<?> pendingTimeboxes = invocation.getArgument(1);
            assertThat(existingTimeboxes).isEmpty();
            assertThat(pendingTimeboxes).hasSize(1);

            invocation.callRealMethod();
            bothValidated.countDown();
            await(bothValidated);
            await(proceedToSave);
            return null;
        }).when(timeboxOverlapValidator).validate(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList()
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<?>> first = executor.submit(
                    allocateTimeboxes(userId, firstCommand)
            );
            Future<List<?>> second = executor.submit(
                    allocateTimeboxes(userId, secondCommand)
            );

            await(bothValidated);
            proceedToSave.countDown();

            Throwable firstFailure = failureOf(first);
            Throwable secondFailure = failureOf(second);
            long successCount = Stream.of(firstFailure, secondFailure)
                    .filter(Objects::isNull)
                    .count();
            List<Throwable> failures = Stream.of(firstFailure, secondFailure)
                    .filter(Objects::nonNull)
                    .toList();

            List<Timebox> savedTimeboxes = findSavedTimeboxes(userId);

            if (overlapConstraintExists()) {
                printConstraintProtection(
                        firstStart,
                        firstEnd,
                        secondStart,
                        secondEnd,
                        successCount,
                        failures.size(),
                        savedTimeboxes.size()
                );

                assertThat(successCount).isEqualTo(1);
                assertThat(failures)
                        .singleElement()
                        .isInstanceOf(DataIntegrityViolationException.class);
                assertThat(savedTimeboxes).hasSize(1);
            } else {
                printOverlapReproduction(
                        firstStart,
                        firstEnd,
                        secondStart,
                        secondEnd,
                        successCount,
                        savedTimeboxes.size()
                );

                assertThat(successCount).isEqualTo(2);
                assertThat(failures).isEmpty();
                assertThat(savedTimeboxes).hasSize(2);
                assertThat(overlaps(savedTimeboxes.get(0), savedTimeboxes.get(1))).isTrue();
            }
        } finally {
            proceedToSave.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Disabled("guard row 락 적용 전용 테스트")
    @DisplayName("guard row 락이 같은 사용자의 Timebox 배정을 직렬화한다")
    void guardRowLockSerializesConcurrentTimeboxAllocation() throws Exception {
        String userId = "timebox-overlap-" + UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        List<ExecutionUnit> executionUnits = saveExecutionUnits(userId, createdAt);
        OffsetDateTime firstStart = createdAt.plusDays(1).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime firstEnd = firstStart.plusHours(1);
        OffsetDateTime secondStart = firstStart.plusMinutes(30);
        OffsetDateTime secondEnd = secondStart.plusHours(1);
/*        TimeboxGuard timeboxGuard = new TimeboxGuard(userId);
        guardRepository.save(timeboxGuard);*/

        TimeboxCommand firstCommand = command(
                executionUnits.get(0).getId(),
                firstStart,
                firstEnd
        );
        TimeboxCommand secondCommand = command(
                executionUnits.get(1).getId(),
                secondStart,
                secondEnd
        );

        CountDownLatch firstAtValidation = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean blockFirstValidation = new AtomicBoolean(true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (blockFirstValidation.compareAndSet(true, false)) {
                firstAtValidation.countDown();
                await(releaseFirst);
            }
            return null;
        }).when(timeboxOverlapValidator).validate(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList()
        );


        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<?>> first = executor.submit(
                    allocateTimeboxes(userId, firstCommand)
            );
            await(firstAtValidation);

            Future<List<?>> second = executor.submit(
                    allocateTimeboxes(userId, secondCommand)
            );

            assertBlocked(second);
            releaseFirst.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).hasSize(1);
            Throwable secondFailure = failureOf(second);
            List<Timebox> savedTimeboxes = findSavedTimeboxes(userId);

            assertThat(secondFailure).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) secondFailure).getErrorCode())
                    .isEqualTo(ErrorCode.CONFLICT);
            assertThat(savedTimeboxes).hasSize(1);
            assertThat(savedTimeboxes.get(0).getStartAt()).isEqualTo(firstStart);
            assertThat(savedTimeboxes.get(0).getEndAt()).isEqualTo(firstEnd);

            log.info("""
                    
                    ========== Timebox Guard Row 방어 확인 ==========
                    1. 요청 A가 사용자 guard row의 PESSIMISTIC_WRITE 락을 획득했습니다.
                    2. 요청 A의 트랜잭션이 끝나기 전까지 요청 B는 같은 guard row에서 대기했습니다.
                    3. 요청 A가 Timebox를 저장하고 커밋한 뒤 요청 B가 락을 획득했습니다.
                    4. 요청 B는 요청 A가 저장한 겹치는 구간을 조회하여 CONFLICT로 거절됐습니다.
                    - 요청 A: {} ~ {}
                    - 요청 B: {} ~ {}
                    - 최종 저장 건수: {}
                    - 결과: 사용자별 배정 요청을 직렬화하여 최초 동시 INSERT 겹침을 방지함
                    =================================================
                    """,
                    firstStart,
                    firstEnd,
                    secondStart,
                    secondEnd,
                    savedTimeboxes.size()
            );
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("기존 겹침 행이 있으면 SELECT FOR UPDATE가 같은 행을 조회하는 트랜잭션을 대기시킨다")
    void overlappingRowForUpdateBlocksSecondTransactionUntilCommit() throws Exception {
        String userId = "timebox-row-lock-" + UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        ExecutionUnit executionUnit = saveExecutionUnits(userId, createdAt).get(0);
//        guardRepository.save(new TimeboxGuard(userId));
        OffsetDateTime existingStart = createdAt.plusDays(1).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime existingEnd = existingStart.plusHours(1);

        timeboxService.allocateTimeboxes(
                userId,
                List.of(command(executionUnit.getId(), existingStart, existingEnd))
        );

        OffsetDateTime newStart = existingStart.plusMinutes(15);
        OffsetDateTime newEnd = existingEnd.plusMinutes(15);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> first = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    List<Timebox> locked = timeboxRepository.findOverlappingForUpdate(
                            userId,
                            newStart,
                            newEnd
                    );
                    assertThat(locked).hasSize(1);
                    firstLocked.countDown();
                    await(releaseFirst);
                });
                return null;
            });

            await(firstLocked);

            Future<List<Timebox>> second = executor.submit(() ->
                    transactionTemplate.execute(status ->
                            timeboxRepository.findOverlappingForUpdate(
                                    userId,
                                    newStart,
                                    newEnd
                            )
                    )
            );

            assertBlocked(second);
            releaseFirst.countDown();

            first.get(5, TimeUnit.SECONDS);
            assertThat(second.get(5, TimeUnit.SECONDS)).hasSize(1);

            log.info("""
                    
                    ========== SELECT FOR UPDATE 동작 확인 ==========
                    1. 기존 PLANNED Timebox 1건이 요청 구간과 겹쳤습니다.
                    2. 트랜잭션 A가 findOverlappingForUpdate()로 해당 row lock을 획득했습니다.
                    3. 트랜잭션 B의 같은 조회는 A가 커밋할 때까지 완료되지 않았습니다.
                    4. A의 커밋 이후 B가 같은 row lock을 획득하고 조회를 완료했습니다.
                    - 의미: 겹치는 기존 행이 실제로 존재할 때 SELECT FOR UPDATE는 동작함
                    - 한계: 조회 결과가 0건이면 잠글 row가 없어 최초 동시 INSERT는 막지 못함
                    =================================================
                    """);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private List<ExecutionUnit> saveExecutionUnits(String userId, OffsetDateTime createdAt) {

        DailyBig3Board board = dailyBig3BoardRepository.save(
                DailyBig3Board.create(userId, LocalDate.now(), createdAt)
        );
        Big3Item firstItem = saveBig3Item(userId, "첫 번째 겹침 테스트 작업", createdAt);
        Big3Item secondItem = saveBig3Item(userId, "두 번째 겹침 테스트 작업", createdAt);

        board.getEntries().add(DailyBig3Entry.create(
                board,
                firstItem,
                1,
                SelectionSource.NEW,
                createdAt
        ));
        board.getEntries().add(DailyBig3Entry.create(
                board,
                secondItem,
                2,
                SelectionSource.NEW,
                createdAt
        ));
        dailyBig3BoardRepository.save(board);

        return executionUnitRepository.saveAll(List.of(
                ExecutionUnit.create(firstItem, "첫 번째 실행 단위", createdAt),
                ExecutionUnit.create(secondItem, "두 번째 실행 단위", createdAt)
        ));
    }

    private Big3Item saveBig3Item(String userId, String content, OffsetDateTime createdAt) {
        InboxItem inboxItem = inboxItemRepository.save(
                InboxItem.create(userId, content, createdAt)
        );
        return big3ItemRepository.save(
                Big3Item.create(userId, LocalDate.now(), inboxItem, createdAt)
        );
    }

    private TimeboxCommand command(
            String executionUnitId,
            OffsetDateTime startAt,
            OffsetDateTime endAt
    ) {
        return new TimeboxCommand(
                executionUnitId,
                startAt.toString(),
                endAt.toString(),
                true,
                "WORK"
        );
    }

    private Callable<List<?>> allocateTimeboxes(
            String userId,
            TimeboxCommand command
    ) {
        return () -> List.copyOf(timeboxService.allocateTimeboxes(userId, List.of(command)));
    }

    private boolean overlaps(Timebox left, Timebox right) {
        return left.getStartAt().isBefore(right.getEndAt())
                && left.getEndAt().isAfter(right.getStartAt());
    }

    private List<Timebox> findSavedTimeboxes(String userId) {
        return timeboxRepository.findAllByUserIdOrderByStartAtAsc(userId);
    }

    private boolean overlapConstraintExists() {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_constraint
                    WHERE conname = ?
                      AND conrelid = 'recovery_timeboxes'::regclass
                )
                """,
                Boolean.class,
                OVERLAP_CONSTRAINT
        );
        return Boolean.TRUE.equals(exists);
    }

    private void printOverlapReproduction(
            OffsetDateTime firstStart,
            OffsetDateTime firstEnd,
            OffsetDateTime secondStart,
            OffsetDateTime secondEnd,
            long successCount,
            int savedCount
    ) {
        log.info("""
                
                ========== Timebox 동시성 문제 재현 ==========
                1. 요청 A와 B가 각각 기존 PLANNED Timebox 0건을 조회했습니다.
                2. 두 요청 모두 애플리케이션 overlap 검증을 통과했습니다.
                3. 두 요청의 allocateTimeboxes()가 모두 성공했습니다.
                4. 최종 DB에는 같은 사용자의 겹치는 PLANNED Timebox가 2건 저장됐습니다.
                - 요청 A: {} ~ {}
                - 요청 B: {} ~ {}
                - 성공 요청 수: {}
                - 최종 저장 건수: {}
                - 도메인 불변식 위반: 같은 사용자의 활성 Timebox 시간 범위가 겹침
                - 원인: 일반 SELECT와 애플리케이션 검증은 아직 커밋되지 않은 상대 INSERT를 볼 수 없음
                =============================================
                """,
                firstStart,
                firstEnd,
                secondStart,
                secondEnd,
                successCount,
                savedCount
        );
    }

    private void printConstraintProtection(
            OffsetDateTime firstStart,
            OffsetDateTime firstEnd,
            OffsetDateTime secondStart,
            OffsetDateTime secondEnd,
            long successCount,
            int failureCount,
            int savedCount
    ) {
        log.info("""
                
                ========== GiST Exclusion Constraint 방어 확인 ==========
                1. 요청 A와 B가 각각 기존 PLANNED Timebox 0건을 조회했습니다.
                2. 두 요청 모두 애플리케이션 overlap 검증을 통과했습니다.
                3. DB가 tstzrange(start_at, end_at, '[)')의 && 겹침을 검사했습니다.
                4. 먼저 INSERT한 요청 1건만 커밋되고, 다른 요청은 exclusion constraint 위반으로 거절됐습니다.
                - 적용 constraint: {}
                - 요청 A: {} ~ {}
                - 요청 B: {} ~ {}
                - 성공 요청 수: {}
                - DB 충돌 요청 수: {}
                - 최종 저장 건수: {}
                - 최종 overlap: 0건
                - 결과: 애플리케이션 검증의 동시성 공백을 PostgreSQL GiST exclusion constraint가 방어함
                =========================================================
                """,
                OVERLAP_CONSTRAINT,
                firstStart,
                firstEnd,
                secondStart,
                secondEnd,
                successCount,
                failureCount,
                savedCount
        );
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

    private void assertBlocked(Future<?> future) throws Exception {
        try {
            future.get(300, TimeUnit.MILLISECONDS);
            throw new AssertionError("두 번째 요청이 row lock을 기다리지 않았습니다.");
        } catch (TimeoutException expected) {
            assertThat(future).isNotDone();
        }
    }
}
