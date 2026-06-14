package com.focuskeeper.reboot.recovery.support;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.constant.SelectionSource;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Board;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Entry;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.DailyBig3BoardRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlanningTestFixtures {

    private final InboxItemRepository inboxItemRepository;
    private final DailyBig3BoardRepository dailyBig3BoardRepository;
    private final Big3ItemRepository big3ItemRepository;
    private final ExecutionUnitRepository executionUnitRepository;

    public PlanningTestFixtures(
            InboxItemRepository inboxItemRepository,
            DailyBig3BoardRepository dailyBig3BoardRepository,
            Big3ItemRepository big3ItemRepository,
            ExecutionUnitRepository executionUnitRepository
    ) {
        this.inboxItemRepository = inboxItemRepository;
        this.dailyBig3BoardRepository = dailyBig3BoardRepository;
        this.big3ItemRepository = big3ItemRepository;
        this.executionUnitRepository = executionUnitRepository;
    }

    public static ExecutionUnit createTransientExecutionUnit(String userId, String title) {
        OffsetDateTime now = OffsetDateTime.now();
        InboxItem inboxItem = InboxItem.create(userId, title, now);
        Big3Item big3Item = Big3Item.create(userId, LocalDate.now(), inboxItem, now);
        return ExecutionUnit.create(big3Item, title, now);
    }

    public static ExecutionUnit saveExecutionUnit(
            String userId,
            String title,
            InboxItemRepository inboxItemRepository,
            DailyBig3BoardRepository dailyBig3BoardRepository,
            Big3ItemRepository big3ItemRepository,
            ExecutionUnitRepository executionUnitRepository
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        InboxItem inboxItem = inboxItemRepository.save(InboxItem.create(userId, title, now));
        LocalDate selectedDate = LocalDate.now();
        DailyBig3Board dailyBig3Board = dailyBig3BoardRepository.findByUserIdAndSelectedDate(userId, selectedDate)
                .orElseGet(() -> dailyBig3BoardRepository.save(DailyBig3Board.create(userId, selectedDate, now)));
        Big3Item big3Item = big3ItemRepository.save(Big3Item.create(userId, selectedDate, inboxItem, now));
        dailyBig3Board.getEntries().add(DailyBig3Entry.create(
                dailyBig3Board,
                big3Item,
                1,
                SelectionSource.NEW,
                now
        ));
        dailyBig3BoardRepository.save(dailyBig3Board);
        return executionUnitRepository.save(ExecutionUnit.create(big3Item, title, now));
    }

    @Transactional
    public ExecutionUnit saveExecutionUnit(String userId, String title) {
        return saveExecutionUnit(
                userId,
                title,
                inboxItemRepository,
                dailyBig3BoardRepository,
                big3ItemRepository,
                executionUnitRepository
        );
    }
}
