package com.library.data;

import com.library.domain.FineEvent;
import com.library.domain.FineEventType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcFineEventRepository implements FineEventRepository {
    private static final String INSERT = """
            INSERT INTO fine_events (id, fine_id, actor_id, event_type, amount, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_FINE = """
            SELECT id, fine_id, actor_id, event_type, amount, occurred_at
            FROM fine_events
            WHERE fine_id = ?
            ORDER BY occurred_at ASC
            """;

    private final DataSource dataSource;

    public JdbcFineEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public FineEvent record(
            UUID fineId,
            UUID actorId,
            FineEventType type,
            BigDecimal amount,
            Instant occurredAt) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, id);
            statement.setObject(2, fineId);
            statement.setObject(3, actorId);
            statement.setString(4, type.name());
            statement.setBigDecimal(5, amount == null ? BigDecimal.ZERO : amount);
            statement.setTimestamp(6, Timestamp.from(occurredAt));
            statement.executeUpdate();
        }
        return new FineEvent(id, fineId, actorId, type, amount, occurredAt);
    }

    @Override
    public List<FineEvent> findByFineId(UUID fineId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_FINE)) {
            statement.setObject(1, fineId);
            try (ResultSet results = statement.executeQuery()) {
                List<FineEvent> events = new ArrayList<>();
                while (results.next()) {
                    events.add(new FineEvent(
                            results.getObject("id", UUID.class),
                            results.getObject("fine_id", UUID.class),
                            results.getObject("actor_id", UUID.class),
                            FineEventType.valueOf(results.getString("event_type")),
                            results.getBigDecimal("amount"),
                            results.getTimestamp("occurred_at").toInstant()));
                }
                return events;
            }
        }
    }
}
