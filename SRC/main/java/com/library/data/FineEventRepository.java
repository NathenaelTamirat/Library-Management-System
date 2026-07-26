package com.library.data;

import com.library.domain.FineEvent;
import com.library.domain.FineEventType;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FineEventRepository {
    FineEvent record(
            UUID fineId,
            UUID actorId,
            FineEventType type,
            BigDecimal amount,
            Instant occurredAt) throws SQLException;

    List<FineEvent> findByFineId(UUID fineId) throws SQLException;
}
