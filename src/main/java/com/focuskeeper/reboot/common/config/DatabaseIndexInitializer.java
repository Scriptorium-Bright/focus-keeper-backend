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

        // entry 중복 삽입 방지를 위한 partial unique index
        jdbcTemplate.execute("""
        CREATE UNIQUE INDEX IF NOT EXISTS uq_daily_big3_entry_order
        ON daily_big3_entries (daily_big3_board_id, slot_order)
        WHERE removed_at is NULL
        """);

        // 동일 작업 중복 등록 방지를 위한 partial unique index
        jdbcTemplate.execute("""
        CREATE UNIQUE INDEX IF NOT EXISTS uq_daily_big3_entry_item
        ON daily_big3_entries (daily_big3_board_id, big3_item_id)
        WHERE removed_at is NULL
        """);

        // 하나의 지난주 item에서 파생되는 다음 주 item은 최대 하나다.
        jdbcTemplate.execute("""
        CREATE UNIQUE INDEX IF NOT EXISTS uq_big3_items_derived_from_item
        ON big3_items (derived_from_item_id)
        WHERE derived_from_item_id IS NOT NULL
        """);

        jdbcTemplate.execute("""
        DO $$
        BEGIN
            IF NOT EXISTS (
                SELECT 1
                FROM pg_constraint
                WHERE conname = 'chk_recovery_timeboxes_valid_period'
                  AND conrelid = 'recovery_timeboxes'::regclass
            ) THEN
                ALTER TABLE recovery_timeboxes
                    ADD CONSTRAINT chk_recovery_timeboxes_valid_period
                    CHECK (start_at < end_at)
                    NOT VALID;
            END IF;
        END
        $$;

        ALTER TABLE recovery_timeboxes
            VALIDATE CONSTRAINT chk_recovery_timeboxes_valid_period;
        """);

        jdbcTemplate.execute("""
        CREATE EXTENSION IF NOT EXISTS btree_gist;
        """);

        jdbcTemplate.execute("""
        DO $$
        BEGIN
            IF NOT EXISTS (
                SELECT 1
                FROM pg_constraint
                WHERE conname = 'ex_recovery_timeboxes_user_planned_period'
                  AND conrelid = 'recovery_timeboxes'::regclass
            ) THEN
                ALTER TABLE recovery_timeboxes
                    ADD CONSTRAINT ex_recovery_timeboxes_user_planned_period
                    EXCLUDE USING gist (
                        user_id WITH =,
                        tstzrange(start_at, end_at, '[)') WITH &&
                    )
                    WHERE (timebox_status = 'PLANNED');
            END IF;
        END
        $$;
        """);

    }
}
