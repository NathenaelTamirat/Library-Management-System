package com.library.data;

import com.library.domain.Loan;
import com.library.domain.ReturnReceipt;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LoanTransactionManager {
    Loan checkout(
            UUID userId,
            String isbn,
            LocalDate checkoutDate,
            LocalDate dueDate,
            int borrowingLimit) throws SQLException;

    ReturnReceipt returnLoan(UUID loanId, LocalDate returnDate) throws SQLException;

    Optional<Loan> findById(UUID loanId) throws SQLException;

    Optional<Loan> findActiveLoanByIsbn(String isbn) throws SQLException;
}
