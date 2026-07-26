package com.library.data;

import com.library.domain.Loan;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

public interface LoanTransactionManager {
    Loan checkout(UUID userId, String isbn, LocalDate checkoutDate, LocalDate dueDate)
            throws SQLException;
}
