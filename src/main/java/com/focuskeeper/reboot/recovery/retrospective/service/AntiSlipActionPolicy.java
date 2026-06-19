package com.focuskeeper.reboot.recovery.retrospective.service;

import com.focuskeeper.reboot.recovery.execution.constant.FailureReason;
import com.focuskeeper.reboot.recovery.retrospective.dto.AntiSlipActionResponse;
import org.springframework.stereotype.Component;

@Component
public class AntiSlipActionPolicy {

    public AntiSlipActionResponse suggest(
            long sessionCompletedCount,
            long sessionInterruptedCount,
            FailureReason dominantFailureReason
    ) {
        if (dominantFailureReason == null) {
            if (sessionInterruptedCount > sessionCompletedCount) {
                return new AntiSlipActionResponse(
                        "PROTECT_RECOVERY_BLOCK",
                        "복귀 블록 동안 인터럽트 차단하기",
                        "다음 주에는 첫 복귀 블록 1개를 회의/메신저 없는 보호 시간으로 먼저 확보하세요."
                );
            }
            return new AntiSlipActionResponse(
                    "KEEP_RESTART_SMALL",
                    "10분 재시작 기준 유지하기",
                    "다음 주에도 실패 직후 10분 안에 다시 붙잡을 수 있는 작은 시작 기준을 유지하세요."
            );
        }

        return switch (dominantFailureReason) {
            case TOO_BIG -> new AntiSlipActionResponse(
                    "SPLIT_FIRST_BLOCK",
                    "첫 복귀 블록을 25분 이하로 쪼개기",
                    "큰 일을 바로 끝내려 하지 말고, 다음 주 첫 복귀 블록은 25분 안에 끝낼 수 있는 단위로 나누세요."
            );
            case INTERRUPTION -> new AntiSlipActionResponse(
                    "PROTECT_RECOVERY_BLOCK",
                    "복귀 블록 동안 인터럽트 차단하기",
                    "인터럽트가 잦았다면 첫 복귀 블록 동안 메신저와 알림을 잠시 끄고 시작하세요."
            );
            case LOW_ENERGY -> new AntiSlipActionResponse(
                    "MOVE_FIRST_BLOCK_EARLIER",
                    "에너지 높은 시간대에 첫 블록 배치하기",
                    "에너지가 떨어지기 전에 가장 중요한 복귀 블록을 오전이나 회복 직후 시간대로 옮겨보세요."
            );
            case UNCLEAR_NEXT_ACTION -> new AntiSlipActionResponse(
                    "WRITE_NEXT_ACTION",
                    "다음 행동을 한 줄로 적어두기",
                    "하루를 끝내기 전에 다음에 할 행동을 동사 하나로 적어두면 다시 시작하기 쉬워집니다."
            );
            case CONTEXT_SWITCHED -> new AntiSlipActionResponse(
                    "GROUP_SAME_CONTEXT",
                    "같은 맥락의 일을 연속 배치하기",
                    "맥락 전환이 잦았다면 다음 주에는 비슷한 성격의 일을 붙여서 배치해 복귀 비용을 낮추세요."
            );
        };
    }
}
