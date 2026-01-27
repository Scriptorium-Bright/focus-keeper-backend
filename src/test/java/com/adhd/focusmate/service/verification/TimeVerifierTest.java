package com.adhd.focusmate.service.verification;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimeVerifier 테스트")
class TimeVerifierTest {

    private TimeVerifier createVerifierWithTime(int hour, int minute) {
        // 2024-01-01 HH:MM:00 KST로 고정된 시간
        String timeStr = String.format("2024-01-01T%02d:%02d:00+09:00", hour, minute);
        Instant instant = Instant.parse(timeStr.replace("+09:00", "Z"))
                .minusSeconds(9 * 3600); // KST offset 보정
        Clock fixedClock = Clock.fixed(
                Instant.parse(String.format("2024-01-01T%02d:%02d:00Z", hour - 9 < 0 ? hour + 15 : hour - 9, minute)),
                ZoneId.of("Asia/Seoul"));
        return new TimeVerifier(fixedClock);
    }

    private Challenge createTestChallenge() {
        return Challenge.builder()
                .id(1L)
                .title("미라클 모닝")
                .challengeType(ChallengeType.TIME_LOG)
                .status(ChallengeStatus.PENDING)
                .build();
    }

    @Nested
    @DisplayName("성공 시나리오")
    class SuccessScenarios {

        @Test
        @DisplayName("04:30에 검증하면 성공")
        void verify_at_0430_should_succeed() {
            // Given: 04:30 (미라클 모닝 시간대)
            Clock fixedClock = Clock.fixed(
                    Instant.parse("2024-01-01T04:30:00Z"),
                    ZoneId.of("UTC"));
            TimeVerifier verifier = new TimeVerifier(fixedClock);
            Challenge challenge = createTestChallenge();

            // When
            boolean result = verifier.verify(challenge);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("06:00에 검증하면 성공")
        void verify_at_0600_should_succeed() {
            Clock fixedClock = Clock.fixed(
                    Instant.parse("2024-01-01T06:00:00Z"),
                    ZoneId.of("UTC"));
            TimeVerifier verifier = new TimeVerifier(fixedClock);
            Challenge challenge = createTestChallenge();

            boolean result = verifier.verify(challenge);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("실패 시나리오")
    class FailureScenarios {

        @Test
        @DisplayName("03:59에 검증하면 실패 (시간대 이전)")
        void verify_at_0359_should_fail_too_early() {
            Clock fixedClock = Clock.fixed(
                    Instant.parse("2024-01-01T03:59:00Z"),
                    ZoneId.of("UTC"));
            TimeVerifier verifier = new TimeVerifier(fixedClock);
            Challenge challenge = createTestChallenge();

            boolean result = verifier.verify(challenge);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("07:01에 검증하면 실패 (시간대 이후)")
        void verify_at_0701_should_fail_too_late() {
            Clock fixedClock = Clock.fixed(
                    Instant.parse("2024-01-01T07:01:00Z"),
                    ZoneId.of("UTC"));
            TimeVerifier verifier = new TimeVerifier(fixedClock);
            Challenge challenge = createTestChallenge();

            boolean result = verifier.verify(challenge);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("12:00에 검증하면 실패 (낮 시간)")
        void verify_at_noon_should_fail() {
            Clock fixedClock = Clock.fixed(
                    Instant.parse("2024-01-01T12:00:00Z"),
                    ZoneId.of("UTC"));
            TimeVerifier verifier = new TimeVerifier(fixedClock);
            Challenge challenge = createTestChallenge();

            boolean result = verifier.verify(challenge);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("경계값 테스트")
    class BoundaryTests {

        @Test
        @DisplayName("정확히 04:00에는 실패 (isAfter 사용)")
        void verify_at_exactly_0400_should_fail() {
            Clock fixedClock = Clock.fixed(
                    Instant.parse("2024-01-01T04:00:00Z"),
                    ZoneId.of("UTC"));
            TimeVerifier verifier = new TimeVerifier(fixedClock);
            Challenge challenge = createTestChallenge();

            boolean result = verifier.verify(challenge);

            // isAfter(04:00)이므로 정확히 04:00은 false
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("정확히 07:00에는 실패 (isBefore 사용)")
        void verify_at_exactly_0700_should_fail() {
            Clock fixedClock = Clock.fixed(
                    Instant.parse("2024-01-01T07:00:00Z"),
                    ZoneId.of("UTC"));
            TimeVerifier verifier = new TimeVerifier(fixedClock);
            Challenge challenge = createTestChallenge();

            boolean result = verifier.verify(challenge);

            // isBefore(07:00)이므로 정확히 07:00은 false
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("04:00:01에는 성공")
        void verify_at_040001_should_succeed() {
            Clock fixedClock = Clock.fixed(
                    Instant.parse("2024-01-01T04:00:01Z"),
                    ZoneId.of("UTC"));
            TimeVerifier verifier = new TimeVerifier(fixedClock);
            Challenge challenge = createTestChallenge();

            boolean result = verifier.verify(challenge);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("06:59:59에는 성공")
        void verify_at_065959_should_succeed() {
            Clock fixedClock = Clock.fixed(
                    Instant.parse("2024-01-01T06:59:59Z"),
                    ZoneId.of("UTC"));
            TimeVerifier verifier = new TimeVerifier(fixedClock);
            Challenge challenge = createTestChallenge();

            boolean result = verifier.verify(challenge);

            assertThat(result).isTrue();
        }
    }

    @Test
    @DisplayName("getSupportedType은 TIME_LOG를 반환")
    void getSupportedType_should_return_TIME_LOG() {
        TimeVerifier verifier = new TimeVerifier();

        assertThat(verifier.getSupportedType()).isEqualTo(ChallengeType.TIME_LOG);
    }
}
