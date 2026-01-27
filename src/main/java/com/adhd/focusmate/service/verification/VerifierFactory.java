package com.adhd.focusmate.service.verification;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ChallengeType에 맞는 Verifier를 찾아주는 팩토리
 * Spring이 모든 ChallengeVerifier 구현체를 주입하고,
 * getSupportedType()을 키로 맵에 저장.
 */
@Component
public class VerifierFactory {

    private final Map<ChallengeType, ChallengeVerifier> verifierMap;

    public VerifierFactory(List<ChallengeVerifier> verifiers) {
        this.verifierMap = verifiers.stream()
                .collect(Collectors.toMap(
                        ChallengeVerifier::getSupportedType,
                        Function.identity()));
    }

    /**
     * ChallengeType에 맞는 Verifier를 반환한다.
     * 
     * @param type 챌린지 타입
     * @return 해당 타입의 Verifier
     * @throws BusinessException 지원하지 않는 타입인 경우
     */
    public ChallengeVerifier getVerifier(ChallengeType type) {
        ChallengeVerifier verifier = verifierMap.get(type);
        if (verifier == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "No verifier found for challenge type: " + type);
        }
        return verifier;
    }
}
