package com.focuskeeper.reboot.recovery.inbox.service;

import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InboxService {

    private final InboxItemRepository inboxItemRepository;

    public InboxService(InboxItemRepository inboxItemRepository) {
        this.inboxItemRepository = inboxItemRepository;
    }

    /**
     *
     * @param userId
     * @param contents
     * @return 사용자가 Brain Dump 한 모든 요소들을 저장한다.
     */

    @Transactional
    public List<InboxItemResponse> saveItems(String userId, List<String> contents) {
        return contents.stream()
                .map(content -> InboxItem.create(userId, content, OffsetDateTime.now()))
                .map(inboxItemRepository::save)
                .map(InboxItem::toResponse)
                .toList();
    }


    public List<InboxItemResponse> findItemsByIds(String userId, List<String> itemIds) {
        Map<String, InboxItemResponse> indexedItems = new LinkedHashMap<>();
        inboxItemRepository.findAllByUserIdAndIdIn(userId, itemIds).stream()
                .map(InboxItem::toResponse)
                .forEach(item -> indexedItems.put(item.id(), item));

        return itemIds.stream()
                .map(indexedItems::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
