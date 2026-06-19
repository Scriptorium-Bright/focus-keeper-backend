package com.focuskeeper.reboot.recovery.retrospective.service;

import com.focuskeeper.reboot.recovery.execution.constant.FailureReason;
import org.springframework.stereotype.Component;

@Component
public class RetrospectiveSummaryPolicy {

    public String summarize(
            long sessionStartedCount,
            long sessionCompletedCount,
            long sessionInterruptedCount,
            long failureCount,
            long restartCount,
            FailureReason dominantFailureReason
    ) {
        if (failureCount == 0) {
            return "이번 주에는 실패 체크인 없이 복귀 블록을 비교적 안정적으로 유지했다.";
        }
        if (sessionInterruptedCount > sessionCompletedCount) {
            return "시작한 복귀 블록보다 중단된 세션이 많아, 복귀를 시작한 뒤 유지하는 힘이 약했다.";
        }
        if (restartCount < failureCount) {
            return "실패 이후 재시작으로 이어지는 비율이 낮아 다음날까지 끌리는 패턴이 보였다.";
        }
        if (dominantFailureReason == null) {
            return "실패는 있었지만 특정 사유 하나로 집중되지는 않아, 주간 맥락 점검이 더 필요하다.";
        }
        return switch (dominantFailureReason) {
            case TOO_BIG -> "이번 주에는 일이 너무 크게 느껴져 첫 복귀 블록 진입 장벽이 높았다.";
            case INTERRUPTION -> "이번 주에는 외부 인터럽트가 복귀 블록을 끊는 핵심 요인이었다.";
            case LOW_ENERGY -> "이번 주에는 낮은 에너지 상태가 복귀 유지력 저하로 이어졌다.";
            case UNCLEAR_NEXT_ACTION -> "이번 주에는 다음 행동이 불명확해 복귀 직후 다시 멈추는 패턴이 보였다.";
            case CONTEXT_SWITCHED -> "이번 주에는 맥락 전환이 잦아 원래 하던 일로 다시 붙잡기 어려웠다.";
        };
    }
}
