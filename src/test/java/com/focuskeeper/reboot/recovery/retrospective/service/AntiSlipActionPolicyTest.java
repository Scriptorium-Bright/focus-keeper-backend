package com.focuskeeper.reboot.recovery.retrospective.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.focuskeeper.reboot.recovery.execution.FailureReason;
import org.junit.jupiter.api.Test;

class AntiSlipActionPolicyTest {

    private final AntiSlipActionPolicy antiSlipActionPolicy = new AntiSlipActionPolicy();

    @Test
    void suggestReturnsSplitFirstBlockWhenDominantReasonIsTooBig() {
        var action = antiSlipActionPolicy.suggest(1, 1, FailureReason.TOO_BIG);

        assertThat(action.actionCode()).isEqualTo("SPLIT_FIRST_BLOCK");
        assertThat(action.title()).isEqualTo("첫 복귀 블록을 25분 이하로 쪼개기");
    }

    @Test
    void suggestReturnsProtectRecoveryBlockWhenInterruptionsOutnumberCompletionsWithoutDominantReason() {
        var action = antiSlipActionPolicy.suggest(1, 3, null);

        assertThat(action.actionCode()).isEqualTo("PROTECT_RECOVERY_BLOCK");
        assertThat(action.description()).contains("보호 시간");
    }

    @Test
    void suggestReturnsKeepRestartSmallAsDefault() {
        var action = antiSlipActionPolicy.suggest(3, 1, null);

        assertThat(action.actionCode()).isEqualTo("KEEP_RESTART_SMALL");
        assertThat(action.title()).isEqualTo("10분 재시작 기준 유지하기");
    }
}
