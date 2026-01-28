package com.adhd.focusmate.repository;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    List<Challenge> findAllByUserIdAndStatus(Long userId, ChallengeStatus status);

    List<Challenge> findAllByUserId(Long userId);

    /**
     * 특정 상태들에 해당하는 모든 챌린지 조회
     */
    List<Challenge> findAllByStatusIn(List<ChallengeStatus> statuses);

    /**
     * 마감 기한이 지났고 아직 정산되지 않은 챌린지 조회 (스케줄러용)
     */
    @Query("SELECT c FROM Challenge c WHERE c.deadline <= :now AND c.status IN :statuses")
    List<Challenge> findAllPendingSettlement(
            @Param("now") LocalDateTime now,
            @Param("statuses") List<ChallengeStatus> statuses);
}
