package com.adhd.focusmate.repository;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChallengeRepository extends JpaRepository<Challenge, Long> {
    List<Challenge> findAllByUserIdAndStatus(Long userId, ChallengeStatus status);

    List<Challenge> findAllByUserId(Long userId);
}
