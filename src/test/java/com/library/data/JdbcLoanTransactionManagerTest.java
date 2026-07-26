package com.library.data;

import static org.junit.jupiter.api.Assertions.*;

import com.library.domain.Loan;
import com.library.domain.LoanStatus;
import com.library.domain.ReturnReceipt;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcLoanTransactionManagerTest {
    private JdbcDataSource dataSource;
    private JdbcLoanTransactionManager transactions;
    private ExecutorService executor;
    private UUID userId;

    @BeforeEach
    void createDatabase() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:loans;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        userId = UUID.randomUUID();
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
                        available_copies INTEGER NOT NULL CHECK (available_copies >= 0)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE loans (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL REFERENCES users(id),
                        isbn VARCHAR(20) NOT NULL REFERENCES books(isbn),
                        checkout_date DATE NOT NULL,
                        due_date DATE NOT NULL,
                        return_date DATE,
                        status VARCHAR(20) NOT NULL,
                        renewal_count INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("""
                    CREATE TABLE fines (
                        id UUID PRIMARY KEY,
                        loan_id UUID NOT NULL UNIQUE REFERENCES loans(id),
                        amount DECIMAL(12, 2) NOT NULL,
                        paid_status BOOLEAN NOT NULL,
                        issued_date DATE NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO users (id) VALUES ('" + userId + "')");
            statement.execute(
                    "INSERT INTO books (isbn, total_copies, available_copies) VALUES ('9780134685991', 1, 1)");
        }
        transactions = new JdbcLoanTransactionManager(dataSource);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void rowLockAllowsOnlyOneConcurrentCheckoutOfLastCopy() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> checkout = () -> {
            start.await();
            try {
                transactions.checkout(
                        userId,
                        "9780134685991",
                        LocalDate.of(2026, 7, 26),
                        LocalDate.of(2026, 8, 9),
                        5);
                return true;
            } catch (SQLException unavailable) {
                return false;
            }
        };
        List<Future<Boolean>> attempts = List.of(
                executor.submit(checkout),
                executor.submit(checkout));

        start.countDown();

        int successes = (attempts.get(0).get() ? 1 : 0) + (attempts.get(1).get() ? 1 : 0);
        assertEquals(1, successes);
        assertEquals(1, scalar("SELECT COUNT(*) FROM loans"));
        assertEquals(0, scalar("SELECT available_copies FROM books"));
    }

    @Test
    void failureAfterLoanInsertRollsBackTheWholeTransaction() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE books ADD CONSTRAINT inventory_must_remain_positive
                    CHECK (available_copies > 0)
                    """);
        }

        assertThrows(SQLException.class, () -> transactions.checkout(
                userId,
                "9780134685991",
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 8, 9),
                5));

        assertEquals(0, scalar("SELECT COUNT(*) FROM loans"));
        assertEquals(1, scalar("SELECT available_copies FROM books"));
    }

    @Test
    void checkoutRejectsWhenDatabaseAlreadyAtBorrowingLimit() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
                    statement.execute("INSERT INTO books (isbn, total_copies, available_copies) VALUES ('9780321356680', 2, 2)");
        }
        transactions.checkout(
                userId,
                "9780134685991",
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 8, 9),
                1);

        SQLException failure = assertThrows(SQLException.class, () -> transactions.checkout(
                userId,
                "9780321356680",
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 8, 9),
                1));

        assertTrue(failure.getMessage().contains("Borrowing limit reached"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM loans"));
        assertEquals(2, scalar("SELECT available_copies FROM books WHERE isbn = '9780321356680'"));
    }

    @Test
    void returnRestoresInventoryAndCreatesFineWhenOverdue() throws Exception {
        Loan loan = transactions.checkout(
                userId,
                "9780134685991",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 10),
                5);
        assertEquals(0, scalar("SELECT available_copies FROM books"));

        ReturnReceipt receipt = transactions.returnLoan(loan.id(), LocalDate.of(2026, 7, 16));

        assertEquals(LoanStatus.RETURNED, receipt.loan().status());
        assertEquals(1, scalar("SELECT available_copies FROM books"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM fines"));
        assertEquals(new BigDecimal("3.00"), receipt.fine().orElseThrow().amount());
        assertTrue(transactions.findActiveLoanByIsbn("9780134685991").isEmpty());
    }

    @Test
    void markLostCreatesReplacementFineAndDecrementsTotalCopies() throws Exception {
        Loan loan = transactions.checkout(
                userId,
                "9780134685991",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 15),
                5);
        assertEquals(0, scalar("SELECT available_copies FROM books"));
        assertEquals(1, scalar("SELECT total_copies FROM books"));

        Loan lost = transactions.markLost(
                loan.id(), new BigDecimal("50.00"), LocalDate.of(2026, 7, 26));

        assertEquals(LoanStatus.LOST, lost.status());
        assertEquals(0, scalar("SELECT available_copies FROM books"));
        assertEquals(0, scalar("SELECT total_copies FROM books"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM fines"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM loans WHERE status IN ('ACTIVE', 'OVERDUE')"));
    }

    @Test
    void markOverdueBeforeFlipsPastDueActiveLoans() throws Exception {
        transactions.checkout(
                userId,
                "9780134685991",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 10),
                5);

        assertEquals(1, transactions.markOverdueBefore(LocalDate.of(2026, 7, 26)));
        assertEquals(1, scalar("SELECT COUNT(*) FROM loans WHERE status = 'OVERDUE'"));
        assertEquals(0, transactions.markOverdueBefore(LocalDate.of(2026, 7, 26)));
    }

    @Test
    void renewExtendsDueDateUnderRowLock() throws Exception {
        Loan loan = transactions.checkout(
                userId,
                "9780134685991",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 15),
                5);

        Loan renewed = transactions.renew(loan.id(), LocalDate.of(2026, 7, 29));

        assertEquals(LocalDate.of(2026, 7, 29), renewed.dueDate());
        assertEquals(1, renewed.renewalCount());
        assertEquals(1, scalar("SELECT renewal_count FROM loans"));
        assertEquals(
                LocalDate.of(2026, 7, 29),
                transactions.findById(loan.id()).orElseThrow().dueDate());
    }

    @Test
    void onTimeReturnDoesNotCreateAFine() throws Exception {
        Loan loan = transactions.checkout(
                userId,
                "9780134685991",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 20),
                5);

        ReturnReceipt receipt = transactions.returnLoan(loan.id(), LocalDate.of(2026, 7, 15));

        assertTrue(receipt.fine().isEmpty());
        assertEquals(0, scalar("SELECT COUNT(*) FROM fines"));
        assertEquals(1, scalar("SELECT available_copies FROM books"));
    }

    private int scalar(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(sql)) {
            results.next();
            return results.getInt(1);
        }
    }
}
