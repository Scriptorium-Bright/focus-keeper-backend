package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.dto.RestartSuggestionResponse;
import org.springframework.stereotype.Component;

@Component
public class RestartSuggestionPolicy {

    private static final int TEN_MINUTE_SUGGESTED_MINUTES = 10;

    public RestartSuggestionResponse suggest(FailureReason reason) {
        return new RestartSuggestionResponse(
                RestartType.TEN_MINUTE_RESTART.name(),
                TEN_MINUTE_SUGGESTED_MINUTES,
                switch (reason) {
                    case TOO_BIG -> "전체를 끝내려 하지 말고 10분만 다시 붙잡아보세요.";
                    case INTERRUPTION -> "끊긴 흐름을 10분만 다시 이어보세요.";
                    case LOW_ENERGY -> "부담을 낮춰 10분만 다시 시작해보세요.";
                    case UNCLEAR_NEXT_ACTION -> "다음 행동 하나만 정해서 10분만 다시 시작해보세요.";
                    case CONTEXT_SWITCHED -> "바뀐 맥락을 다시 잡기 위해 10분만 원래 일로 복귀해보세요.";
                }
        );
    }
}
