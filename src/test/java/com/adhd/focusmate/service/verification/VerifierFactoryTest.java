package com.adhd.focusmate.service.verification;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * VerifierFactory 단위 테스트
 * Spring Context 없이 순수 Java로 테스트 (CI 환경에서 인프라 불필요)
 */
@DisplayName("VerifierFactory 단위 테스트")
class VerifierFactoryTest {

    private VerifierFactory verifierFactory;

    @BeforeEach
    void setUp() {
        // 실제 Verifier 구현체들을 직접 생성하여 Factory에 주입
        List<ChallengeVerifier> verifiers = List.of(
                new ManualVerifier(),
                new TimeVerifier(),
                new GitHubVerifier(null) // RestClient는 이 테스트에서 사용하지 않음
        );
        verifierFactory = new VerifierFactory(verifiers);
    }

    @Test
    @DisplayName("TIME_LOG 타입 요청 시 TimeVerifier 반환")
    void getVerifier_TIME_LOG_should_return_TimeVerifier() {
        ChallengeVerifier verifier = verifierFactory.getVerifier(ChallengeType.TIME_LOG);

        assertThat(verifier).isNotNull();
        assertThat(verifier).isInstanceOf(TimeVerifier.class);
    }

    @Test
    @DisplayName("MANUAL 타입 요청 시 ManualVerifier 반환")
    void getVerifier_MANUAL_should_return_ManualVerifier() {
        ChallengeVerifier verifier = verifierFactory.getVerifier(ChallengeType.MANUAL);

        assertThat(verifier).isNotNull();
        assertThat(verifier).isInstanceOf(ManualVerifier.class);
    }

    @Test
    @DisplayName("GITHUB_COMMIT 타입 요청 시 GitHubVerifier 반환")
    void getVerifier_GITHUB_COMMIT_should_return_GitHubVerifier() {
        ChallengeVerifier verifier = verifierFactory.getVerifier(ChallengeType.GITHUB_COMMIT);

        assertThat(verifier).isNotNull();
        assertThat(verifier).isInstanceOf(GitHubVerifier.class);
    }

    @Test
    @DisplayName("지원하지 않는 타입 요청 시 예외 발생")
    void getVerifier_unsupported_type_should_throw_exception() {
        assertThatThrownBy(() -> verifierFactory.getVerifier(ChallengeType.COMMUNITY_POST))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No verifier found");
    }

    @Test
    @DisplayName("모든 등록된 Verifier가 고유한 타입을 가짐")
    void all_verifiers_should_have_unique_types() {
        ChallengeVerifier manual = verifierFactory.getVerifier(ChallengeType.MANUAL);
        ChallengeVerifier time = verifierFactory.getVerifier(ChallengeType.TIME_LOG);
        ChallengeVerifier github = verifierFactory.getVerifier(ChallengeType.GITHUB_COMMIT);

        assertThat(manual).isNotSameAs(time);
        assertThat(time).isNotSameAs(github);
        assertThat(manual).isNotSameAs(github);
    }
}
