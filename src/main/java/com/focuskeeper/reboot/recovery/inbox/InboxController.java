package com.focuskeeper.reboot.recovery.inbox;

import com.focuskeeper.reboot.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
                .map(SaveInboxItemsRequest.InboxItemPayload::content)
                .toList();
        List<InboxItemResponse> savedItems = inboxService.saveItems(request.userId(), contents);

        List<SavedInboxItem> responseItems = savedItems.stream()
                .map(item -> new SavedInboxItem(item.id(), item.content(), item.createdAt()))
                .toList();

        SaveInboxItemsResponse response = new SaveInboxItemsResponse(
                responseItems.size(),
                responseItems
        );
        return ApiResponse.success(response, "INBOX_ITEMS_SAVED");
    }

    public record SaveInboxItemsRequest(
            @NotBlank(message = "userId는 필수입니다.")
            String userId,
            @NotEmpty(message = "items는 최소 1개 이상이어야 합니다.")
            @Size(max = 20, message = "items는 최대 20개까지 허용됩니다.")
            List<@Valid InboxItemPayload> items
    ) {
        public record InboxItemPayload(
                @NotBlank(message = "content는 비어 있을 수 없습니다.")
                @Size(max = 200, message = "content는 최대 200자까지 허용됩니다.")
                String content
        ) {
        }
    }

    public record SaveInboxItemsResponse(
            int savedCount,
            List<SavedInboxItem> savedItems
    ) {
    }

    public record SavedInboxItem(
            String id,
            String content,
            String createdAt
    ) {
    }
}
