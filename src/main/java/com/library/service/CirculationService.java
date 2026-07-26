package com.library.service;

import com.library.data.LoanTransactionManager;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.domain.ReturnReceipt;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

public final class CirculationService {
    private final LoanTransactionManager transactions;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Clock clock;
    private final int loanDays;

    public CirculationService(
            LoanTransactionManager transactions,
            AuthorizationService authorization,
            AuditService audit,
            Clock clock,
            int loanDays) {
        if (loanDays < 1) {
            throw new IllegalArgumentException("Loan period must be positive");
        }
        this.transactions = transactions;
        this.authorization = authorization;
        this.audit = audit;
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
        audit.record(
                member.id(),
                "CHECKOUT",
                "{\"loanId\":\"" + loan.id() + "\",\"isbn\":\"" + isbn + "\"}");
        return loan;
    }

    public ReturnReceipt returnLoan(User actor, UUID loanId) throws SQLException {
        Loan existing = transactions.findById(loanId)
                .orElseThrow(() -> new IllegalStateException("Loan not found: " + loanId));
        authorizeReturn(actor, existing);

        ReturnReceipt receipt = transactions.returnLoan(loanId, LocalDate.now(clock));
        if (actor instanceof Member member) {
            member.removeActiveLoan(loanId);
        }
        audit.record(
                actor.id(),
                "RETURN",
                "{\"loanId\":\"" + loanId + "\",\"fine\":"
                        + receipt.fine().map(fine -> fine.amount().toPlainString()).orElse("0")
                        + "}");
        return receipt;
    }

    public ReturnReceipt returnSelectedBook(User actor, String isbn) throws SQLException {
        Loan loan = transactions.findActiveLoanByIsbn(isbn)
                .orElseThrow(() -> new IllegalStateException("No active loan for ISBN " + isbn));
        return returnLoan(actor, loan.id());
    }

    private void authorizeReturn(User actor, Loan loan) {
        if (authorization.isAllowed(actor.role(), Permission.MANAGE_LOANS)) {
            return;
        }
        authorization.require(actor.role(), Permission.BORROW_BOOK);
        if (!(actor instanceof Member member) || !loan.userId().equals(member.id())) {
            throw new SecurityException("Members may only return their own loans");
        }
    }
}
