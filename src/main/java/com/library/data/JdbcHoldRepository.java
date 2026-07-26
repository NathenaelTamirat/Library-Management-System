package com.library.data;

import com.library.domain.Hold;
import com.library.domain.HoldStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcHoldRepository implements HoldRepository {
    private static final String INSERT = """
            INSERT INTO holds (id, user_id, isbn, status, placed_at)
            VALUES (?, ?, ?, 'WAITING', ?)
            """;
    private static final String CANCEL = """
            UPDATE holds SET status = 'CANCELLED'
            WHERE id = ? AND status IN ('WAITING', 'READY')
            """;
    private static final String FULFILL = """
            UPDATE holds SET status = 'FULFILLED'
            WHERE id = ? AND status IN ('WAITING', 'READY')
            """;
    private static final String EXPIRE_READY = """
            UPDATE holds
            SET status = 'EXPIRED'
            WHERE status = 'READY'
              AND expires_at IS NOT NULL
              AND expires_at <= ?
            """;
    private static final String FIND_BY_ID = """
            SELECT id, user_id, isbn, status, placed_at, expires_at
            FROM holds WHERE id = ?
            """;
    private static final String FIND_ACTIVE_BY_USER_ISBN = """
            SELECT id, user_id, isbn, status, placed_at, expires_at
            FROM holds
            WHERE user_id = ? AND isbn = ? AND status IN ('WAITING', 'READY')
            """;
    private static final String FIND_FIRST_ACTIVE = """
            SELECT id, user_id, isbn, status, placed_at, expires_at
            FROM holds
            WHERE isbn = ? AND status IN ('WAITING', 'READY')
            ORDER BY placed_at ASC
            LIMIT 1
            """;
    private static final String FIND_ACTIVE_BY_ISBN = """
            SELECT id, user_id, isbn, status, placed_at, expires_at
            FROM holds
            WHERE isbn = ? AND status IN ('WAITING', 'READY')
            ORDER BY placed_at ASC
            """;
    private static final String FIND_ACTIVE_BY_USER = """
            SELECT id, user_id, isbn, status, placed_at, expires_at
            FROM holds
            WHERE user_id = ? AND status IN ('WAITING', 'READY')
            ORDER BY placed_at ASC
            """;
    private static final String BOOK_EXISTS = "SELECT 1 FROM books WHERE isbn = ?";

    private final DataSource dataSource;

    public JdbcHoldRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Hold place(UUID userId, String isbn, Instant placedAt) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            if (findActiveByUserAndIsbn(connection, userId, isbn).isPresent()) {
                throw new SQLException("Active hold already exists for user and ISBN");
            }
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                statement.setObject(1, id);
                statement.setObject(2, userId);
                statement.setString(3, isbn);
                statement.setTimestamp(4, Timestamp.from(placedAt));
                statement.executeUpdate();
            }
        }
        return new Hold(id, userId, isbn, placedAt);
    }

    @Override
    public void cancel(UUID holdId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(CANCEL)) {
            statement.setObject(1, holdId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Hold could not be cancelled: " + holdId);
            }
        }
    }

    @Override
    public void fulfill(UUID holdId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FULFILL)) {
            statement.setObject(1, holdId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Hold could not be fulfilled: " + holdId);
            }
        }
    }

    @Override
    public int expireReadyBefore(Instant asOf) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXPIRE_READY)) {
            statement.setTimestamp(1, Timestamp.from(asOf));
            return statement.executeUpdate();
        }
    }

    @Override
    public Optional<Hold> findById(UUID holdId) throws SQLException {
        return queryOne(FIND_BY_ID, holdId, null);
    }

    @Override
    public Optional<Hold> findActiveByUserAndIsbn(UUID userId, String isbn) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return findActiveByUserAndIsbn(connection, userId, isbn);
        }
    }

    private static Optional<Hold> findActiveByUserAndIsbn(
            Connection connection, UUID userId, String isbn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_BY_USER_ISBN)) {
            statement.setObject(1, userId);
            statement.setString(2, isbn);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Hold> findFirstActiveByIsbn(String isbn) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_FIRST_ACTIVE)) {
            statement.setString(1, isbn);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Hold> findActiveByIsbn(String isbn) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_BY_ISBN)) {
            statement.setString(1, isbn);
            return readAll(statement);
        }
    }

    @Override
    public List<Hold> findActiveByUser(UUID userId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_BY_USER)) {
            statement.setObject(1, userId);
            return readAll(statement);
        }
    }

    @Override
    public boolean bookExists(String isbn) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(BOOK_EXISTS)) {
            statement.setString(1, isbn);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private Optional<Hold> queryOne(String sql, UUID id, String ignored) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    private static List<Hold> readAll(PreparedStatement statement) throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            List<Hold> holds = new ArrayList<>();
            while (results.next()) {
                holds.add(map(results));
            }
            return holds;
        }
    }

    private static Hold map(ResultSet results) throws SQLException {
        Hold hold = new Hold(
                results.getObject("id", UUID.class),
                results.getObject("user_id", UUID.class),
                results.getString("isbn"),
                results.getTimestamp("placed_at").toInstant());
        Timestamp expires = results.getTimestamp("expires_at");
        hold.restore(
                HoldStatus.valueOf(results.getString("status")),
                expires == null ? null : expires.toInstant());
        return hold;
    }
}
