package com.library.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record AuditEntry(
        long id,
        Optional<UUID> userId,
        String action,
        Instant occurredAt,
        String details) {
}
