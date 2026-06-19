package com.focuskeeper.reboot.recovery.planning.constant;

/**
 * Big3Item의 lifecycle 상태다. DB에 저장된다.
 *
 * <p>화면에 표시하는 진행 상태(NOT_STARTED, IN_PROGRESS, COMPLETED)는
 * {@link Big3ItemCompletionStatus}로 매번 계산하며 DB에 저장하지 않는다.</p>
 */
public enum Big3ItemStatus {
    OPEN,
    COMPLETED,
    ABANDONED,
    EXPIRED
}
