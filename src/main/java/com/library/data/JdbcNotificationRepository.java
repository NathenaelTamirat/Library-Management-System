package com.library.data;

import com.library.domain.Notification;
import com.library.domain.NotificationKind;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcNotificationRepository implements NotificationRepository {
    private static final String INSERT = """
            INSERT INTO notifications (id, user_id, kind, payload, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String FIND_PENDING = """
            SELECT id, user_id, kind, payload, created_at, sent_at
            FROM notifications
            WHERE sent_at IS NULL
            ORDER BY created_at ASC
            LIMIT ?
            """;
    private static final String MARK_SENT = """
            UPDATE notifications SET sent_at = ? WHERE id = ? AND sent_at IS NULL
            """;

    private final DataSource dataSource;

    public JdbcNotificationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Notification enqueue(
            UUID userId, NotificationKind kind, String payload, Instant createdAt)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setString(3, kind.name());
            statement.setString(4, payload);
            statement.setTimestamp(5, Timestamp.from(createdAt));
            statement.executeUpdate();
        }
        return new Notification(id, userId, kind, payload, createdAt, null);
    }

    @Override
    public List<Notification> findPending(int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_PENDING)) {
            statement.setInt(1, limit);
            try (ResultSet results = statement.executeQuery()) {
                List<Notification> notifications = new ArrayList<>();
                while (results.next()) {
                    Timestamp sent = results.getTimestamp("sent_at");
                    notifications.add(new Notification(
                            results.getObject("id", UUID.class),
                            results.getObject("user_id", UUID.class),
                            NotificationKind.valueOf(results.getString("kind")),
                            results.getString("payload"),
                            results.getTimestamp("created_at").toInstant(),
                            sent == null ? null : sent.toInstant()));
                }
                return notifications;
            }
        }
    }

    @Override
    public void markSent(UUID notificationId, Instant sentAt) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(MARK_SENT)) {
            statement.setTimestamp(1, Timestamp.from(sentAt));
            statement.setObject(2, notificationId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Notification not found or already sent: " + notificationId);
            }
        }
    }
}
