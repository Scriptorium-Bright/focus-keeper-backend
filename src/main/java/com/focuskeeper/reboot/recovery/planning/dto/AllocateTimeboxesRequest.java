package com.focuskeeper.reboot.recovery.planning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

// timebox들을 받는거같음 아마
public record AllocateTimeboxesRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotEmpty(message = "timeboxes는 최소 1개 이상이어야 합니다.")
        @Size(max = 3, message = "timeboxes는 최대 3개까지 허용됩니다.")
        List<@Valid TimeboxPayloadRequest> timeboxes
) {
}
