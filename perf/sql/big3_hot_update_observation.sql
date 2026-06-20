-- DatabaseIndexInitializer의 fillfactor 설정과 실제 HOT update 누적치를 확인한다.
-- 대량 만료 테스트 직전/직후에 같은 쿼리를 실행해 값을 비교한다.

SELECT
    namespace.nspname AS schema_name,
    relation.relname AS table_name,
    relation.reloptions
FROM pg_class relation
JOIN pg_namespace namespace
  ON namespace.oid = relation.relnamespace
WHERE namespace.nspname = current_schema()
  AND relation.relname = 'big3_items';

SELECT
    schemaname,
    relname,
    n_tup_upd,
    n_tup_hot_upd,
    round(
        100.0 * n_tup_hot_upd / NULLIF(n_tup_upd, 0),
        2
    ) AS hot_update_ratio_percent,
    n_dead_tup,
    last_autovacuum,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE schemaname = current_schema()
  AND relname = 'big3_items';

SELECT
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND tablename = 'big3_items'
ORDER BY indexname;

SELECT
    pg_size_pretty(pg_relation_size('big3_items')) AS table_size,
    pg_size_pretty(pg_indexes_size('big3_items')) AS total_index_size,
    pg_size_pretty(pg_total_relation_size('big3_items')) AS total_size;
