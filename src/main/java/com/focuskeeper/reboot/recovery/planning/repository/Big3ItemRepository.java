package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.constant.Big3ItemStatus;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Big3 선택 항목을 기준으로 하위 실행 단위를 만들 때 사용하는 저장소다.
 */
public interface Big3ItemRepository extends JpaRepository<Big3Item, String> {

    // user가 소유한 big3Item 조회
    Optional<Big3Item> findByIdAndUserId(String id, String userId);

    List<Big3Item> findAllByIdInAndUserId(Collection<String> ids, String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        WITH targets AS (
            SELECT id
            FROM big3_items
            WHERE status = :openStatusStr
              AND week_start < :currentWeekStart
            ORDER BY week_start, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        UPDATE big3_items item
        SET status = :expiredStatusStr,
            expired_at = :now,
            updated_at = :now,
            version = version + 1
        FROM targets
        WHERE item.id = targets.id
          AND item.status = :openStatusStr
        """, nativeQuery = true)
    int expirePastOpenItem(
            @Param("now") OffsetDateTime now,
            @Param("openStatusStr") String openStatusStr,       // "OPEN" 문자열 전달
            @Param("expiredStatusStr") String expiredStatusStr,   // "EXPIRED" 문자열 전달
            @Param("currentWeekStart") LocalDate currentWeekStart,
            @Param("batchSize") int batchSize
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Big3Item b
                    set b.expiredAt = :now,
                        b.updatedAt = :now,
                        b.status = :expired,
                        b.version = b.version + 1
                        where b.status = :open and b.weekStart < :currentWeekStart
            """)
    int expirePastOpenItemWithoutChunk(
            OffsetDateTime now,
            Big3ItemStatus open,
            Big3ItemStatus expired,
            LocalDate currentWeekStart
    );




    // user,week/originInboxItem 기준 big3Item 조회
    @EntityGraph(attributePaths = "originInboxItem")
    List<Big3Item> findAllByUserIdAndWeekStartAndOriginInboxItem_IdIn(
            String userId,
            LocalDate weekStart,
            Collection<String> inboxItemIds
    );

    long countById(String id);

    @Query("""
    select b
    from Big3Item b
    where b.userId = :userId AND b.status IN :statuses AND b.weekStart < :weekStart
        """)
    List<Big3Item> findPastUnfinishedItems(
            String userId,
            Collection<Big3ItemStatus> statuses,
            LocalDate weekStart
    );


    List<Big3Item> findAllByStatusAndWeekStartBefore(
            Big3ItemStatus status,
            LocalDate currentWeekStart
    );

    boolean existsByDerivedFromItem_Id(String derivedFromItemId);

}
