package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Entry;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 날짜별 Big3 배치 이력과 현재 활성 배치를 조회하는 저장소다.
 */
public interface DailyBig3EntryRepository extends JpaRepository<DailyBig3Entry, String> {

    @EntityGraph(attributePaths = {"big3Item", "big3Item.originInboxItem"})
    List<DailyBig3Entry> findAllByDailyBig3Board_IdAndRemovedAtIsNullOrderBySlotOrderAsc(
            String dailyBig3BoardId
    );
}
