package com.library.data;

import com.library.domain.Loan;
import com.library.domain.ReturnReceipt;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
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

    Loan renew(UUID loanId, LocalDate newDueDate) throws SQLException;

    int markOverdueBefore(LocalDate asOfDate) throws SQLException;

    Loan markLost(UUID loanId, BigDecimal replacementFine, LocalDate issuedDate)
            throws SQLException;

    int countOpenLoansByIsbn(String isbn) throws SQLException;

    List<Loan> findOpenLoansByUser(UUID userId) throws SQLException;

    List<Loan> findOverdueLoans() throws SQLException;

    Optional<Loan> findById(UUID loanId) throws SQLException;

    Optional<Loan> findActiveLoanByIsbn(String isbn) throws SQLException;
}
