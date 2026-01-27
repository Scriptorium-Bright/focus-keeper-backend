package com.adhd.focusmate.domain.model.type;

/**
 * 아이템 유형 - 범용 아이템 시스템
 * 특정 챌린지 타입에 종속되지 않음
 */
public enum ItemType {
    PASS_TICKET, // 면제권 - 실패 시 예치금 방어
    DOUBLE_POINT, // 더블 포인트 - 성공 시 포인트 2배 (향후)
    EXTEND_DEADLINE // 마감 연장 - 마감 시간 연장 (향후)
}
