package com.library.data;

import com.library.domain.AuditEntry;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditRepository {
    void record(Optional<UUID> userId, String action, String details) throws SQLException;

    List<AuditEntry> findRecent(int limit) throws SQLException;

    List<AuditEntry> findByAction(String action, int limit) throws SQLException;

    List<AuditEntry> findByUser(UUID userId, int limit) throws SQLException;
}
