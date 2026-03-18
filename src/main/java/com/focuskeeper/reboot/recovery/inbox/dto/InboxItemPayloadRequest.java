package com.focuskeeper.reboot.recovery.inbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InboxItemPayloadRequest(
        @NotBlank(message = "content는 비어 있을 수 없습니다.")
        @Size(max = 200, message = "content는 최대 200자까지 허용됩니다.")
        String content
) {
}
