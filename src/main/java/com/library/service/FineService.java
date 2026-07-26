package com.library.service;

import com.library.data.FineRepository;
import com.library.domain.Fine;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class FineService {
    private final FineRepository fines;
    private final AuthorizationService authorization;
    private final AuditService audit;

    public FineService(
            FineRepository fines,
            AuthorizationService authorization,
            AuditService audit) {
        this.fines = fines;
        this.authorization = authorization;
        this.audit = audit;
    }

    public List<Fine> unpaidFinesFor(User actor, UUID memberId) throws SQLException {
        if (!actor.id().equals(memberId)) {
            authorization.require(actor.role(), Permission.MANAGE_LOANS);
        }
        return fines.findUnpaidByUser(memberId);
    }

    public BigDecimal unpaidBalance(User actor, UUID memberId) throws SQLException {
        return unpaidFinesFor(actor, memberId).stream()
                .map(Fine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void pay(User actor, UUID fineId) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_LOANS);
        fines.markPaid(fineId);
        audit.record(actor.id(), "PAY_FINE", "{\"fineId\":\"" + fineId + "\"}");
    }

    public void waive(User actor, UUID fineId) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_LOANS);
        fines.waive(fineId);
        audit.record(actor.id(), "WAIVE_FINE", "{\"fineId\":\"" + fineId + "\"}");
    }
}
