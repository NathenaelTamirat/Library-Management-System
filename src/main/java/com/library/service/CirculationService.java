package com.library.service;

import com.library.data.LoanTransactionManager;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;

public final class CirculationService {
    private final LoanTransactionManager transactions;
    private final AuthorizationService authorization;
    private final Clock clock;
    private final int loanDays;

    public CirculationService(
            LoanTransactionManager transactions,
            AuthorizationService authorization,
            Clock clock,
            int loanDays) {
        if (loanDays < 1) {
            throw new IllegalArgumentException("Loan period must be positive");
        }
        this.transactions = transactions;
        this.authorization = authorization;
        this.clock = clock;
        this.loanDays = loanDays;
    }

    public Loan checkout(Member member, String isbn) throws SQLException {
        authorization.require(member.role(), Permission.BORROW_BOOK);
        if (!member.canBorrow()) {
            throw new IllegalStateException("Member has reached the borrowing limit");
        }
        LocalDate checkoutDate = LocalDate.now(clock);
        Loan loan = transactions.checkout(
                member.id(),
                isbn,
                checkoutDate,
                checkoutDate.plusDays(loanDays));
        member.addLoan(loan);
        return loan;
    }
}
