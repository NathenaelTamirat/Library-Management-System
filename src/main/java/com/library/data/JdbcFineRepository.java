package com.library.data;

import com.library.domain.Fine;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcFineRepository implements FineRepository {
    private static final String FIND_BY_LOAN = """
            SELECT id, loan_id, amount, amount_paid, paid_status, issued_date
            FROM fines
            WHERE loan_id = ?
            """;
    private static final String FIND_UNPAID_BY_USER = """
            SELECT f.id, f.loan_id, f.amount, f.amount_paid, f.paid_status, f.issued_date
            FROM fines f
            INNER JOIN loans l ON l.id = f.loan_id
            WHERE l.user_id = ?
              AND f.paid_status = FALSE
              AND f.waived = FALSE
              AND f.amount_paid < f.amount
            ORDER BY f.issued_date
            """;
    private static final String FIND_UNPAID = """
            SELECT id, loan_id, amount, amount_paid, paid_status, issued_date
            FROM fines
            WHERE paid_status = FALSE AND waived = FALSE AND amount_paid < amount
            ORDER BY issued_date
            """;
    private static final String MARK_PAID = """
            UPDATE fines
            SET paid_status = TRUE, amount_paid = amount
            WHERE id = ? AND paid_status = FALSE AND waived = FALSE
            """;
    private static final String PAY_PARTIAL = """
            UPDATE fines
            SET amount_paid = amount_paid + ?,
                paid_status = CASE
                    WHEN amount_paid + ? >= amount THEN TRUE
                    ELSE paid_status
                END
            WHERE id = ?
              AND paid_status = FALSE
              AND waived = FALSE
              AND ? > 0
              AND amount_paid + ? <= amount
            """;
    private static final String WAIVE = """
            UPDATE fines
            SET waived = TRUE
            WHERE id = ? AND paid_status = FALSE AND waived = FALSE
            """;

    private final DataSource dataSource;

    public JdbcFineRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Fine> findByLoanId(UUID loanId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_LOAN)) {
            statement.setObject(1, loanId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Fine> findUnpaidByUser(UUID userId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_UNPAID_BY_USER)) {
            statement.setObject(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                List<Fine> fines = new ArrayList<>();
                while (results.next()) {
                    fines.add(map(results));
                }
                return fines;
            }
        }
    }

    @Override
    public List<Fine> findUnpaid() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_UNPAID);
             ResultSet results = statement.executeQuery()) {
            List<Fine> fines = new ArrayList<>();
            while (results.next()) {
                fines.add(map(results));
            }
            return fines;
        }
    }

    @Override
    public void markPaid(UUID fineId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(MARK_PAID)) {
            statement.setObject(1, fineId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Fine not found or already settled: " + fineId);
            }
        }
    }

    @Override
    public void payPartial(UUID fineId, BigDecimal payment) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PAY_PARTIAL)) {
            statement.setBigDecimal(1, payment);
            statement.setBigDecimal(2, payment);
            statement.setObject(3, fineId);
            statement.setBigDecimal(4, payment);
            statement.setBigDecimal(5, payment);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Partial payment rejected for fine: " + fineId);
            }
        }
    }

    @Override
    public void waive(UUID fineId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(WAIVE)) {
            statement.setObject(1, fineId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Fine not found or already settled: " + fineId);
            }
        }
    }

    private static Fine map(ResultSet results) throws SQLException {
        return new Fine(
                results.getObject("id", UUID.class),
                results.getObject("loan_id", UUID.class),
                results.getObject("amount", BigDecimal.class),
                results.getObject("amount_paid", BigDecimal.class),
                results.getBoolean("paid_status"),
                results.getObject("issued_date", LocalDate.class));
    }
}
