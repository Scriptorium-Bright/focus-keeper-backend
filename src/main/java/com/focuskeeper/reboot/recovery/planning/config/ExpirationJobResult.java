package com.focuskeeper.reboot.recovery.planning.config;

public class ExpirationJobResult {


    public static ExpirationJobResult skipped(String alreadyRunning) {
        return null;
    }

    public static ExpirationJobResult successed(int expireLastWeekTasks) {
        return null;
    }
}
