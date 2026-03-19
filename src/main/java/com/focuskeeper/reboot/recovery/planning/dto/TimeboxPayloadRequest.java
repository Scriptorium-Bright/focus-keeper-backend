package com.focuskeeper.reboot.recovery.planning.dto;

import jakarta.validation.constraints.NotBlank;

public record TimeboxPayloadRequest(
        @NotBlank(message = "itemId는 비어 있을 수 없습니다.")
        String itemId,
        @NotBlank(message = "startAt은 비어 있을 수 없습니다.")
        String startAt,
        @NotBlank(message = "endAt은 비어 있을 수 없습니다.")
        String endAt,
        boolean firstRecoveryBlock,
        @NotBlank(message = "type은 비어 있을 수 없습니다.")
        String type
) {
}
