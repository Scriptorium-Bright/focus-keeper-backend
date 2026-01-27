package com.adhd.focusmate.service.verification;

import com.adhd.focusmate.domain.model.type.ChallengeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("VerifierFactory 통합 테스트")
class VerifierFactoryTest {

    @Autowired
    private VerifierFactory verifierFactory;

    @Test
    @DisplayName("TIME_LOG 타입 요청 시 TimeVerifier 반환")
    void getVerifier_TIME_LOG_should_return_TimeVerifier() {
        // When
        ChallengeVerifier verifier = verifierFactory.getVerifier(ChallengeType.TIME_LOG);

        // Then
        assertThat(verifier).isNotNull();
        assertThat(verifier).isInstanceOf(TimeVerifier.class);
    }

    @Test
    @DisplayName("MANUAL 타입 요청 시 ManualVerifier 반환")
    void getVerifier_MANUAL_should_return_ManualVerifier() {
        // When
        ChallengeVerifier verifier = verifierFactory.getVerifier(ChallengeType.MANUAL);

        // Then
        assertThat(verifier).isNotNull();
        assertThat(verifier).isInstanceOf(ManualVerifier.class);
    }

    @Test
    @DisplayName("GITHUB_COMMIT 타입 요청 시 GitHubVerifier 반환")
    void getVerifier_GITHUB_COMMIT_should_return_GitHubVerifier() {
        // When
        ChallengeVerifier verifier = verifierFactory.getVerifier(ChallengeType.GITHUB_COMMIT);

        // Then
        assertThat(verifier).isNotNull();
        assertThat(verifier).isInstanceOf(GitHubVerifier.class);
    }

    @Test
    @DisplayName("지원하지 않는 타입 요청 시 예외 발생")
    void getVerifier_unsupported_type_should_throw_exception() {
        // COMMUNITY_POST는 아직 Verifier가 없음
        assertThatThrownBy(() -> verifierFactory.getVerifier(ChallengeType.COMMUNITY_POST))
                .hasMessageContaining("No verifier found");
    }

    @Test
    @DisplayName("모든 등록된 Verifier가 고유한 타입을 가짐")
    void all_verifiers_should_have_unique_types() {
        // 각 타입별로 다른 Verifier 인스턴스가 반환되어야 함
        ChallengeVerifier manual = verifierFactory.getVerifier(ChallengeType.MANUAL);
        ChallengeVerifier time = verifierFactory.getVerifier(ChallengeType.TIME_LOG);
        ChallengeVerifier github = verifierFactory.getVerifier(ChallengeType.GITHUB_COMMIT);

        assertThat(manual).isNotSameAs(time);
        assertThat(time).isNotSameAs(github);
        assertThat(manual).isNotSameAs(github);
    }
}
