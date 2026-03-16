package com.focuskeeper.reboot.recovery.planning;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.InboxService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class Big3Service {

    private final InboxService inboxService;
    private final Map<String, Big3Selection> todayBig3Store = new ConcurrentHashMap<>();

    public Big3Service(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    public Big3Selection selectTodayBig3(String userId, List<String> itemIds) {
        List<String> uniqueItemIds = deduplicate(itemIds);
        if (uniqueItemIds.size() != itemIds.size()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("itemIds", "중복된 itemId는 허용되지 않습니다.")
            );
        }

        List<InboxItem> selectedItems = inboxService.findItemsByIds(userId, uniqueItemIds);
        if (selectedItems.size() != uniqueItemIds.size()) {
            Set<String> selectedItemIds = selectedItems.stream()
                    .map(InboxItem::id)
                    .collect(HashSet::new, Set::add, Set::addAll);
            List<String> missingItemIds = uniqueItemIds.stream()
                    .filter(id -> !selectedItemIds.contains(id))
                    .toList();

            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    Map.of("missingItemIds", missingItemIds)
            );
        }

        Big3Selection selection = new Big3Selection(
                userId,
                LocalDate.now(),
                OffsetDateTime.now(),
                selectedItems
        );
        todayBig3Store.put(userId, selection);
        return selection;
    }

    public Big3Selection getTodayBig3OrThrow(String userId) {
        Big3Selection selection = todayBig3Store.get(userId);
        if (selection == null) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    Map.of(
                            "userId", userId,
                            "selectedDate", LocalDate.now().toString()
                    )
            );
        }
        return selection;
    }

    private List<String> deduplicate(List<String> itemIds) {
        return new ArrayList<>(new LinkedHashSet<>(itemIds));
    }
}
