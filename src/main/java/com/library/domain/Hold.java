package com.library.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Hold {
    private final UUID id;
    private final UUID userId;
    private final String isbn;
    private final Instant placedAt;
    private HoldStatus status;
    private Instant expiresAt;

    public Hold(UUID id, UUID userId, String isbn, Instant placedAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.isbn = Objects.requireNonNull(isbn);
        this.placedAt = Objects.requireNonNull(placedAt);
        this.status = HoldStatus.WAITING;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String isbn() {
        return isbn;
    }

    public Instant placedAt() {
        return placedAt;
    }

    public HoldStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public void cancel() {
        if (status == HoldStatus.FULFILLED || status == HoldStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel a " + status + " hold");
        }
        status = HoldStatus.CANCELLED;
    }

    public void markReady(Instant expiresAt) {
        if (status != HoldStatus.WAITING) {
            throw new IllegalStateException("Only waiting holds can become ready");
        }
        status = HoldStatus.READY;
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public void fulfill() {
        if (status != HoldStatus.WAITING && status != HoldStatus.READY) {
            throw new IllegalStateException("Cannot fulfill a " + status + " hold");
        }
        status = HoldStatus.FULFILLED;
    }

    public void expire() {
        if (status != HoldStatus.READY && status != HoldStatus.WAITING) {
            throw new IllegalStateException("Cannot expire a " + status + " hold");
        }
        status = HoldStatus.EXPIRED;
    }

    public void restore(HoldStatus status, Instant expiresAt) {
        this.status = Objects.requireNonNull(status);
        this.expiresAt = expiresAt;
    }
}
