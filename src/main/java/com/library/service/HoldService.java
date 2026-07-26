package com.library.service;

import com.library.data.HoldRepository;
import com.library.domain.Hold;
import com.library.domain.HoldStatus;
import com.library.domain.Member;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class HoldService {
    public static final Duration READY_HOLD_DURATION = Duration.ofHours(48);

    private final HoldRepository holds;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Clock clock;

    public HoldService(
            HoldRepository holds,
            AuthorizationService authorization,
            AuditService audit,
            Clock clock) {
        this.holds = holds;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    public Hold place(Member member, String isbn) throws SQLException {
        authorization.require(member, Permission.BORROW_BOOK);
        if (!holds.bookExists(isbn)) {
            throw new IllegalStateException("Book does not exist: " + isbn);
        }
        if (holds.findActiveByUserAndIsbn(member.id(), isbn).isPresent()) {
            throw new IllegalStateException("Member already has an active hold for " + isbn);
        }
        Hold hold = holds.place(member.id(), isbn, clock.instant());
        audit.record(
                member.id(),
                "PLACE_HOLD",
                "{\"holdId\":\"" + hold.id() + "\",\"isbn\":\"" + isbn + "\"}");
        return hold;
    }

    public void cancel(User actor, UUID holdId) throws SQLException {
        Hold hold = holds.findById(holdId)
                .orElseThrow(() -> new IllegalStateException("Hold not found: " + holdId));
        if (!actor.id().equals(hold.userId())) {
            authorization.require(actor, Permission.MANAGE_LOANS);
        } else {
            authorization.require(actor, Permission.BORROW_BOOK);
        }
        holds.cancel(holdId);
        audit.record(actor.id(), "CANCEL_HOLD", "{\"holdId\":\"" + holdId + "\"}");
    }

    public List<Hold> queueForIsbn(User actor, String isbn) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        return holds.findActiveByIsbn(isbn);
    }

    public List<Hold> holdsFor(User actor, UUID memberId) throws SQLException {
        if (!actor.id().equals(memberId)) {
            authorization.require(actor, Permission.MANAGE_LOANS);
        }
        return holds.findActiveByUser(memberId);
    }

    public Optional<Hold> firstActive(String isbn) throws SQLException {
        return holds.findFirstActiveByIsbn(isbn);
    }

    public void fulfillIfOwned(UUID userId, String isbn) throws SQLException {
        Optional<Hold> hold = holds.findActiveByUserAndIsbn(userId, isbn);
        if (hold.isPresent()) {
            holds.fulfill(hold.orElseThrow().id());
        }
    }

    public Optional<Hold> readyNextForIsbn(String isbn) throws SQLException {
        Optional<Hold> first = holds.findFirstActiveByIsbn(isbn);
        if (first.isEmpty() || first.orElseThrow().status() != HoldStatus.WAITING) {
            return Optional.empty();
        }
        Hold hold = first.orElseThrow();
        var expiresAt = clock.instant().plus(READY_HOLD_DURATION);
        holds.markReady(hold.id(), expiresAt);
        Hold ready = holds.findById(hold.id()).orElse(hold);
        if (ready.status() == HoldStatus.WAITING) {
            ready.markReady(expiresAt);
        }
        audit.record(
                ready.userId(),
                "HOLD_READY",
                "{\"holdId\":\"" + ready.id() + "\",\"isbn\":\"" + isbn + "\"}");
        return Optional.of(ready);
    }

    public int expireStale(User actor) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        int expired = holds.expireReadyBefore(clock.instant());
        audit.record(actor.id(), "EXPIRE_HOLDS", "{\"expired\":" + expired + "}");
        return expired;
    }
}
