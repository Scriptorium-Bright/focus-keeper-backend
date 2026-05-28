package com.focuskeeper.reboot.recovery.planning.service;

/**
 * 컨트롤러 요청을 서비스 내부 검증/생성 흐름에 넘길 때 사용하는 경량 command record다.
 *
 * 외부 API DTO와 달리 서비스가 실제로 필요한 필드만 남겨두어,
 * validation과 entity materialize 로직이 HTTP 계층에 덜 묶이게 한다.
 */

// Q. 다 좋은데 Service에 두는게 참 아쉽단말이지 ? 어떻게 생각하나
// A. 맞다. 이건 서비스 구현이 아니라 서비스 계층 입력 모델이라 `service` 직하가 아주 예쁘진 않다.
//    다만 HTTP Request DTO와 분리하려는 의도는 맞고, 지금 범위에서는 실용적으로 둘 수 있다.
//    구조를 더 다듬는다면 `planning.service.command`나 `planning.application.command` 쪽이 더 선명하다.

public record TimeboxCommand(
        String executionUnitId,
        String startAt,
        String endAt,
        boolean firstRecoveryBlock,
        String type
) {
}
