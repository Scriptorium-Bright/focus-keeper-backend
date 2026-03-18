package com.focuskeeper.reboot.recovery.inbox;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InboxService {

    private final InboxItemRepository inboxItemRepository;

    public InboxService(InboxItemRepository inboxItemRepository) {
        this.inboxItemRepository = inboxItemRepository;
    }

    @Transactional
    public List<InboxItem> saveItems(String userId, List<String> contents) {
        return contents.stream()
                .map(content -> InboxItemEntity.create(userId, content, OffsetDateTime.now()))
                .map(inboxItemRepository::save)
                .map(InboxItemEntity::toDomain)
                .toList();
    }

    public List<InboxItem> findItemsByIds(String userId, List<String> itemIds) {
        Map<String, InboxItem> indexedItems = new LinkedHashMap<>();
        inboxItemRepository.findAllByUserIdAndIdIn(userId, itemIds).stream()
                .map(InboxItemEntity::toDomain)
                .forEach(item -> indexedItems.put(item.id(), item));

        return itemIds.stream()
                .map(indexedItems::get)
                .filter(item -> item != null)
                .toList();
    }
}
