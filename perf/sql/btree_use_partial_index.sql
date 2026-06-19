\set ON_ERROR_STOP on

DROP INDEX IF EXISTS idx_btree_api_full;

CREATE UNIQUE INDEX IF NOT EXISTS uq_daily_big3_entry_order
ON daily_big3_entries (daily_big3_board_id, slot_order)
WHERE removed_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_daily_big3_entry_item
ON daily_big3_entries (daily_big3_board_id, big3_item_id)
WHERE removed_at IS NULL;

ANALYZE daily_big3_entries;

SELECT
    'partial' AS index_mode,
    pg_relation_size('uq_daily_big3_entry_order'::regclass) AS index_bytes;
