package com.library.service;

import com.library.data.FineEventRepository;
import com.library.data.FineRepository;
import com.library.domain.Fine;
import com.library.domain.FineEvent;
import com.library.domain.FineEventType;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public final class FineService {
    private final FineRepository fines;
    private final FineEventRepository events;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Clock clock;

    public FineService(
            FineRepository fines,
            AuthorizationService authorization,
            AuditService audit) {
        this(fines, null, authorization, audit, Clock.systemUTC());
    }

    public FineService(
            FineRepository fines,
            FineEventRepository events,
            AuthorizationService authorization,
            AuditService audit,
            Clock clock) {
        this.fines = fines;
        this.events = events;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    public List<Fine> unpaidFinesFor(User actor, UUID memberId) throws SQLException {
        if (!actor.id().equals(memberId)) {
            authorization.require(actor, Permission.MANAGE_LOANS);
        }
        return fines.findUnpaidByUser(memberId);
    }

    public BigDecimal unpaidBalance(User actor, UUID memberId) throws SQLException {
        return unpaidFinesFor(actor, memberId).stream()
                .map(Fine::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<FineEvent> historyFor(User actor, UUID fineId) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        if (events == null) {
            return List.of();
        }
        return events.findByFineId(fineId);
    }

    public void pay(User actor, UUID fineId) throws SQLException {
        authorization.require(actor, Permission.MANAGE_FINES);
        fines.markPaid(fineId);
        recordEvent(fineId, actor.id(), FineEventType.PAY, BigDecimal.ZERO);
        audit.record(actor.id(), "PAY_FINE", "{\"fineId\":\"" + fineId + "\"}");
    }

    public void payPartial(User actor, UUID fineId, BigDecimal payment) throws SQLException {
        authorization.require(actor, Permission.MANAGE_FINES);
        if (payment == null || payment.signum() <= 0) {
            throw new IllegalArgumentException("Payment must be positive");
        }
        fines.payPartial(fineId, payment);
        recordEvent(fineId, actor.id(), FineEventType.PAY_PARTIAL, payment);
        audit.record(
                actor.id(),
                "PAY_FINE_PARTIAL",
                "{\"fineId\":\"" + fineId + "\",\"amount\":\"" + payment.toPlainString() + "\"}");
    }

    public void waive(User actor, UUID fineId) throws SQLException {
        authorization.require(actor, Permission.MANAGE_FINES);
        fines.waive(fineId);
        recordEvent(fineId, actor.id(), FineEventType.WAIVE, BigDecimal.ZERO);
        audit.record(actor.id(), "WAIVE_FINE", "{\"fineId\":\"" + fineId + "\"}");
    }

    private void recordEvent(
            UUID fineId, UUID actorId, FineEventType type, BigDecimal amount) throws SQLException {
        if (events == null) {
            return;
        }
        events.record(fineId, actorId, type, amount, clock.instant());
    }
}
