package com.library.data;

import static org.junit.jupiter.api.Assertions.*;

import com.library.domain.Fine;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcFineRepositoryTest {
    private JdbcDataSource dataSource;
    private JdbcFineRepository fines;
    private UUID userId;
    private UUID loanId;
    private UUID fineId;

    @BeforeEach
    void createDatabase() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:fines;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        userId = UUID.randomUUID();
        loanId = UUID.randomUUID();
        fineId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS fines");
            statement.execute("DROP TABLE IF EXISTS loans");
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            statement.execute("""
                    CREATE TABLE loans (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL REFERENCES users(id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE fines (
                        id UUID PRIMARY KEY,
                        loan_id UUID NOT NULL UNIQUE REFERENCES loans(id),
                        amount DECIMAL(12, 2) NOT NULL,
                        paid_status BOOLEAN NOT NULL,
                        waived BOOLEAN NOT NULL DEFAULT FALSE,
                        issued_date DATE NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO users (id) VALUES ('" + userId + "')");
            statement.execute("INSERT INTO loans (id, user_id) VALUES ('" + loanId + "', '" + userId + "')");
            statement.execute("""
                    INSERT INTO fines (id, loan_id, amount, paid_status, issued_date)
                    VALUES ('%s', '%s', 4.50, FALSE, DATE '2026-07-20')
                    """.formatted(fineId, loanId));
        }
        fines = new JdbcFineRepository(dataSource);
    }

    @Test
    void findsUnpaidFinesAndMarksThemPaid() throws Exception {
        List<Fine> unpaid = fines.findUnpaidByUser(userId);
        assertEquals(1, unpaid.size());
        assertEquals(new BigDecimal("4.50"), unpaid.get(0).amount());
        assertFalse(unpaid.get(0).paid());

        fines.markPaid(fineId);

        assertTrue(fines.findUnpaidByUser(userId).isEmpty());
        Fine paid = fines.findByLoanId(loanId).orElseThrow();
        assertTrue(paid.paid());
        assertEquals(LocalDate.of(2026, 7, 20), paid.issuedDate());
    }

    @Test
    void waiveRemovesFineFromUnpaidBalance() throws Exception {
        fines.waive(fineId);
        assertTrue(fines.findUnpaidByUser(userId).isEmpty());
    }
}
