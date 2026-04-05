package com.focuskeeper.reboot.recovery.friction.dto;

/**
 * 분석 결과를 사람이 이해하기 쉬운 friction segment 한 건으로 풀어낸 응답 DTO다.
 */
public record FrictionSegmentResponse(
        String segmentType,
        String title,
        String summary,
        String evidence
) {
}
