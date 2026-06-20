package com.focuskeeper.reboot.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("perf")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "BTREE_BUFFER_TEST_ENABLED", matches = "true")
class BTreePartialIndexBufferEfficiencyTest {

    private static final String SCHEMA = "btree_buffer_probe";
    private static final String FULL_INDEX = "idx_btree_buffer_full";
    private static final String PARTIAL_INDEX = "idx_btree_buffer_partial";
    private static final int TOTAL_ROWS = 1_000_000;
    private static final int SOFT_DELETED_ROWS = 900_000;
    private static final double MAXIMUM_EXPECTED_PARTIAL_BUFFER_RATIO = 0.20;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Connection connection;

    // Run with:
    // BTREE_BUFFER_TEST_ENABLED=true ./gradlew test --no-daemon --rerun-tasks --info \
    //   --tests com.focuskeeper.reboot.common.persistence.BTreePartialIndexBufferEfficiencyTest

    @BeforeAll
    void setUpDatabase() throws Exception {
        connection = openConnection();

        System.out.printf(
                "%n========== B-Tree 부분 인덱스 버퍼 효율 테스트 ==========%n"
                        + "[준비 데이터]%n"
                        + "- 동일한 테이블 2개, 각 %,d건%n"
                        + "- 각 테이블의 소프트 삭제 row: %,d건%n"
                        + "- 각 테이블의 활성 row: %,d건%n"
                        + "[비교 대상]%n"
                        + "- Full index: 삭제 여부와 관계없이 전체 row 포함%n"
                        + "- Partial index: removed_at IS NULL인 활성 row만 포함%n"
                        + "[확인 목적]%n"
                        + "- 활성 row 조회 시 읽는 shared buffer block과 캐시 점유 page 비교%n"
                        + "=====================================================%n",
                TOTAL_ROWS,
                SOFT_DELETED_ROWS,
                TOTAL_ROWS - SOFT_DELETED_ROWS
        );

        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_buffercache");
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_prewarm");
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + SCHEMA);

            createProbeTable(statement, "probe_full");
            createProbeTable(statement, "probe_partial");

            statement.executeUpdate("""
                    INSERT INTO %s.probe_full (
                        id,
                        daily_big3_board_id,
                        slot_order,
                        removed_at
                    )
                    SELECT
                        sequence_number,
                        lpad(to_hex(sequence_number), 32, '0')::uuid,
                        (sequence_number %% 3) + 1,
                        CASE
                            WHEN sequence_number <= %d
                                THEN TIMESTAMPTZ '2026-06-12 00:00:00+09'
                            ELSE NULL
                        END
                    FROM generate_series(1, %d) AS sequence_number
                    """.formatted(SCHEMA, SOFT_DELETED_ROWS, TOTAL_ROWS));
            statement.executeUpdate("""
                    INSERT INTO %s.probe_partial
                    SELECT * FROM %s.probe_full
                    """.formatted(SCHEMA, SCHEMA));

            statement.execute("""
                    CREATE UNIQUE INDEX %s
                    ON %s.probe_full (daily_big3_board_id, slot_order)
                    """.formatted(FULL_INDEX, SCHEMA));
            statement.execute("""
                    CREATE UNIQUE INDEX %s
                    ON %s.probe_partial (daily_big3_board_id, slot_order)
                    WHERE removed_at IS NULL
                    """.formatted(PARTIAL_INDEX, SCHEMA));

            statement.execute("VACUUM ANALYZE " + SCHEMA + ".probe_full");
            statement.execute("VACUUM ANALYZE " + SCHEMA + ".probe_partial");
        }
    }

    @AfterAll
    void cleanUpDatabase() throws SQLException {
        if (connection == null) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        } finally {
            connection.close();
        }
    }

    @Test
    @DisplayName("활성 row 조회 시 부분 인덱스가 더 적은 shared buffer block을 읽는다")
    void partialIndexTouchesFewerBuffersForActiveRowScan() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
            statement.execute("SET enable_bitmapscan = off");

            ExplainMetrics full = explainActiveRowScan(statement, "probe_full");
            ExplainMetrics partial = explainActiveRowScan(statement, "probe_partial");

            printExplainPlan("Full index", full);
            printExplainPlan("Partial index", partial);

            assertThat(full.indexNames()).contains(FULL_INDEX);
            assertThat(partial.indexNames()).contains(PARTIAL_INDEX);
            assertThat(full.actualRows()).isEqualTo(TOTAL_ROWS - SOFT_DELETED_ROWS);
            assertThat(partial.actualRows()).isEqualTo(TOTAL_ROWS - SOFT_DELETED_ROWS);
            assertThat(full.rowsRemovedByFilter()).isEqualTo(SOFT_DELETED_ROWS);
            assertThat(partial.rowsRemovedByFilter()).isZero();
            assertThat(partial.sharedBlocks()).isLessThan(full.sharedBlocks());

            long savedBlocks = full.sharedBlocks() - partial.sharedBlocks();
            double reductionPercent = savedBlocks * 100.0 / full.sharedBlocks();

            System.out.printf(
                    "%n[판정] 활성 row 조회의 buffer 접근 감소 확인%n"
                            + "- 실제 반환 row: Full %,d건 / Partial %,d건%n"
                            + "- 필터에서 버린 row: Full %,d건 / Partial %,d건%n"
                            + "- shared block: Full %,d개 / Partial %,d개%n"
                            + "- 감소량: %,d blocks (%.2f%%)%n"
                            + "- 실행 시간: Full %.3f ms / Partial %.3f ms%n"
                            + "- 의미: 같은 활성 row를 반환하면서 삭제 row를 훑는 buffer I/O를 피했습니다.%n",
                    full.actualRows(),
                    partial.actualRows(),
                    full.rowsRemovedByFilter(),
                    partial.rowsRemovedByFilter(),
                    full.sharedBlocks(),
                    partial.sharedBlocks(),
                    savedBlocks,
                    reductionPercent,
                    full.executionTimeMillis(),
                    partial.executionTimeMillis()
            );

            System.out.printf(
                    "BTREE_BUFFER_ACCESS rows[active=%d,softDeleted=%d] "
                            + "blocks[full=%d,partial=%d,saved=%d] "
                            + "executionMs[full=%.3f,partial=%.3f]%n",
                    partial.actualRows(),
                    SOFT_DELETED_ROWS,
                    full.sharedBlocks(),
                    partial.sharedBlocks(),
                    full.sharedBlocks() - partial.sharedBlocks(),
                    full.executionTimeMillis(),
                    partial.executionTimeMillis()
            );
        }
    }

    @Test
    @DisplayName("부분 인덱스가 shared buffer에 더 적은 page를 점유한다")
    void partialIndexOccupiesFewerSharedBufferPages() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int fullPrewarmedPages = prewarm(statement, FULL_INDEX);
            long fullCachedPages = cachedPages(statement, FULL_INDEX);

            int partialPrewarmedPages = prewarm(statement, PARTIAL_INDEX);
            long partialCachedPages = cachedPages(statement, PARTIAL_INDEX);

            long blockSize = queryLong(statement, "SELECT current_setting('block_size')::bigint");
            long fullCachedBytes = fullCachedPages * blockSize;
            long partialCachedBytes = partialCachedPages * blockSize;
            double partialBufferRatio = (double) partialCachedPages / fullCachedPages;

            assertThat(fullCachedPages).isPositive();
            assertThat(partialCachedPages).isPositive();
            assertThat(fullCachedPages).isLessThanOrEqualTo(fullPrewarmedPages);
            assertThat(partialCachedPages).isLessThanOrEqualTo(partialPrewarmedPages);
            assertThat(partialCachedPages).isLessThan(fullCachedPages);
            assertThat(partialBufferRatio).isLessThanOrEqualTo(MAXIMUM_EXPECTED_PARTIAL_BUFFER_RATIO);

            System.out.printf(
                    "%n[판정] 부분 인덱스의 shared buffer 점유 감소 확인%n"
                            + "- Full index cache: %,d pages, %s%n"
                            + "- Partial index cache: %,d pages, %s%n"
                            + "- 절감량: %,d pages, %s%n"
                            + "- Partial/Full 비율: %.2f%%%n"
                            + "- 의미: 같은 buffer pool에서 부분 인덱스가 차지하는 페이지가 더 적습니다.%n",
                    fullCachedPages,
                    formatBytes(fullCachedBytes),
                    partialCachedPages,
                    formatBytes(partialCachedBytes),
                    fullCachedPages - partialCachedPages,
                    formatBytes(fullCachedBytes - partialCachedBytes),
                    partialBufferRatio * 100.0
            );

            System.out.printf(
                    "BTREE_BUFFER_CACHE pages[full=%d,partial=%d] "
                            + "bytes[full=%d,partial=%d,saved=%d] partialRatio=%.4f%n",
                    fullCachedPages,
                    partialCachedPages,
                    fullCachedBytes,
                    partialCachedBytes,
                    fullCachedBytes - partialCachedBytes,
                    partialBufferRatio
            );
        }
    }

    private void createProbeTable(Statement statement, String tableName) throws SQLException {
        statement.execute("""
                CREATE UNLOGGED TABLE %s.%s (
                    id BIGINT PRIMARY KEY,
                    daily_big3_board_id UUID NOT NULL,
                    slot_order INTEGER NOT NULL,
                    removed_at TIMESTAMPTZ
                )
                """.formatted(SCHEMA, tableName));
    }

    private ExplainMetrics explainActiveRowScan(Statement statement, String tableName) throws Exception {
        String sql = """
                EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, FORMAT JSON)
                SELECT daily_big3_board_id, slot_order
                FROM %s.%s
                WHERE removed_at IS NULL
                ORDER BY daily_big3_board_id, slot_order
                """.formatted(SCHEMA, tableName);

        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();

            JsonNode explain = objectMapper.readTree(resultSet.getString(1)).get(0);
            JsonNode plan = explain.get("Plan");
            List<String> indexNames = new ArrayList<>();
            collectIndexNames(plan, indexNames);

            return new ExplainMetrics(
                    plan.path("Shared Hit Blocks").asLong() + plan.path("Shared Read Blocks").asLong(),
                    plan.path("Actual Rows").asLong(),
                    sumLongField(plan, "Rows Removed by Filter"),
                    explain.path("Execution Time").asDouble(),
                    indexNames,
                    formatPlanTree(plan)
            );
        }
    }

    private void printExplainPlan(String label, ExplainMetrics metrics) {
        System.out.printf(
                "%n========== EXPLAIN ANALYZE: %s ==========%n"
                        + "Options: ANALYZE, BUFFERS, TIMING OFF, FORMAT JSON%n"
                        + "%s"
                        + "Execution Time: %.3f ms%n"
                        + "===============================================%n",
                label,
                metrics.planTree(),
                metrics.executionTimeMillis()
        );
    }

    private String formatPlanTree(JsonNode plan) {
        StringBuilder output = new StringBuilder();
        appendPlanNode(output, plan, 0);
        return output.toString();
    }

    private void appendPlanNode(StringBuilder output, JsonNode node, int depth) {
        String indent = "  ".repeat(depth);
        output.append(indent)
                .append("-> ")
                .append(node.path("Node Type").asText("Unknown"));

        if (node.has("Relation Name")) {
            output.append(" on ").append(node.get("Relation Name").asText());
        }
        if (node.has("Index Name")) {
            output.append(" using ").append(node.get("Index Name").asText());
        }

        output.append(" (actual rows=")
                .append(node.path("Actual Rows").asLong())
                .append(" loops=")
                .append(node.path("Actual Loops").asLong())
                .append(")")
                .append(System.lineSeparator());

        appendPlanDetail(output, indent, node, "Index Cond");
        appendPlanDetail(output, indent, node, "Filter");

        long rowsRemovedByFilter = node.path("Rows Removed by Filter").asLong();
        if (rowsRemovedByFilter > 0) {
            output.append(indent)
                    .append("   Rows Removed by Filter: ")
                    .append(rowsRemovedByFilter)
                    .append(System.lineSeparator());
        }

        long sharedHitBlocks = node.path("Shared Hit Blocks").asLong();
        long sharedReadBlocks = node.path("Shared Read Blocks").asLong();
        if (sharedHitBlocks > 0 || sharedReadBlocks > 0) {
            output.append(indent)
                    .append("   Buffers: shared hit=")
                    .append(sharedHitBlocks)
                    .append(" read=")
                    .append(sharedReadBlocks)
                    .append(System.lineSeparator());
        }

        for (JsonNode child : node.path("Plans")) {
            appendPlanNode(output, child, depth + 1);
        }
    }

    private void appendPlanDetail(
            StringBuilder output,
            String indent,
            JsonNode node,
            String fieldName
    ) {
        if (node.has(fieldName)) {
            output.append(indent)
                    .append("   ")
                    .append(fieldName)
                    .append(": ")
                    .append(node.get(fieldName).asText())
                    .append(System.lineSeparator());
        }
    }

    private String formatBytes(long bytes) {
        return "%,.2f MiB".formatted(bytes / 1024.0 / 1024.0);
    }

    private void collectIndexNames(JsonNode node, List<String> indexNames) {
        if (node.has("Index Name")) {
            indexNames.add(node.get("Index Name").asText());
        }
        node.path("Plans").forEach(child -> collectIndexNames(child, indexNames));
    }

    private long sumLongField(JsonNode node, String fieldName) {
        long value = node.path(fieldName).asLong();
        for (JsonNode child : node.path("Plans")) {
            value += sumLongField(child, fieldName);
        }
        return value;
    }

    private int prewarm(Statement statement, String indexName) throws SQLException {
        return Math.toIntExact(queryLong(
                statement,
                "SELECT pg_prewarm('%s.%s'::regclass, 'buffer')".formatted(SCHEMA, indexName)
        ));
    }

    private long cachedPages(Statement statement, String indexName) throws SQLException {
        return queryLong(statement, """
                SELECT count(*)
                FROM pg_buffercache
                WHERE relfilenode = pg_relation_filenode('%s.%s'::regclass)
                  AND reldatabase = (
                      SELECT oid
                      FROM pg_database
                      WHERE datname = current_database()
                  )
                  AND relforknumber = 0
                """.formatted(SCHEMA, indexName));
    }

    private Connection openConnection() throws SQLException {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String database = System.getenv().getOrDefault("DB_NAME", "rebootfocus");
        String username = System.getenv().getOrDefault("DB_USERNAME", "rebootfocus");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "rebootfocus");
        String url = "jdbc:postgresql://%s:%s/%s".formatted(host, port, database);

        return DriverManager.getConnection(url, username, password);
    }

    private long queryLong(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private record ExplainMetrics(
            long sharedBlocks,
            long actualRows,
            long rowsRemovedByFilter,
            double executionTimeMillis,
            List<String> indexNames,
            String planTree
    ) {
    }
}
