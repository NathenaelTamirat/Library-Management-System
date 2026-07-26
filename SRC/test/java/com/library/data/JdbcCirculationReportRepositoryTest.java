package com.library.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.library.domain.CirculationSummary;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcCirculationReportRepositoryTest {
    private JdbcCirculationReportRepository reports;

    @BeforeEach
    void createDatabase() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:circ-report;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS fines");
            statement.execute("DROP TABLE IF EXISTS loans");
            statement.execute("DROP TABLE IF EXISTS books");
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            statement.execute("""
                    CREATE TABLE books (
                        isbn VARCHAR(20) PRIMARY KEY,
                        total_copies INTEGER NOT NULL,
                        available_copies INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE loans (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL REFERENCES users(id),
                        isbn VARCHAR(20) NOT NULL REFERENCES books(isbn),
                        status VARCHAR(20) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE fines (
                        id UUID PRIMARY KEY,
                        loan_id UUID NOT NULL REFERENCES loans(id),
                        amount DECIMAL(12, 2) NOT NULL,
                        paid_status BOOLEAN NOT NULL,
                        waived BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);
            UUID userId = UUID.randomUUID();
            UUID openLoan = UUID.randomUUID();
            UUID overdueLoan = UUID.randomUUID();
            statement.execute("INSERT INTO users (id) VALUES ('" + userId + "')");
            statement.execute(
                    "INSERT INTO books VALUES ('9780134685991', 5, 2), ('9780321356680', 3, 0)");
            statement.execute("INSERT INTO loans VALUES ('" + openLoan + "', '" + userId
                    + "', '9780134685991', 'ACTIVE')");
            statement.execute("INSERT INTO loans VALUES ('" + overdueLoan + "', '" + userId
                    + "', '9780321356680', 'OVERDUE')");
            statement.execute("INSERT INTO fines VALUES ('" + UUID.randomUUID() + "', '"
                    + overdueLoan + "', 4.50, FALSE, FALSE)");
            statement.execute("INSERT INTO fines VALUES ('" + UUID.randomUUID() + "', '"
                    + openLoan + "', 1.00, TRUE, FALSE)");
        }
        reports = new JdbcCirculationReportRepository(dataSource);
    }

    @Test
    void summarizesOpenOverdueUnpaidAndInventory() throws Exception {
        CirculationSummary summary = reports.summarize();

        assertEquals(2, summary.openLoans());
        assertEquals(1, summary.overdueLoans());
        assertEquals(1, summary.unpaidFines());
        assertEquals(new BigDecimal("4.50"), summary.unpaidFineTotal());
        assertEquals(2, summary.availableCopies());
        assertEquals(8, summary.totalCopies());
    }
}
