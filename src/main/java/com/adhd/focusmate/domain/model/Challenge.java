package com.adhd.focusmate.domain.model;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.domain.common.BaseEntity;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@Table(name = "challenge")
public class Challenge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_type", nullable = false)
    @Builder.Default
    private ChallengeType challengeType = ChallengeType.MANUAL;

    @Column(name = "energy_level")
    private Integer energyLevel;

    @Column(name = "estimated_time")
    private Integer estimatedTime;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private ChallengeStatus status = ChallengeStatus.PENDING;

    // ===== Domain Methods =====

    public void complete() {
        if (this.status == ChallengeStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_COMPLETED);
        }
        if (this.status == ChallengeStatus.FAILED) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_FINALIZED);
        }
        this.status = ChallengeStatus.COMPLETED;
    }

    public void fail() {
        if (this.status == ChallengeStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_COMPLETED);
        }
        if (this.status == ChallengeStatus.FAILED) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_FINALIZED);
        }
        this.status = ChallengeStatus.FAILED;
    }

    public void startVerification() {
        if (this.status != ChallengeStatus.PENDING && this.status != ChallengeStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_TASK_STATUS);
        }
        this.status = ChallengeStatus.PENDING_VERIFICATION;
    }
}
