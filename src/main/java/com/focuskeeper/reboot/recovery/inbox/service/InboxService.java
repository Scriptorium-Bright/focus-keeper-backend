package com.focuskeeper.reboot.recovery.inbox.service;

import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

        List<InboxItem> inboxItemList = contents.stream()
                .map(content -> InboxItem.create(userId, content, OffsetDateTime.now()))
                .collect(Collectors.toList());

        List<InboxItem> saveList = inboxItemRepository.saveAll(inboxItemList);

        /*
        List<InboxItem> saveList = new ArrayList<>();
        for (InboxItem item : inboxItemList) {
            saveList.add(inboxItemRepository.save(item));
        }
        */
        return InboxItemResponse.from(saveList);
    }


/*    public List<InboxItemResponse> findItemsByIds(String userId, List<String> itemIds) {
        Map<String, InboxItemResponse> indexedItems = new LinkedHashMap<>();
        inboxItemRepository.findAllByUserIdAndIdIn(userId, itemIds).stream()
                .map(InboxItem::toResponse)
                .forEach(item -> indexedItems.put(item.id(), item));

        return itemIds.stream()
                .map(indexedItems::get)
                .filter(Objects::nonNull)
                .toList();
    }*/
}
