package com.library.data;

import com.library.domain.Loan;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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

    private static void decrementInventory(Connection connection, String isbn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DECREMENT_BOOK)) {
            statement.setString(1, isbn);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Inventory changed during checkout: " + isbn);
            }
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
