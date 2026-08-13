package com.library.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.library.domain.FineEvent;
import com.library.domain.FineEventType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcFineEventRepositoryTest {
    private JdbcFineEventRepository events;
    private UUID actorId;
    private UUID fineId;

    @BeforeEach
    void createDatabase() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:fine-events;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        actorId = UUID.randomUUID();
        fineId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS fine_events");
            statement.execute("DROP TABLE IF EXISTS fines");
            statement.execute("DROP TABLE IF EXISTS loans");
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            statement.execute("CREATE TABLE loans (id UUID PRIMARY KEY, user_id UUID NOT NULL)");
            statement.execute("""
                    CREATE TABLE fines (
                        id UUID PRIMARY KEY,
                        loan_id UUID NOT NULL,
                        amount DECIMAL(12, 2) NOT NULL,
                        paid_status BOOLEAN NOT NULL,
                        waived BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE fine_events (
                        id UUID PRIMARY KEY,
                        fine_id UUID NOT NULL REFERENCES fines(id),
                        actor_id UUID NOT NULL REFERENCES users(id),
                        event_type VARCHAR(20) NOT NULL,
                        amount DECIMAL(12, 2) NOT NULL,
                        occurred_at TIMESTAMP NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO users (id) VALUES ('" + actorId + "')");
            statement.execute("INSERT INTO users (id) VALUES ('" + userId + "')");
            statement.execute("INSERT INTO loans (id, user_id) VALUES ('" + loanId + "', '" + userId + "')");
            statement.execute("INSERT INTO fines (id, loan_id, amount, paid_status) VALUES ('"
                    + fineId + "', '" + loanId + "', 5.00, FALSE)");
        }
        events = new JdbcFineEventRepository(dataSource);
    }

    @Test
    void recordsAndListsFineEventsInOrder() throws Exception {
        Instant first = Instant.parse("2026-07-26T10:00:00Z");
        Instant second = Instant.parse("2026-07-26T11:00:00Z");
        events.record(fineId, actorId, FineEventType.PAY_PARTIAL, new BigDecimal("2.00"), first);
        events.record(fineId, actorId, FineEventType.WAIVE, BigDecimal.ZERO, second);

        List<FineEvent> history = events.findByFineId(fineId);
        assertEquals(2, history.size());
        assertEquals(FineEventType.PAY_PARTIAL, history.get(0).type());
        assertEquals(FineEventType.WAIVE, history.get(1).type());
    }
}
