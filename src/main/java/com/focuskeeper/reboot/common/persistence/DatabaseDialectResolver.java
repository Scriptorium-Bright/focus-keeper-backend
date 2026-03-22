package com.focuskeeper.reboot.common.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class DatabaseDialectResolver {

    private final DataSource dataSource;
    private volatile Boolean postgreSql;

    public DatabaseDialectResolver(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isPostgreSql() {
        Boolean cached = postgreSql;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (postgreSql == null) {
                postgreSql = detectPostgreSql();
            }
            return postgreSql;
        }
    }

    private boolean detectPostgreSql() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to detect database dialect.", exception);
        }
    }
}
