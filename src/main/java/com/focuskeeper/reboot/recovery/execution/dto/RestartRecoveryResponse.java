package com.focuskeeper.reboot.recovery.execution.dto;

// Q. 참 애매함 이게, 용도에 따라 이름을 바꾸는게 맞지만 .. Result와 용도가 비슷함
// A. 외부 API 응답으로 나가는 타입이면 Response가 맞고, 서비스 내부 유스케이스 결과라면 Result가 맞다.
//    이 record는 dto 패키지에 있고 컨트롤러 응답 형태를 표현하므로 Response로 두는 게 자연스럽지만,
//    RestartService.RestartRecoveryResult와 필드가 거의 같아서 중복/경계가 애매해진 상태다.
public record RestartRecoveryResponse(
        RestartEventResponse restartEvent,
        RecoverySessionResponse recoverySession,
        RestartSuggestionResponse restartSuggestion
) {
}
