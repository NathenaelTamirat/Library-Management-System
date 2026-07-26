package com.library.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record FineEvent(
        UUID id,
        UUID fineId,
        UUID actorId,
        FineEventType type,
        BigDecimal amount,
        Instant occurredAt) {
    public FineEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(fineId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(occurredAt);
        amount = Optional.ofNullable(amount).orElse(BigDecimal.ZERO);
    }
}
