package com.library.data;

import com.library.domain.LoanPolicy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

public final class JdbcLoanPolicyRepository implements LoanPolicyRepository {
    private static final String SELECT = """
            SELECT loan_days, daily_fine, replacement_fine, max_renewals, borrow_limit
            FROM library_policy
            WHERE id = 1
            """;
    private static final String UPSERT = """
            MERGE INTO library_policy (id, loan_days, daily_fine, replacement_fine, max_renewals, borrow_limit)
            KEY (id)
            VALUES (1, ?, ?, ?, ?, ?)
            """;
    private static final String UPSERT_PG = """
            INSERT INTO library_policy (id, loan_days, daily_fine, replacement_fine, max_renewals, borrow_limit)
            VALUES (1, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                loan_days = EXCLUDED.loan_days,
                daily_fine = EXCLUDED.daily_fine,
                replacement_fine = EXCLUDED.replacement_fine,
                max_renewals = EXCLUDED.max_renewals,
                borrow_limit = EXCLUDED.borrow_limit
            """;

    private final DataSource dataSource;
    private final boolean postgres;

    public JdbcLoanPolicyRepository(DataSource dataSource) {
        this(dataSource, false);
    }

    public JdbcLoanPolicyRepository(DataSource dataSource, boolean postgres) {
        this.dataSource = dataSource;
        this.postgres = postgres;
    }

    @Override
    public LoanPolicy load() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT);
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                LoanPolicy defaults = LoanPolicy.defaults();
                save(defaults);
                return defaults;
            }
            return new LoanPolicy(
                    results.getInt("loan_days"),
                    results.getBigDecimal("daily_fine"),
                    results.getBigDecimal("replacement_fine"),
                    results.getInt("max_renewals"),
                    results.getInt("borrow_limit"));
        }
    }

    @Override
    public void save(LoanPolicy policy) throws SQLException {
        String sql = postgres ? UPSERT_PG : UPSERT;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, policy.loanDays());
            statement.setBigDecimal(2, policy.dailyFine());
            statement.setBigDecimal(3, policy.replacementFine());
            statement.setInt(4, policy.maxRenewals());
            statement.setInt(5, policy.borrowLimit());
            statement.executeUpdate();
        }
    }
}
