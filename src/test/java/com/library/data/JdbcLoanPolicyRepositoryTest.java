package com.library.data;

import static org.junit.jupiter.api.Assertions.*;

import com.library.domain.LoanPolicy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcLoanPolicyRepositoryTest {
    private JdbcLoanPolicyRepository policies;

    @BeforeEach
    void createDatabase() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:policy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS library_policy");
            statement.execute("""
                    CREATE TABLE library_policy (
                        id INTEGER PRIMARY KEY,
                        loan_days INTEGER NOT NULL,
                        daily_fine DECIMAL(12, 2) NOT NULL,
                        replacement_fine DECIMAL(12, 2) NOT NULL,
                        max_renewals INTEGER NOT NULL,
                        borrow_limit INTEGER NOT NULL
                    )
                    """);
        }
        policies = new JdbcLoanPolicyRepository(dataSource, false);
    }

    @Test
    void loadsDefaultsThenPersistsUpdates() throws Exception {
        LoanPolicy defaults = policies.load();
        assertEquals(14, defaults.loanDays());
        assertEquals(5, defaults.borrowLimit());

        LoanPolicy updated = new LoanPolicy(10, new BigDecimal("1.00"), new BigDecimal("40.00"), 1, 3);
        policies.save(updated);
        assertEquals(updated, policies.load());
    }
}
