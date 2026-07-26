package com.library.data;

import com.library.domain.Fine;
import com.library.domain.Loan;
import com.library.domain.LoanStatus;
import com.library.domain.ReturnReceipt;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcLoanTransactionManager implements LoanTransactionManager {
    private static final String LOCK_BOOK = """
            SELECT available_copies
            FROM books
            WHERE isbn = ?
            FOR UPDATE
            """;
    private static final String INSERT_LOAN = """
            INSERT INTO loans (id, user_id, isbn, checkout_date, due_date, status)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """;
    private static final String DECREMENT_BOOK = """
            UPDATE books
            SET available_copies = available_copies - 1
            WHERE isbn = ? AND available_copies > 0
            """;
    private static final String LOCK_LOAN = """
            SELECT id, user_id, isbn, checkout_date, due_date, return_date, status
            FROM loans
            WHERE id = ?
            FOR UPDATE
            """;
    private static final String MARK_RETURNED = """
            UPDATE loans
            SET return_date = ?, status = 'RETURNED'
            WHERE id = ? AND status IN ('ACTIVE', 'OVERDUE')
            """;
    private static final String INCREMENT_BOOK = """
            UPDATE books
            SET available_copies = available_copies + 1
            WHERE isbn = ?
            """;
    private static final String INSERT_FINE = """
            INSERT INTO fines (id, loan_id, amount, paid_status, issued_date)
            VALUES (?, ?, ?, FALSE, ?)
            """;
    private static final String FIND_BY_ID = """
            SELECT id, user_id, isbn, checkout_date, due_date, return_date, status
            FROM loans
            WHERE id = ?
            """;
    private static final String FIND_ACTIVE_BY_ISBN = """
            SELECT id, user_id, isbn, checkout_date, due_date, return_date, status
            FROM loans
            WHERE isbn = ? AND status IN ('ACTIVE', 'OVERDUE')
            ORDER BY checkout_date
            LIMIT 1
            """;

    private final DataSource dataSource;

    public JdbcLoanTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Loan checkout(UUID userId, String isbn, LocalDate checkoutDate, LocalDate dueDate)
            throws SQLException {
        UUID loanId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockAvailableBook(connection, isbn);
                insertLoan(connection, loanId, userId, isbn, checkoutDate, dueDate);
                decrementInventory(connection, isbn);
                connection.commit();
                return new Loan(loanId, userId, isbn, checkoutDate, dueDate);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public ReturnReceipt returnLoan(UUID loanId, LocalDate returnDate) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Loan loan = lockLoan(connection, loanId);
                if (loan.status() == LoanStatus.RETURNED) {
                    throw new SQLException("Loan is already returned: " + loanId);
                }
                lockBook(connection, loan.isbn());
                markReturned(connection, loanId, returnDate);
                incrementInventory(connection, loan.isbn());
                loan.markReturned(returnDate);

                Optional<Fine> fine = Optional.empty();
                BigDecimal amount = loan.calculateOverdueFine();
                if (amount.signum() > 0) {
                    Fine created = new Fine(
                            UUID.randomUUID(), loanId, amount, false, returnDate);
                    insertFine(connection, created);
                    fine = Optional.of(created);
                }

                connection.commit();
                return new ReturnReceipt(loan, fine);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public Optional<Loan> findById(UUID loanId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setObject(1, loanId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapLoan(results)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Loan> findActiveLoanByIsbn(String isbn) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_BY_ISBN)) {
            statement.setString(1, isbn);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapLoan(results)) : Optional.empty();
            }
        }
    }

    private static void lockAvailableBook(Connection connection, String isbn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_BOOK)) {
            statement.setString(1, isbn);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new SQLException("Book does not exist: " + isbn);
                }
                if (results.getInt("available_copies") < 1) {
                    throw new SQLException("No copy is available: " + isbn);
                }
            }
        }
    }

    private static void lockBook(Connection connection, String isbn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_BOOK)) {
            statement.setString(1, isbn);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new SQLException("Book does not exist: " + isbn);
                }
            }
        }
    }

    private static Loan lockLoan(Connection connection, UUID loanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_LOAN)) {
            statement.setObject(1, loanId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    throw new SQLException("Loan does not exist: " + loanId);
                }
                return mapLoan(results);
            }
        }
    }

    private static void insertLoan(
            Connection connection,
            UUID loanId,
            UUID userId,
            String isbn,
            LocalDate checkoutDate,
            LocalDate dueDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_LOAN)) {
            statement.setObject(1, loanId);
            statement.setObject(2, userId);
            statement.setString(3, isbn);
            statement.setObject(4, checkoutDate);
            statement.setObject(5, dueDate);
            statement.executeUpdate();
        }
    }

    private static void markReturned(Connection connection, UUID loanId, LocalDate returnDate)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MARK_RETURNED)) {
            statement.setObject(1, returnDate);
            statement.setObject(2, loanId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Loan could not be returned: " + loanId);
            }
        }
    }

    private static void decrementInventory(Connection connection, String isbn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DECREMENT_BOOK)) {
            statement.setString(1, isbn);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Inventory changed during checkout: " + isbn);
            }
        }
    }

    private static void incrementInventory(Connection connection, String isbn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INCREMENT_BOOK)) {
            statement.setString(1, isbn);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Inventory could not be restored: " + isbn);
            }
        }
    }

    private static void insertFine(Connection connection, Fine fine) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_FINE)) {
            statement.setObject(1, fine.id());
            statement.setObject(2, fine.loanId());
            statement.setBigDecimal(3, fine.amount());
            statement.setObject(4, fine.issuedDate());
            statement.executeUpdate();
        }
    }

    private static Loan mapLoan(ResultSet results) throws SQLException {
        Loan loan = new Loan(
                results.getObject("id", UUID.class),
                results.getObject("user_id", UUID.class),
                results.getString("isbn"),
                results.getObject("checkout_date", LocalDate.class),
                results.getObject("due_date", LocalDate.class));
        LocalDate returnDate = results.getObject("return_date", LocalDate.class);
        if (returnDate != null) {
            loan.markReturned(returnDate);
        }
        return loan;
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
