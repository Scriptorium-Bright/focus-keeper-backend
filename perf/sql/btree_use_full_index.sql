\set ON_ERROR_STOP on

DROP INDEX IF EXISTS uq_daily_big3_entry_order;
DROP INDEX IF EXISTS uq_daily_big3_entry_item;
DROP INDEX IF EXISTS idx_btree_api_full;

CREATE INDEX idx_btree_api_full
ON daily_big3_entries (daily_big3_board_id, slot_order);

ANALYZE daily_big3_entries;

SELECT
    'full' AS index_mode,
    pg_relation_size('idx_btree_api_full'::regclass) AS index_bytes;
