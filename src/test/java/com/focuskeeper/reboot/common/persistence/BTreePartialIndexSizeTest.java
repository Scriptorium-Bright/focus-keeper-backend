package com.focuskeeper.reboot.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("perf")
@EnabledIfEnvironmentVariable(named = "BTREE_INDEX_SIZE_TEST_ENABLED", matches = "true")
class BTreePartialIndexSizeTest {

    private static final int TOTAL_ROWS = 1_000_000;
    private static final int SOFT_DELETED_ROWS = 900_000;
    private static final double MINIMUM_EXPECTED_REDUCTION_PERCENT = 80.0;

    // Run with:
    // BTREE_INDEX_SIZE_TEST_ENABLED=true ./gradlew test --no-daemon --rerun-tasks --info \
    //   --tests com.focuskeeper.reboot.common.persistence.BTreePartialIndexSizeTest

    @Test
    @DisplayName("소프트 삭제 90% 조건에서 부분 인덱스의 실제 byte 절감률을 측정한다")
    void partialIndexExcludesSoftDeletedRowsAndReducesPhysicalSize() throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            System.out.printf(
                    "%n========== B-Tree 부분 인덱스 물리 용량 테스트 ==========%n"
                            + "[준비 데이터]%n"
                            + "- 전체 row: %,d건%n"
                            + "- 소프트 삭제 row: %,d건%n"
                            + "- 활성 row: %,d건%n"
                            + "[비교 대상]%n"
                            + "- Full index: 전체 %,d건을 인덱싱%n"
                            + "- Partial index: removed_at IS NULL인 활성 %,d건만 인덱싱%n"
                            + "[확인 목적]%n"
                            + "- 테이블 크기가 아니라 두 B-Tree 인덱스 자체의 실제 byte 크기를 비교%n"
                            + "=====================================================%n",
                    TOTAL_ROWS,
                    SOFT_DELETED_ROWS,
                    TOTAL_ROWS - SOFT_DELETED_ROWS,
                    TOTAL_ROWS,
                    TOTAL_ROWS - SOFT_DELETED_ROWS
            );

            statement.execute("""
                    CREATE TEMP TABLE btree_index_size_probe (
                        id BIGINT PRIMARY KEY,
                        daily_big3_board_id UUID NOT NULL,
                        slot_order INTEGER NOT NULL,
                        removed_at TIMESTAMPTZ
                    )
                    """);

            statement.executeUpdate("""
                    INSERT INTO btree_index_size_probe (
                        id,
                        daily_big3_board_id,
                        slot_order,
                        removed_at
                    )
                    SELECT
                        sequence_number,
                        md5(sequence_number::text)::uuid,
                        (sequence_number %% 3) + 1,
                        CASE
                            WHEN sequence_number <= %d
                                THEN TIMESTAMPTZ '2026-06-12 00:00:00+09'
                            ELSE NULL
                        END
                    FROM generate_series(1, %d) AS sequence_number
                    """.formatted(SOFT_DELETED_ROWS, TOTAL_ROWS));

            statement.execute("""
                    CREATE UNIQUE INDEX idx_btree_full_probe
                    ON btree_index_size_probe (daily_big3_board_id, slot_order)
                    """);
            statement.execute("""
                    CREATE UNIQUE INDEX idx_btree_partial_probe
                    ON btree_index_size_probe (daily_big3_board_id, slot_order)
                    WHERE removed_at IS NULL
                    """);

            long totalRows = queryLong(statement, "SELECT count(*) FROM btree_index_size_probe");
            long softDeletedRows = queryLong(
                    statement,
                    "SELECT count(*) FROM btree_index_size_probe WHERE removed_at IS NOT NULL"
            );
            long activeRows = queryLong(
                    statement,
                    "SELECT count(*) FROM btree_index_size_probe WHERE removed_at IS NULL"
            );
            long fullIndexBytes = queryLong(
                    statement,
                    "SELECT pg_relation_size('idx_btree_full_probe'::regclass)"
            );
            long partialIndexBytes = queryLong(
                    statement,
                    "SELECT pg_relation_size('idx_btree_partial_probe'::regclass)"
            );
            double reductionPercent = (fullIndexBytes - partialIndexBytes) * 100.0 / fullIndexBytes;

            assertThat(totalRows).isEqualTo(TOTAL_ROWS);
            assertThat(softDeletedRows).isEqualTo(SOFT_DELETED_ROWS);
            assertThat(activeRows).isEqualTo(TOTAL_ROWS - SOFT_DELETED_ROWS);
            assertThat(partialIndexBytes).isLessThan(fullIndexBytes);
            assertThat(reductionPercent).isGreaterThanOrEqualTo(MINIMUM_EXPECTED_REDUCTION_PERCENT);

            System.out.printf(
                    "%n[판정] 부분 인덱스 물리 용량 절감 확인%n"
                            + "- Full index: %s (%,d bytes)%n"
                            + "- Partial index: %s (%,d bytes)%n"
                            + "- 절감량: %s (%,d bytes)%n"
                            + "- 절감률: %.2f%%%n"
                            + "- 의미: 소프트 삭제 90%%인 조건에서 활성 row만 적재해 B-Tree 저장 공간을 줄였습니다.%n",
                    formatBytes(fullIndexBytes),
                    fullIndexBytes,
                    formatBytes(partialIndexBytes),
                    partialIndexBytes,
                    formatBytes(fullIndexBytes - partialIndexBytes),
                    fullIndexBytes - partialIndexBytes,
                    reductionPercent
            );

            System.out.printf(
                    "BTREE_INDEX_SIZE rows[total=%d,softDeleted=%d,active=%d] "
                            + "bytes[full=%d,partial=%d,saved=%d] reductionPercent=%.2f%%%n",
                    totalRows,
                    softDeletedRows,
                    activeRows,
                    fullIndexBytes,
                    partialIndexBytes,
                    fullIndexBytes - partialIndexBytes,
                    reductionPercent
            );
        }
    }

    private String formatBytes(long bytes) {
        return "%,.2f MiB".formatted(bytes / 1024.0 / 1024.0);
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
}
