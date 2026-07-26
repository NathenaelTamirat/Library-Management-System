package com.library.data;

import com.library.domain.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcUserAdminRepository implements UserAdminRepository {
    private static final String INSERT = """
            INSERT INTO users (id, name, email, password_hash, role, is_active, failed_login_attempts)
            VALUES (?, ?, ?, ?, ?, TRUE, 0)
            """;
    private static final String SET_ACTIVE = """
            UPDATE users SET is_active = ? WHERE id = ?
            """;
    private static final String CHANGE_ROLE = """
            UPDATE users SET role = ? WHERE id = ?
            """;
    private static final String UPDATE_PASSWORD = """
            UPDATE users SET password_hash = ? WHERE id = ?
            """;
    private static final String RECORD_FAILURE = """
            UPDATE users SET failed_login_attempts = ?, locked_until = ? WHERE id = ?
            """;
    private static final String CLEAR_FAILURES = """
            UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE id = ?
            """;
    private static final String FIND_BY_EMAIL = """
            SELECT id, name, email, password_hash, role, is_active, failed_login_attempts, locked_until
            FROM users
            WHERE email = ?
            """;
    private static final String FIND_BY_ID = """
            SELECT id, name, email, password_hash, role, is_active, failed_login_attempts, locked_until
            FROM users
            WHERE id = ?
            """;
    private static final String LIST = """
            SELECT id, name, email, password_hash, role, is_active, failed_login_attempts, locked_until
            FROM users
            ORDER BY email
            """;

    private final DataSource dataSource;

    public JdbcUserAdminRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UUID create(String name, String email, String passwordHash, Role role) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, id);
            statement.setString(2, name);
            statement.setString(3, email);
            statement.setString(4, passwordHash);
            statement.setString(5, role.name());
            statement.executeUpdate();
            return id;
        }
    }

    @Override
    public void setActive(UUID userId, boolean active) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SET_ACTIVE)) {
            statement.setBoolean(1, active);
            statement.setObject(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("User not found: " + userId);
            }
        }
    }

    @Override
    public void changeRole(UUID userId, Role role) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(CHANGE_ROLE)) {
            statement.setString(1, role.name());
            statement.setObject(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("User not found: " + userId);
            }
        }
    }

    @Override
    public void updatePasswordHash(UUID userId, String passwordHash) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PASSWORD)) {
            statement.setString(1, passwordHash);
            statement.setObject(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("User not found: " + userId);
            }
        }
    }

    @Override
    public void recordFailedLogin(UUID userId, int attempts, Instant lockedUntil) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(RECORD_FAILURE)) {
            statement.setInt(1, attempts);
            if (lockedUntil == null) {
                statement.setNull(2, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(2, Timestamp.from(lockedUntil));
            }
            statement.setObject(3, userId);
            statement.executeUpdate();
        }
    }

    @Override
    public void clearFailedLogins(UUID userId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(CLEAR_FAILURES)) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<UserRecord> findRecordByEmail(String email) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_EMAIL)) {
            statement.setString(1, email);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<UserRecord> findRecordById(UUID userId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setObject(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    @Override
    public List<UserRecord> listUsers() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LIST);
             ResultSet results = statement.executeQuery()) {
            List<UserRecord> users = new ArrayList<>();
            while (results.next()) {
                users.add(map(results));
            }
            return users;
        }
    }

    private static UserRecord map(ResultSet results) throws SQLException {
        Timestamp locked = results.getTimestamp("locked_until");
        return new UserRecord(
                results.getObject("id", UUID.class),
                results.getString("name"),
                results.getString("email"),
                results.getString("password_hash"),
                Role.valueOf(results.getString("role")),
                results.getBoolean("is_active"),
                results.getInt("failed_login_attempts"),
                locked == null ? null : locked.toInstant());
    }
}
