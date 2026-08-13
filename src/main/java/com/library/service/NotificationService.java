package com.library.service;

import com.library.data.NotificationRepository;
import com.library.domain.Notification;
import com.library.domain.NotificationKind;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class NotificationService {
    private final NotificationRepository notifications;
    private final AuthorizationService authorization;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notifications,
            AuthorizationService authorization,
            Clock clock) {
        this.notifications = notifications;
        this.authorization = authorization;
        this.clock = clock;
    }

    public Notification enqueue(UUID userId, NotificationKind kind, String payload)
            throws SQLException {
        return notifications.enqueue(userId, kind, payload, clock.instant());
    }

    public List<Notification> pending(User actor, int limit) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        return notifications.findPending(limit);
    }

    public void markSent(User actor, UUID notificationId) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        notifications.markSent(notificationId, clock.instant());
    }
}
