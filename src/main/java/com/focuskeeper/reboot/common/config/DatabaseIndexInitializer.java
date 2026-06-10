package com.focuskeeper.reboot.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void createSessionIndexes() {
        //status를 인덱스 컬럼에 안 넣는 이유는, 이미 WHERE status = 'STARTED'로 걸러진 row들만 unique 검사 대상이기 때문
        jdbcTemplate.execute("""
        CREATE UNIQUE INDEX IF NOT EXISTS uq_recovery_session_active
        ON recovery_session (user_id)
        WHERE status = 'STARTED';
        """);


    }
}
