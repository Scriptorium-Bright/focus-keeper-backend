package com.focuskeeper.reboot.recovery.planning.service;

public record TimeboxCommand(
        String itemId,
        String startAt,
        String endAt,
        boolean firstRecoveryBlock,
        String type
) {
}
