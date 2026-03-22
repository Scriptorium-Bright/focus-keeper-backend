package com.focuskeeper.reboot.common.observability.dto;

import java.util.List;

public record RunbookScenarioResponse(
        String scenarioKey,
        String title,
        String trigger,
        List<String> steps,
        List<String> verificationSignals
) {
}
