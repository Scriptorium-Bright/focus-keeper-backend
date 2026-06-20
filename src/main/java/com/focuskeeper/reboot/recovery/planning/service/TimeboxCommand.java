package com.focuskeeper.reboot.recovery.planning.service;

/**
 * 컨트롤러 요청을 서비스 내부 검증/생성 흐름에 넘길 때 사용하는 경량 command record다.
 *
 * 외부 API DTO와 달리 서비스가 실제로 필요한 필드만 남겨두어,
 * validation과 entity materialize 로직이 HTTP 계층에 덜 묶이게 한다.
 */



public record TimeboxCommand(
        String executionUnitId,
        String startAt,
        String endAt,
        boolean firstRecoveryBlock,
        String type
) {
}
