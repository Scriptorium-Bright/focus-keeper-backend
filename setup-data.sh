#!/bin/bash

# DB 접속 정보 설정
DB_HOST="localhost"
DB_PORT="5432"
DB_USER="rebootfocus"
DB_NAME="rebootfocus_oom"
TARGET_ROWS=3000000

echo "[Data Setup] 기존 데이터를 초기화하고 ${TARGET_ROWS}건의 더미 데이터를 적재합니다..."

# psql 비밀번호 환경변수 주입
export PGPASSWORD="rebootfocus"

psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "
TRUNCATE TABLE big3_items CASCADE;
TRUNCATE TABLE inbox_items CASCADE;

INSERT INTO inbox_items (id, user_id, content, created_at)
VALUES ('oom-harness-inbox', 'oom-memory-harness-user', 'Expiration memory pressure harness', now() - interval '2 weeks');

INSERT INTO big3_items (
    id, origin_inbox_item_id, user_id, week_start, title_snapshot, status, created_at, updated_at, version
)
SELECT
    'oom-' || lpad(sequence_number::text, 32, '0'),
    'oom-harness-inbox',
    'oom-memory-harness-user',
    date_trunc('week', now() - interval '2 weeks')::date,
    'Expiration candidate',
    'OPEN',
    now() - interval '2 weeks',
    now() - interval '2 weeks',
    0
FROM generate_series(1, ${TARGET_ROWS}) AS sequence_number;
"

echo "[Data Setup] 300만 건 데이터 적재 완료."