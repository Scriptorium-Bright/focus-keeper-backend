package com.focuskeeper.reboot.recovery.inbox.controller;

import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemPayloadRequest;
import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.inbox.dto.SaveInboxItemsRequest;
import com.focuskeeper.reboot.recovery.inbox.dto.SaveInboxItemsResponse;
import com.focuskeeper.reboot.recovery.inbox.dto.SavedInboxItemResponse;
import com.focuskeeper.reboot.recovery.inbox.service.InboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recovery")
@Tag(name = "Recovery", description = "Recovery loop planning and execution APIs")
public class InboxController {

    private final InboxService inboxService;

    public InboxController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @PostMapping("/inbox-items")
    @Operation(summary = "Save Brain Dump inbox items", description = "Stores raw task ideas that will later be narrowed down into today's Big3.")
    public ApiResponse<SaveInboxItemsResponse> saveInboxItems(
            @Valid @RequestBody SaveInboxItemsRequest request
    ) {
        List<String> contents = request.items().stream()
                .map(InboxItemPayloadRequest::content)
                .toList();
        List<InboxItemResponse> savedItems = inboxService.saveItems(request.userId(), contents);

        List<SavedInboxItemResponse> responseItems = savedItems.stream()
                .map(item -> new SavedInboxItemResponse(item.id(), item.content(), item.createdAt()))
                .toList();

        SaveInboxItemsResponse response = new SaveInboxItemsResponse(
                responseItems.size(),
                responseItems
        );
        return ApiResponse.success(response, "INBOX_ITEMS_SAVED");
    }
}
