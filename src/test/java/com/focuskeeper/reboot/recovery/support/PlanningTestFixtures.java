package com.focuskeeper.reboot.recovery.support;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Selection;
import com.focuskeeper.reboot.recovery.planning.entity.Big3SelectionItem;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.repository.Big3SelectionItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.Big3SelectionRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class PlanningTestFixtures {

    private static final AtomicInteger SORT_ORDER = new AtomicInteger();

    private final InboxItemRepository inboxItemRepository;
    private final Big3SelectionRepository big3SelectionRepository;
    private final Big3SelectionItemRepository big3SelectionItemRepository;
    private final ExecutionUnitRepository executionUnitRepository;

    public PlanningTestFixtures(
            InboxItemRepository inboxItemRepository,
            Big3SelectionRepository big3SelectionRepository,
            Big3SelectionItemRepository big3SelectionItemRepository,
            ExecutionUnitRepository executionUnitRepository
    ) {
        this.inboxItemRepository = inboxItemRepository;
        this.big3SelectionRepository = big3SelectionRepository;
        this.big3SelectionItemRepository = big3SelectionItemRepository;
        this.executionUnitRepository = executionUnitRepository;
    }

    public static ExecutionUnit createTransientExecutionUnit(String userId, String title) {
        OffsetDateTime now = OffsetDateTime.now();
        InboxItem inboxItem = InboxItem.create(userId, title, now);
        Big3Selection selection = Big3Selection.create(userId, LocalDate.now(), now);
        Big3SelectionItem selectionItem = Big3SelectionItem.create(selection, inboxItem, 0);
        return ExecutionUnit.create(selectionItem, title, now);
    }

    public static ExecutionUnit saveExecutionUnit(
            String userId,
            String title,
            InboxItemRepository inboxItemRepository,
            Big3SelectionRepository big3SelectionRepository,
            Big3SelectionItemRepository big3SelectionItemRepository,
            ExecutionUnitRepository executionUnitRepository
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        InboxItem inboxItem = inboxItemRepository.save(InboxItem.create(userId, title, now));
        LocalDate selectedDate = LocalDate.now();
        Big3Selection selection = big3SelectionRepository.findByUserIdAndSelectedDate(userId, selectedDate)
                .orElseGet(() -> big3SelectionRepository.save(Big3Selection.create(userId, selectedDate, now)));
        Big3SelectionItem selectionItem = big3SelectionItemRepository.save(
                Big3SelectionItem.create(selection, inboxItem, SORT_ORDER.getAndIncrement())
        );
        return executionUnitRepository.save(ExecutionUnit.create(selectionItem, title, now));
    }

    public ExecutionUnit saveExecutionUnit(String userId, String title) {
        return saveExecutionUnit(
                userId,
                title,
                inboxItemRepository,
                big3SelectionRepository,
                big3SelectionItemRepository,
                executionUnitRepository
        );
    }
}
