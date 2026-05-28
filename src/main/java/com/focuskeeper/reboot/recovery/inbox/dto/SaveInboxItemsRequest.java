package com.focuskeeper.reboot.recovery.inbox.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

// 다중 brandump 내용 (근데 이름이 다른거에 대해서는 좀 수정이 ..)
public record SaveInboxItemsRequest (
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotEmpty(message = "items는 최소 1개 이상이어야 합니다.")
        @Size(max = 20, message = "items는 최대 20개까지 허용됩니다.")
        List<@Valid InboxItemPayloadRequest> items
) {
}
