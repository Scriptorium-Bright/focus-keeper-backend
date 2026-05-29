package com.focuskeeper.reboot.recovery.inbox.dto;


import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record InboxItemResponse (
        String id,
        String content,
        String createdAt
) {

    public static List<InboxItemResponse> from(List<InboxItem> inboxItemList) {
        return inboxItemList.stream()
                .map(inboxItem -> new InboxItemResponse(inboxItem.getId(), inboxItem.getContent(), inboxItem.getCreatedAt().toString()))
                .collect(Collectors.toList());
    }
}
