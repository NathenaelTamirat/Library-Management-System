package com.library.data;

import com.library.domain.AuditEntry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private static final String FIND_RECENT = """
            SELECT id, user_id, action, occurred_at, details
            FROM audit_log
            ORDER BY occurred_at DESC, id DESC
            LIMIT ?
            """;
    private static final String FIND_BY_ACTION = """
            SELECT id, user_id, action, occurred_at, details
            FROM audit_log
            WHERE action = ?
            ORDER BY occurred_at DESC, id DESC
            LIMIT ?
            """;
    private static final String FIND_BY_USER = """
            SELECT id, user_id, action, occurred_at, details
            FROM audit_log
            WHERE user_id = ?
            ORDER BY occurred_at DESC, id DESC
            LIMIT ?
            """;
    private static final String FIND_BETWEEN = """
            SELECT id, user_id, action, occurred_at, details
            FROM audit_log
            WHERE occurred_at BETWEEN ? AND ?
            ORDER BY occurred_at DESC, id DESC
            LIMIT ?
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

    @Override
    public List<AuditEntry> findRecent(int limit) throws SQLException {
        return query(FIND_RECENT, null, null, limit);
    }

    @Override
    public List<AuditEntry> findByAction(String action, int limit) throws SQLException {
        return query(FIND_BY_ACTION, action, null, limit);
    }

    @Override
    public List<AuditEntry> findByUser(UUID userId, int limit) throws SQLException {
        return query(FIND_BY_USER, null, userId, limit);
    }

    @Override
    public List<AuditEntry> findBetween(Instant from, Instant to, int limit) throws SQLException {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        validateLimit(limit);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BETWEEN)) {
            statement.setTimestamp(1, Timestamp.from(from));
            statement.setTimestamp(2, Timestamp.from(to));
            statement.setInt(3, limit);
            try (ResultSet results = statement.executeQuery()) {
                return mapAll(results);
            }
        }
    }

    private List<AuditEntry> query(String sql, String action, UUID userId, int limit)
            throws SQLException {
        validateLimit(limit);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (action != null) {
                statement.setString(index++, action);
            }
            if (userId != null) {
                statement.setObject(index++, userId);
            }
            statement.setInt(index, limit);
            try (ResultSet results = statement.executeQuery()) {
                return mapAll(results);
            }
        }
    }

    private static void validateLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
    }

    private static List<AuditEntry> mapAll(ResultSet results) throws SQLException {
        List<AuditEntry> entries = new ArrayList<>();
        while (results.next()) {
            entries.add(map(results));
        }
        return entries;
    }

    private static AuditEntry map(ResultSet results) throws SQLException {
        UUID userId = results.getObject("user_id", UUID.class);
        Timestamp occurred = results.getTimestamp("occurred_at");
        return new AuditEntry(
                results.getLong("id"),
                Optional.ofNullable(userId),
                results.getString("action"),
                occurred.toInstant(),
                results.getString("details"));
    }
}
