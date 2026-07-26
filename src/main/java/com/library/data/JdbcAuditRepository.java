package com.library.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcAuditRepository implements AuditRepository {
    private static final String INSERT = """
            INSERT INTO audit_log (user_id, action, details)
            VALUES (?, ?, ?::jsonb)
            """;
    private static final String INSERT_H2 = """
            INSERT INTO audit_log (user_id, action, details)
            VALUES (?, ?, ?)
            """;

    private final DataSource dataSource;
    private final String insertSql;

    public JdbcAuditRepository(DataSource dataSource) {
        this(dataSource, false);
    }

    public JdbcAuditRepository(DataSource dataSource, boolean postgresJsonCast) {
        this.dataSource = dataSource;
        this.insertSql = postgresJsonCast ? INSERT : INSERT_H2;
    }

    @Override
    public void record(Optional<UUID> userId, String action, String details) throws SQLException {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Audit action is required");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(insertSql)) {
            if (userId.isPresent()) {
                statement.setObject(1, userId.orElseThrow());
            } else {
                statement.setObject(1, null);
            }
            statement.setString(2, action.strip());
            statement.setString(3, details == null || details.isBlank() ? "{}" : details);
            statement.executeUpdate();
        }
    }
}
