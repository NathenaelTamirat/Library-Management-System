package com.library.data;

import com.library.domain.CirculationSummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

public final class JdbcCirculationReportRepository implements CirculationReportRepository {
    private static final String SUMMARY = """
            SELECT
                (SELECT COUNT(*) FROM loans WHERE status IN ('ACTIVE', 'OVERDUE')) AS open_loans,
                (SELECT COUNT(DISTINCT user_id) FROM loans
                    WHERE status IN ('ACTIVE', 'OVERDUE')) AS members_with_open_loans,
                (SELECT COUNT(*) FROM loans WHERE status = 'OVERDUE') AS overdue_loans,
                (SELECT COUNT(*) FROM fines WHERE paid_status = FALSE AND waived = FALSE) AS unpaid_fines,
                (SELECT COALESCE(SUM(amount - amount_paid), 0) FROM fines
                    WHERE paid_status = FALSE AND waived = FALSE
                      AND amount_paid < amount) AS unpaid_fine_total,
                (SELECT COALESCE(SUM(available_copies), 0) FROM books) AS available_copies,
                (SELECT COALESCE(SUM(total_copies), 0) FROM books) AS total_copies
            """;

    private final DataSource dataSource;

    public JdbcCirculationReportRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CirculationSummary summarize() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SUMMARY);
             ResultSet results = statement.executeQuery()) {
            results.next();
            return new CirculationSummary(
                    results.getLong("open_loans"),
                    results.getLong("members_with_open_loans"),
                    results.getLong("overdue_loans"),
                    results.getLong("unpaid_fines"),
                    results.getBigDecimal("unpaid_fine_total"),
                    results.getLong("available_copies"),
                    results.getLong("total_copies"));
        }
    }
}
