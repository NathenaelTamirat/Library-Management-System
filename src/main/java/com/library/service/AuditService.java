package com.library.service;

import com.library.data.AuditRepository;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class AuditService {
    private final AuditRepository audits;

    public AuditService(AuditRepository audits) {
        this.audits = audits;
    }

    public void record(UUID userId, String action, String details) {
        try {
            audits.record(Optional.ofNullable(userId), action, details);
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to write audit log", failure);
        }
    }
}
