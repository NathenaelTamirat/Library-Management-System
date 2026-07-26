package com.library.service;

import com.library.data.AuditRepository;
import com.library.domain.AuditEntry;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AuditService {
    private final AuditRepository audits;
    private final AuthorizationService authorization;

    public AuditService(AuditRepository audits) {
        this(audits, null);
    }

    public AuditService(AuditRepository audits, AuthorizationService authorization) {
        this.audits = audits;
        this.authorization = authorization;
    }

    public void record(UUID userId, String action, String details) {
        try {
            audits.record(Optional.ofNullable(userId), action, details);
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to write audit log", failure);
        }
    }

    public List<AuditEntry> recent(User actor, int limit) throws SQLException {
        requireView(actor);
        return audits.findRecent(limit);
    }

    public List<AuditEntry> byAction(User actor, String action, int limit) throws SQLException {
        requireView(actor);
        return audits.findByAction(action, limit);
    }

    public List<AuditEntry> byUser(User actor, UUID userId, int limit) throws SQLException {
        requireView(actor);
        return audits.findByUser(userId, limit);
    }

    private void requireView(User actor) {
        if (authorization == null) {
            throw new IllegalStateException("Audit queries require an AuthorizationService");
        }
        authorization.require(actor.role(), Permission.VIEW_AUDIT_LOG);
    }
}
