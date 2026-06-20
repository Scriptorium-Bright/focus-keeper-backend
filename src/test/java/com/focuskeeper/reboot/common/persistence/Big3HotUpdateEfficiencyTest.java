package com.focuskeeper.reboot.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("perf")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "BIG3_HOT_UPDATE_TEST_ENABLED", matches = "true")
class Big3HotUpdateEfficiencyTest {

    private static final String SCHEMA = "big3_hot_probe";
    private static final String FILLFACTOR_100_TABLE = "big3_fillfactor_100";
    private static final String FILLFACTOR_80_TABLE = "big3_fillfactor_80";
    private static final String ACTUAL_INDEX_TABLE = "big3_fillfactor_80_actual_index";
    private static final int TOTAL_ROWS = 200_000;
    private static final int EXPECTED_UPDATED_ROWS = TOTAL_ROWS;
    private static final double MINIMUM_HOT_RATIO_IMPROVEMENT = 0.10;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Connection connection;

    // Run with:
    // BIG3_HOT_UPDATE_TEST_ENABLED=true ./gradlew test --no-daemon --rerun-tasks --info \
    //   --tests com.focuskeeper.reboot.common.persistence.Big3HotUpdateEfficiencyTest

    @BeforeAll
    void setUpDatabase() throws SQLException {
        connection = openConnection();

        System.out.printf(
                "%n========== Big3 HOT update 효율 테스트 ==========%n"
                        + "[준비 데이터]%n"
                        + "- 동일한 테이블 3개, 각 %,d건%n"
                        + "- 비교 조건: fillfactor=100 / fillfactor=80 / fillfactor=80+실제 status index%n"
                        + "- 갱신 컬럼: status, expired_at, updated_at, version%n"
                        + "[확인 목적]%n"
                        + "- fillfactor 여유 공간이 같은 page의 새 tuple 저장에 사용되는지 확인%n"
                        + "- pg_stat_user_tables의 HOT update 비율과 WAL/buffer 차이 확인%n"
                        + "=================================================%n",
                TOTAL_ROWS
        );

        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + SCHEMA);
            createProbeTable(statement, FILLFACTOR_100_TABLE, 100);
            createProbeTable(statement, FILLFACTOR_80_TABLE, 80);
            createProbeTable(statement, ACTUAL_INDEX_TABLE, 80);
            insertFixture(statement, FILLFACTOR_100_TABLE);
            copyFixture(statement, FILLFACTOR_80_TABLE);
            copyFixture(statement, ACTUAL_INDEX_TABLE);
            statement.execute("""
                    CREATE INDEX status_week_start_id_indexes_probe
                    ON %s (status, week_start, id)
                    """.formatted(qualified(ACTUAL_INDEX_TABLE)));
            statement.execute("VACUUM ANALYZE " + qualified(FILLFACTOR_100_TABLE));
            statement.execute("VACUUM ANALYZE " + qualified(FILLFACTOR_80_TABLE));
            statement.execute("VACUUM ANALYZE " + qualified(ACTUAL_INDEX_TABLE));
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
    @DisplayName("fillfactor는 HOT을 유도하지만 실제 status 인덱스에서는 HOT이 불가능하다")
    void statusIndexPreventsHotUpdateEvenWithFillfactor80() throws Exception {
        try (Statement statement = connection.createStatement()) {
            resetTableStats(statement, FILLFACTOR_100_TABLE);
            resetTableStats(statement, FILLFACTOR_80_TABLE);
            resetTableStats(statement, ACTUAL_INDEX_TABLE);

            ExplainMetrics fillfactor100Explain = explainExpirationUpdate(
                    statement,
                    FILLFACTOR_100_TABLE
            );
            ExplainMetrics fillfactor80Explain = explainExpirationUpdate(
                    statement,
                    FILLFACTOR_80_TABLE
            );
            ExplainMetrics actualIndexExplain = explainExpirationUpdate(
                    statement,
                    ACTUAL_INDEX_TABLE
            );

            TableStats fillfactor100Stats = waitForStats(statement, FILLFACTOR_100_TABLE);
            TableStats fillfactor80Stats = waitForStats(statement, FILLFACTOR_80_TABLE);
            TableStats actualIndexStats = waitForStats(statement, ACTUAL_INDEX_TABLE);

            printResult("fillfactor=100, PK only", fillfactor100Explain, fillfactor100Stats);
            printResult("fillfactor=80, PK only", fillfactor80Explain, fillfactor80Stats);
            printResult(
                    "fillfactor=80, actual (status, week_start, id) index",
                    actualIndexExplain,
                    actualIndexStats
            );

            assertThat(countExpiredRows(statement, FILLFACTOR_100_TABLE))
                    .isEqualTo(EXPECTED_UPDATED_ROWS);
            assertThat(countExpiredRows(statement, FILLFACTOR_80_TABLE))
                    .isEqualTo(EXPECTED_UPDATED_ROWS);
            assertThat(countExpiredRows(statement, ACTUAL_INDEX_TABLE))
                    .isEqualTo(EXPECTED_UPDATED_ROWS);
            assertThat(fillfactor100Stats.updatedRows()).isEqualTo(EXPECTED_UPDATED_ROWS);
            assertThat(fillfactor80Stats.updatedRows()).isEqualTo(EXPECTED_UPDATED_ROWS);
            assertThat(actualIndexStats.updatedRows()).isEqualTo(EXPECTED_UPDATED_ROWS);
            assertThat(fillfactor80Stats.hotUpdateRatio())
                    .isGreaterThan(
                            fillfactor100Stats.hotUpdateRatio()
                                    + MINIMUM_HOT_RATIO_IMPROVEMENT
                    );
            assertThat(actualIndexStats.hotUpdatedRows()).isZero();

            System.out.printf(
                    "%n[판정] 실제 Big3 인덱스에서는 fillfactor만으로 HOT update를 만들 수 없음%n"
                            + "- HOT 비율: fillfactor=100 %.2f%% / fillfactor=80 %.2f%%"
                            + " / fillfactor=80+status index %.2f%%%n"
                            + "- PK만 있을 때는 page 여유 공간으로 HOT이 증가했습니다.%n"
                            + "- 실제 status 인덱스에서는 변경 컬럼 status의 index entry를 갱신해야 해 HOT이 0건입니다.%n"
                            + "- 결론: 현재 인덱스를 유지하는 동안 fillfactor=80을 HOT 최적화로 채택하지 않습니다.%n",
                    fillfactor100Stats.hotUpdateRatio() * 100.0,
                    fillfactor80Stats.hotUpdateRatio() * 100.0,
                    actualIndexStats.hotUpdateRatio() * 100.0
            );

            System.out.printf(
                    "BIG3_HOT_UPDATE rows=%d "
                            + "hotRatio[fillfactor100=%.4f,fillfactor80=%.4f,actualIndex=%.4f] "
                            + "walBytes[fillfactor100=%d,fillfactor80=%d,actualIndex=%d] "
                            + "executionMs[fillfactor100=%.3f,fillfactor80=%.3f,actualIndex=%.3f]%n",
                    EXPECTED_UPDATED_ROWS,
                    fillfactor100Stats.hotUpdateRatio(),
                    fillfactor80Stats.hotUpdateRatio(),
                    actualIndexStats.hotUpdateRatio(),
                    fillfactor100Explain.walBytes(),
                    fillfactor80Explain.walBytes(),
                    actualIndexExplain.walBytes(),
                    fillfactor100Explain.executionTimeMillis(),
                    fillfactor80Explain.executionTimeMillis(),
                    actualIndexExplain.executionTimeMillis()
            );
        }
    }

    private void createProbeTable(
            Statement statement,
            String tableName,
            int fillfactor
    ) throws SQLException {
        statement.execute("""
                CREATE TABLE %s.%s (
                    id BIGINT PRIMARY KEY,
                    week_start DATE NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    expired_at TIMESTAMPTZ,
                    updated_at TIMESTAMPTZ NOT NULL,
                    version BIGINT NOT NULL,
                    payload VARCHAR(160) NOT NULL
                ) WITH (fillfactor = %d)
                """.formatted(SCHEMA, tableName, fillfactor));
    }

    private void insertFixture(Statement statement, String tableName) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO %s.%s (
                    id,
                    week_start,
                    status,
                    expired_at,
                    updated_at,
                    version,
                    payload
                )
                SELECT
                    sequence_number,
                    DATE '2026-05-25',
                    'OPEN',
                    NULL,
                    TIMESTAMPTZ '2026-05-25 00:00:00+09',
                    0,
                    repeat('x', 160)
                FROM generate_series(1, %d) AS sequence_number
                """.formatted(SCHEMA, tableName, TOTAL_ROWS));
    }

    private void copyFixture(Statement statement, String targetTableName) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO %s
                SELECT * FROM %s
                """.formatted(
                qualified(targetTableName),
                qualified(FILLFACTOR_100_TABLE)
        ));
    }

    private ExplainMetrics explainExpirationUpdate(
            Statement statement,
            String tableName
    ) throws Exception {
        String sql = """
                EXPLAIN (ANALYZE, BUFFERS, WAL, TIMING OFF, FORMAT JSON)
                UPDATE %s
                SET status = 'EXPIRED',
                    expired_at = TIMESTAMPTZ '2026-06-14 00:00:00+09',
                    updated_at = TIMESTAMPTZ '2026-06-14 00:00:00+09',
                    version = version + 1
                WHERE status = 'OPEN'
                  AND week_start < DATE '2026-06-08'
                """.formatted(qualified(tableName));

        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            JsonNode explain = objectMapper.readTree(resultSet.getString(1)).get(0);
            JsonNode plan = explain.path("Plan");

            return new ExplainMetrics(
                    explain.path("Execution Time").asDouble(),
                    plan.path("Shared Hit Blocks").asLong()
                            + plan.path("Shared Read Blocks").asLong(),
                    plan.path("Shared Dirtied Blocks").asLong(),
                    plan.path("Shared Written Blocks").asLong(),
                    plan.path("WAL Records").asLong(),
                    plan.path("WAL FPI").asLong(),
                    plan.path("WAL Bytes").asLong()
            );
        }
    }

    private TableStats waitForStats(Statement statement, String tableName) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            statement.execute("SELECT pg_stat_force_next_flush()");
            statement.execute("SELECT pg_stat_clear_snapshot()");

            TableStats stats = queryStats(statement, tableName);
            if (stats.updatedRows() >= EXPECTED_UPDATED_ROWS) {
                return stats;
            }
            Thread.sleep(100);
        }

        return queryStats(statement, tableName);
    }

    private TableStats queryStats(Statement statement, String tableName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT n_tup_upd, n_tup_hot_upd
                FROM pg_stat_user_tables
                WHERE schemaname = '%s'
                  AND relname = '%s'
                """.formatted(SCHEMA, tableName))) {
            assertThat(resultSet.next()).isTrue();
            long updatedRows = resultSet.getLong("n_tup_upd");
            long hotUpdatedRows = resultSet.getLong("n_tup_hot_upd");
            double hotUpdateRatio = updatedRows == 0
                    ? 0.0
                    : (double) hotUpdatedRows / updatedRows;
            return new TableStats(updatedRows, hotUpdatedRows, hotUpdateRatio);
        }
    }

    private void resetTableStats(Statement statement, String tableName) throws SQLException {
        statement.execute(
                "SELECT pg_stat_reset_single_table_counters('%s'::regclass)"
                        .formatted(qualified(tableName))
        );
    }

    private long countExpiredRows(Statement statement, String tableName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT count(*)
                FROM %s
                WHERE status = 'EXPIRED'
                """.formatted(qualified(tableName)))) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private void printResult(
            String label,
            ExplainMetrics explain,
            TableStats stats
    ) {
        System.out.printf(
                "%n[%s]%n"
                        + "- updated rows: %,d%n"
                        + "- HOT updated rows: %,d%n"
                        + "- HOT ratio: %.2f%%%n"
                        + "- shared blocks: touched %,d / dirtied %,d / written %,d%n"
                        + "- WAL: records %,d / FPI %,d / bytes %,d%n"
                        + "- execution time: %.3f ms%n",
                label,
                stats.updatedRows(),
                stats.hotUpdatedRows(),
                stats.hotUpdateRatio() * 100.0,
                explain.sharedBlocks(),
                explain.sharedDirtiedBlocks(),
                explain.sharedWrittenBlocks(),
                explain.walRecords(),
                explain.walFullPageImages(),
                explain.walBytes(),
                explain.executionTimeMillis()
        );
    }

    private String qualified(String tableName) {
        return SCHEMA + "." + tableName;
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

    private record ExplainMetrics(
            double executionTimeMillis,
            long sharedBlocks,
            long sharedDirtiedBlocks,
            long sharedWrittenBlocks,
            long walRecords,
            long walFullPageImages,
            long walBytes
    ) {
    }

    private record TableStats(
            long updatedRows,
            long hotUpdatedRows,
            double hotUpdateRatio
    ) {
    }
}
