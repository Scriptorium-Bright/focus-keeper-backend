package com.focuskeeper.reboot.recovery.friction.dto;

import java.util.List;

/**
 * 한 날짜의 friction segment 해석 결과 전체를 감싸는 응답 DTO다.
 */
public record FrictionSegmentReportResponse(
        String userId,
        String metricDate,
        List<FrictionSegmentResponse> segments
) {
}
