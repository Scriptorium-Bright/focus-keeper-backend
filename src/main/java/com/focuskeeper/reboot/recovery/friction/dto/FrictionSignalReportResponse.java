package com.focuskeeper.reboot.recovery.friction.dto;

import java.util.List;

/**
 * 특정 날짜에 계산된 friction signal 목록 전체를 감싸는 응답 DTO다.
 */
public record FrictionSignalReportResponse(
        String userId,
        String metricDate,
        List<FrictionSignalResponse> signals
) {
}
