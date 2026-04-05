package com.focuskeeper.reboot.recovery.friction;

/**
 * friction signal과 failure-hour 패턴을 사용자 해석용 세그먼트로 묶을 때 사용하는 분류다.
 */
public enum FrictionSegmentType {
    MORNING_SLIP,
    OVERSIZED_TASK,
    LATE_RESTART
}
