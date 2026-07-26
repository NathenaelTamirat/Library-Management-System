package com.library.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID userId,
        NotificationKind kind,
        String payload,
        Instant createdAt,
        Instant sentAt) {
    public Notification {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(payload);
        Objects.requireNonNull(createdAt);
    }

    public boolean pending() {
        return sentAt == null;
    }
}
