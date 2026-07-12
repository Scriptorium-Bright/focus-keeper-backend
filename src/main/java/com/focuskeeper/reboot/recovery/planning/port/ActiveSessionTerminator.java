package com.focuskeeper.reboot.recovery.planning.port;

import java.time.OffsetDateTime;

/**
 * 계획된 실행 단위가 완료될 때 연결된 활성 실행 시도를 종료하기 위한 도메인 경계다.
 */
public interface ActiveSessionTerminator {

    void completeIfActive(String timeboxId, String userId, OffsetDateTime completedAt);
}
