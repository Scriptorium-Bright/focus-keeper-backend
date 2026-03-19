package com.focuskeeper.reboot.recovery.inbox.repository;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxItemRepository extends JpaRepository<InboxItem, String> {

    List<InboxItem> findAllByUserIdAndIdIn(String userId, Collection<String> ids);

    @Query("""
            select count(distinct i.userId)
            from InboxItem i
            where i.createdAt >= :start
              and i.createdAt < :end
            """)
    long countDistinctUsersCreatedBetween(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );
}
