package com.focuskeeper.reboot.recovery.planning.dto;

import jakarta.validation.constraints.NotBlank;

//  start / end / type 받는거
public record TimeboxPayloadRequest(
        @NotBlank(message = "executionUnitId는 비어 있을 수 없습니다.")
        String executionUnitId,
        @NotBlank(message = "startAt은 비어 있을 수 없습니다.")
        String startAt,
        @NotBlank(message = "endAt은 비어 있을 수 없습니다.")
        String endAt,
        boolean firstRecoveryBlock,
        @NotBlank(message = "type은 비어 있을 수 없습니다.")
        String type
) {
}
