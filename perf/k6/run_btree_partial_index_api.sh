#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K6_BIN="${K6_BIN:-/opt/homebrew/bin/k6}"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
DB_NAME="${DB_NAME:-rebootfocus_btree_bench}"
DB_USERNAME="${DB_USERNAME:-rebootfocus}"
BENCHMARK_DATE="${BENCHMARK_DATE:-$(date +%F)}"
BOARD_COUNT="${BOARD_COUNT:-33334}"
ACTIVE_ROWS="${ACTIVE_ROWS:-100000}"
SOFT_DELETED_ROWS="${SOFT_DELETED_ROWS:-900000}"
USER_COUNT="${USER_COUNT:-33333}"
RATE="${RATE:-100}"
DURATION="${DURATION:-30s}"
WARMUP_RATE="${WARMUP_RATE:-50}"
WARMUP_DURATION="${WARMUP_DURATION:-10s}"
ROUNDS="${ROUNDS:-3}"
SEED="${SEED:-false}"
RESULT_DIR="${RESULT_DIR:-${ROOT_DIR}/perf/results/btree-partial-index-api}"
MODE="${1:-compare}"

PSQL=(
  docker compose exec -T postgres
  psql -v ON_ERROR_STOP=1
  -U "${DB_USERNAME}"
  -d "${DB_NAME}"
)

restore_partial_index() {
  "${PSQL[@]}" < "${ROOT_DIR}/perf/sql/btree_use_partial_index.sql" >/dev/null
}

if [[ ! -x "${K6_BIN}" ]]; then
  echo "k6 executable not found: ${K6_BIN}" >&2
  exit 1
fi

if ! curl -fsS "${BASE_URL}/api/v1/health" >/dev/null; then
  echo "Application is not running at ${BASE_URL}" >&2
  echo "Start it before seeding because the local profile uses ddl-auto=create." >&2
  exit 1
fi

mkdir -p "${RESULT_DIR}"

if [[ "${SEED}" == "true" ]]; then
  "${PSQL[@]}" \
    -v benchmark_date="${BENCHMARK_DATE}" \
    -v board_count="${BOARD_COUNT}" \
    -v active_rows="${ACTIVE_ROWS}" \
    -v soft_deleted_rows="${SOFT_DELETED_ROWS}" \
    < "${ROOT_DIR}/perf/sql/btree_partial_index_api_fixture.sql"
fi

fixture_count="$("${PSQL[@]}" -Atc "
  SELECT count(*)
  FROM daily_big3_entries
  WHERE id LIKE 'btree-a-%' OR id LIKE 'btree-d-%'
")"

expected_count=$((ACTIVE_ROWS + SOFT_DELETED_ROWS))
if [[ "${fixture_count}" != "${expected_count}" ]]; then
  echo "Fixture row count mismatch: expected=${expected_count}, actual=${fixture_count}" >&2
  echo "Run with SEED=true after the application has started." >&2
  exit 1
fi

trap restore_partial_index EXIT

apply_index_mode() {
  local index_mode="$1"
  "${PSQL[@]}" < "${ROOT_DIR}/perf/sql/btree_use_${index_mode}_index.sql"
}

record_explain() {
  local index_mode="$1"
  "${PSQL[@]}" -c "
    EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
    SELECT id, daily_big3_board_id, big3_item_id, slot_order
    FROM daily_big3_entries
    WHERE daily_big3_board_id = 'btree-board-00000001'
      AND removed_at IS NULL
    ORDER BY slot_order
  " > "${RESULT_DIR}/${index_mode}-explain.txt"
}

run_k6() {
  local index_mode="$1"
  local round="$2"
  local result_file="${RESULT_DIR}/${index_mode}-round-${round}.json"

  INDEX_MODE="${index_mode}" \
  BASE_URL="${BASE_URL}" \
  USER_COUNT="${USER_COUNT}" \
  RATE="${WARMUP_RATE}" \
  DURATION="${WARMUP_DURATION}" \
  P95_THRESHOLD_MS=5000 \
  "${K6_BIN}" run --quiet \
    "${ROOT_DIR}/perf/k6/btree_partial_index_api.js" >/dev/null

  INDEX_MODE="${index_mode}" \
  BASE_URL="${BASE_URL}" \
  USER_COUNT="${USER_COUNT}" \
  RATE="${RATE}" \
  DURATION="${DURATION}" \
  "${K6_BIN}" run \
    --summary-export "${result_file}" \
    "${ROOT_DIR}/perf/k6/btree_partial_index_api.js"
}

run_mode() {
  local index_mode="$1"
  local round="$2"

  echo "Applying ${index_mode} index for round ${round}"
  apply_index_mode "${index_mode}"
  record_explain "${index_mode}"
  run_k6 "${index_mode}" "${round}"
}

case "${MODE}" in
  full|partial)
    for round in $(seq 1 "${ROUNDS}"); do
      run_mode "${MODE}" "${round}"
    done
    ;;
  compare)
    for round in $(seq 1 "${ROUNDS}"); do
      if (( round % 2 == 1 )); then
        run_mode full "${round}"
        run_mode partial "${round}"
      else
        run_mode partial "${round}"
        run_mode full "${round}"
      fi
    done
    ;;
  *)
    echo "Usage: $0 [compare|full|partial]" >&2
    exit 1
    ;;
esac

printf '\n%-24s %8s %10s %10s %10s %10s %8s\n' \
  "result" "requests" "avg_ms" "median_ms" "p95_ms" "p99_ms" "fail"
for result_file in "${RESULT_DIR}"/*-round-*.json; do
  jq -r --arg file "$(basename "${result_file}")" '
    [
      $file,
      (.metrics.http_reqs.count | tostring),
      (.metrics.http_req_duration.avg | tostring),
      (.metrics.http_req_duration.med | tostring),
      (.metrics.http_req_duration["p(95)"] | tostring),
      (.metrics.http_req_duration["p(99)"] | tostring),
      (.metrics.http_req_failed.value | tostring)
    ] | @tsv
  ' "${result_file}" |
    awk -F '\t' '{ printf "%-24s %8s %10.2f %10.2f %10.2f %10.2f %8.4f\n", $1, $2, $3, $4, $5, $6, $7 }'
done

echo "Results written to ${RESULT_DIR}"
