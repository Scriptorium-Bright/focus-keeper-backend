package com.focuskeeper.reboot.recovery.analytics.entity;

import com.focuskeeper.reboot.recovery.analytics.dto.CohortRetentionResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "cohort_retention_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cohort_retention_reports_cohort_date", columnNames = {"cohort_date"})
        }
)
public class CohortRetentionReport {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "cohort_date", nullable = false)
    private LocalDate cohortDate;

    @Column(name = "cohort_size", nullable = false)
    private int cohortSize;

    @Column(name = "retained_day_1_users", nullable = false)
    private int retainedDay1Users;

    @Column(name = "retained_day_7_users", nullable = false)
    private int retainedDay7Users;

    @Column(name = "retained_day_30_users", nullable = false)
    private int retainedDay30Users;

    @Column(name = "retention_day_1_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal retentionDay1Rate;

    @Column(name = "retention_day_7_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal retentionDay7Rate;

    @Column(name = "retention_day_30_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal retentionDay30Rate;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    protected CohortRetentionReport() {
    }

    private CohortRetentionReport(
            String id,
            LocalDate cohortDate,
            int cohortSize,
            int retainedDay1Users,
            int retainedDay7Users,
            int retainedDay30Users,
            BigDecimal retentionDay1Rate,
            BigDecimal retentionDay7Rate,
            BigDecimal retentionDay30Rate,
            OffsetDateTime generatedAt
    ) {
        this.id = id;
        this.cohortDate = cohortDate;
        this.cohortSize = cohortSize;
        this.retainedDay1Users = retainedDay1Users;
        this.retainedDay7Users = retainedDay7Users;
        this.retainedDay30Users = retainedDay30Users;
        this.retentionDay1Rate = retentionDay1Rate;
        this.retentionDay7Rate = retentionDay7Rate;
        this.retentionDay30Rate = retentionDay30Rate;
        this.generatedAt = generatedAt;
    }

    public static CohortRetentionReport create(
            LocalDate cohortDate,
            int cohortSize,
            int retainedDay1Users,
            int retainedDay7Users,
            int retainedDay30Users,
            BigDecimal retentionDay1Rate,
            BigDecimal retentionDay7Rate,
            BigDecimal retentionDay30Rate,
            OffsetDateTime generatedAt
    ) {
        return new CohortRetentionReport(
                UUID.randomUUID().toString(),
                cohortDate,
                cohortSize,
                retainedDay1Users,
                retainedDay7Users,
                retainedDay30Users,
                retentionDay1Rate,
                retentionDay7Rate,
                retentionDay30Rate,
                generatedAt
        );
    }

    public void regenerate(
            int cohortSize,
            int retainedDay1Users,
            int retainedDay7Users,
            int retainedDay30Users,
            BigDecimal retentionDay1Rate,
            BigDecimal retentionDay7Rate,
            BigDecimal retentionDay30Rate,
            OffsetDateTime generatedAt
    ) {
        this.cohortSize = cohortSize;
        this.retainedDay1Users = retainedDay1Users;
        this.retainedDay7Users = retainedDay7Users;
        this.retainedDay30Users = retainedDay30Users;
        this.retentionDay1Rate = retentionDay1Rate;
        this.retentionDay7Rate = retentionDay7Rate;
        this.retentionDay30Rate = retentionDay30Rate;
        this.generatedAt = generatedAt;
    }

    public CohortRetentionResponse toResponse() {
        return new CohortRetentionResponse(
                id,
                cohortDate.toString(),
                cohortSize,
                retainedDay1Users,
                retainedDay7Users,
                retainedDay30Users,
                retentionDay1Rate,
                retentionDay7Rate,
                retentionDay30Rate,
                generatedAt.toString()
        );
    }
}
