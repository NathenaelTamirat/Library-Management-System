package com.library.data;

import com.library.domain.Notification;
import com.library.domain.NotificationKind;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository {
    Notification enqueue(
            UUID userId, NotificationKind kind, String payload, Instant createdAt)
            throws SQLException;

    List<Notification> findPending(int limit) throws SQLException;

    void markSent(UUID notificationId, Instant sentAt) throws SQLException;
}
