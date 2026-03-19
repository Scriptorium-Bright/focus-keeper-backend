package com.focuskeeper.reboot.recovery.analytics.dto;

import java.math.BigDecimal;

public record CohortRetentionResponse(
        String cohortRetentionId,
        String cohortDate,
        int cohortSize,
        int retainedDay1Users,
        int retainedDay7Users,
        int retainedDay30Users,
        BigDecimal retentionDay1Rate,
        BigDecimal retentionDay7Rate,
        BigDecimal retentionDay30Rate,
        String generatedAt
) {
}
