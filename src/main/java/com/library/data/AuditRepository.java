package com.library.data;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public interface AuditRepository {
    void record(Optional<UUID> userId, String action, String details) throws SQLException;
}
