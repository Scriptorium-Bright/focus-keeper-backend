\set ON_ERROR_STOP on

\echo 'Preparing B-Tree API benchmark fixture'

DROP INDEX IF EXISTS idx_btree_api_full;
DROP INDEX IF EXISTS uq_daily_big3_entry_order;
DROP INDEX IF EXISTS uq_daily_big3_entry_item;

DELETE FROM daily_big3_entries
WHERE id LIKE 'btree-a-%'
   OR id LIKE 'btree-d-%';

DELETE FROM daily_big3_boards
WHERE id LIKE 'btree-board-%';

DELETE FROM big3_items
WHERE id LIKE 'btree-item-%';

DELETE FROM inbox_items
WHERE id LIKE 'btree-inbox-%';

INSERT INTO inbox_items (id, user_id, content, created_at)
SELECT
    'btree-inbox-' || slot_number,
    'btree-shared-user',
    'B-Tree benchmark item ' || slot_number,
    now()
FROM generate_series(1, 3) AS slot_number;

INSERT INTO big3_items (
    id,
    origin_inbox_item_id,
    user_id,
    week_start,
    title_snapshot,
    status,
    version,
    created_at,
    updated_at
)
SELECT
    'btree-item-' || slot_number,
    'btree-inbox-' || slot_number,
    'btree-shared-user',
    date_trunc('week', :'benchmark_date'::date)::date,
    'B-Tree benchmark item ' || slot_number,
    'OPEN',
    0,
    now(),
    now()
FROM generate_series(1, 3) AS slot_number;

INSERT INTO daily_big3_boards (
    id,
    user_id,
    selected_date,
    selected_at,
    created_at,
    updated_at
)
SELECT
    'btree-board-' || lpad(board_number::text, 8, '0'),
    'btree-load-' || lpad(board_number::text, 8, '0'),
    :'benchmark_date'::date,
    now(),
    now(),
    now()
FROM generate_series(1, :board_count) AS board_number;

INSERT INTO daily_big3_entries (
    id,
    daily_big3_board_id,
    big3_item_id,
    slot_order,
    selection_source,
    selected_at,
    removed_at,
    created_at,
    updated_at
)
SELECT
    'btree-d-' || lpad(sequence_number::text, 28, '0'),
    'btree-board-' || lpad((((sequence_number - 1) % :board_count) + 1)::text, 8, '0'),
    'btree-item-' || (((sequence_number - 1) % 3) + 1),
    ((sequence_number - 1) % 3) + 1,
    'NEW',
    now() - interval '30 days',
    now() - interval '1 day',
    now() - interval '30 days',
    now() - interval '1 day'
FROM generate_series(1, :soft_deleted_rows) AS sequence_number;

INSERT INTO daily_big3_entries (
    id,
    daily_big3_board_id,
    big3_item_id,
    slot_order,
    selection_source,
    selected_at,
    removed_at,
    created_at,
    updated_at
)
SELECT
    'btree-a-' || lpad(sequence_number::text, 28, '0'),
    'btree-board-' || lpad((((sequence_number - 1) / 3) + 1)::text, 8, '0'),
    'btree-item-' || (((sequence_number - 1) % 3) + 1),
    ((sequence_number - 1) % 3) + 1,
    'NEW',
    now(),
    NULL,
    now(),
    now()
FROM generate_series(1, :active_rows) AS sequence_number;

ANALYZE daily_big3_boards;
ANALYZE daily_big3_entries;
ANALYZE big3_items;
ANALYZE inbox_items;

SELECT
    count(*) AS total_rows,
    count(*) FILTER (WHERE removed_at IS NOT NULL) AS soft_deleted_rows,
    count(*) FILTER (WHERE removed_at IS NULL) AS active_rows
FROM daily_big3_entries
WHERE id LIKE 'btree-a-%'
   OR id LIKE 'btree-d-%';
