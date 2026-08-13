package com.library.data;

import static org.junit.jupiter.api.Assertions.*;

import com.library.domain.Hold;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcHoldRepositoryTest {
    private JdbcDataSource dataSource;
    private JdbcHoldRepository holds;
    private UUID firstUser;
    private UUID secondUser;

    @BeforeEach
    void createDatabase() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:holds;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        firstUser = UUID.randomUUID();
        secondUser = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS holds");
            statement.execute("DROP TABLE IF EXISTS books");
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            statement.execute("""
                    CREATE TABLE books (
                        isbn VARCHAR(20) PRIMARY KEY,
                        available_copies INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE holds (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL REFERENCES users(id),
                        isbn VARCHAR(20) NOT NULL REFERENCES books(isbn),
                        status VARCHAR(20) NOT NULL,
                        placed_at TIMESTAMP NOT NULL,
                        expires_at TIMESTAMP
                    )
                    """);
            statement.execute("INSERT INTO users (id) VALUES ('" + firstUser + "')");
            statement.execute("INSERT INTO users (id) VALUES ('" + secondUser + "')");
            statement.execute(
                    "INSERT INTO books (isbn, available_copies) VALUES ('9780134685991', 0)");
        }
        holds = new JdbcHoldRepository(dataSource);
    }

    @Test
    void placesFifoQueueAndPreventsDuplicateActiveHold() throws Exception {
        Instant first = Instant.parse("2026-07-26T10:00:00Z");
        Instant second = Instant.parse("2026-07-26T11:00:00Z");
        Hold head = holds.place(firstUser, "9780134685991", first);
        holds.place(secondUser, "9780134685991", second);

        assertEquals(firstUser, holds.findFirstActiveByIsbn("9780134685991").orElseThrow().userId());
        assertEquals(2, holds.findActiveByIsbn("9780134685991").size());
        assertThrows(Exception.class, () -> holds.place(firstUser, "9780134685991", second));

        holds.cancel(head.id());
        assertEquals(secondUser, holds.findFirstActiveByIsbn("9780134685991").orElseThrow().userId());
    }

    @Test
    void expireReadyBeforeMarksStaleReadyHoldsExpired() throws Exception {
        Hold hold = holds.place(firstUser, "9780134685991", Instant.parse("2026-07-20T10:00:00Z"));
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE holds SET status = 'READY', expires_at = TIMESTAMP '2026-07-25 10:00:00'"
                            + " WHERE id = '" + hold.id() + "'");
        }

        assertEquals(1, holds.expireReadyBefore(Instant.parse("2026-07-26T12:00:00Z")));
        assertEquals("EXPIRED", holds.findById(hold.id()).orElseThrow().status().name());
        assertEquals(0, holds.expireReadyBefore(Instant.parse("2026-07-26T12:00:00Z")));
    }
}
