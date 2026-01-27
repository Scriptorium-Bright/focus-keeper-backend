package com.adhd.focusmate.service.verification;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;

/**
 * 시간 기반 검증기 (미라클 모닝 등)
 * 사용자가 특정 시간대에 접속/완료 요청을 했는지 확인.
 */
@Slf4j
@Component
public class TimeVerifier implements ChallengeVerifier {

    // 미라클 모닝 기준: 오전 4시 ~ 7시 사이
    private static final LocalTime MORNING_START = LocalTime.of(4, 0);
    private static final LocalTime MORNING_END = LocalTime.of(7, 0);

    private final Clock clock;

    public TimeVerifier() {
        this.clock = Clock.systemDefaultZone();
    }

    // 테스트용 생성자
    public TimeVerifier(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean verify(Challenge challenge) {
        LocalTime now = LocalTime.now(clock);

        boolean success = now.isAfter(MORNING_START) && now.isBefore(MORNING_END);

        log.info("TimeVerifier: Challenge [{}] at {} - {}",
                challenge.getId(), now, success ? "SUCCESS" : "FAILED (outside window)");

        return success;
    }

    @Override
    public ChallengeType getSupportedType() {
        return ChallengeType.TIME_LOG;
    }
}
