package com.library.service;

import com.library.data.HoldRepository;
import com.library.domain.Hold;
import com.library.domain.Member;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class HoldService {
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
        authorization.require(member.role(), Permission.BORROW_BOOK);
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
            authorization.require(actor.role(), Permission.MANAGE_LOANS);
        } else {
            authorization.require(actor.role(), Permission.BORROW_BOOK);
        }
        holds.cancel(holdId);
        audit.record(actor.id(), "CANCEL_HOLD", "{\"holdId\":\"" + holdId + "\"}");
    }

    public List<Hold> queueForIsbn(User actor, String isbn) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_LOANS);
        return holds.findActiveByIsbn(isbn);
    }

    public List<Hold> holdsFor(User actor, UUID memberId) throws SQLException {
        if (!actor.id().equals(memberId)) {
            authorization.require(actor.role(), Permission.MANAGE_LOANS);
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
}
