package com.focuskeeper.reboot.recovery.inbox.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveInboxItemsRequest (
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotEmpty(message = "items는 최소 1개 이상이어야 합니다.")
        @Size(max = 20, message = "items는 최대 20개까지 허용됩니다.")
        List<@Valid InboxItemPayloadRequest> items
) {
}
