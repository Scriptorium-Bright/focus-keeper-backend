package com.focuskeeper.reboot.recovery.retrospective.dto;

/**
 * 주간 회고(Retrospective) 결과를 API 응답으로 전달하는 DTO다.
 *
 * 한 주간의 세션/실패/재시작 통계뿐만 아니라, 가장 지배적인 실패 이유(dominantFailureReason),
 * 이를 바탕으로 생성된 주간 요약(summary), 그리고 다음 주를 위한 구체적인 행동 처방(antiSlipAction)을 포함한다.
 */
public record WeeklyRetrospectiveResponse(
        String retrospectiveId,
        String weekStart,
        String weekEnd,
        long sessionStartedCount,
        long sessionCompletedCount,
        long sessionInterruptedCount,
        long failureCount,
        long restartCount,
        String dominantFailureReason,
        String summary,
        AntiSlipActionResponse antiSlipAction,
        String generatedAt
) {
}
