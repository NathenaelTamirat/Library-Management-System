package com.library.service;

import com.library.data.FineRepository;
import com.library.data.LoanTransactionManager;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.domain.ReturnReceipt;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CirculationService {
    public static final BigDecimal REPLACEMENT_FINE = new BigDecimal("50.00");

    private final LoanTransactionManager transactions;
    private final FineRepository fines;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Clock clock;
    private final int loanDays;

    public CirculationService(
            LoanTransactionManager transactions,
            FineRepository fines,
            AuthorizationService authorization,
            AuditService audit,
            Clock clock,
            int loanDays) {
        if (loanDays < 1) {
            throw new IllegalArgumentException("Loan period must be positive");
        }
        this.transactions = transactions;
        this.fines = fines;
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
        if (!fines.findUnpaidByUser(member.id()).isEmpty()) {
            throw new IllegalStateException("Member has unpaid fines");
        }
        LocalDate checkoutDate = LocalDate.now(clock);
        Loan loan = transactions.checkout(
                member.id(),
                isbn,
                checkoutDate,
                checkoutDate.plusDays(loanDays),
                member.borrowingLimit());
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

    public Loan renew(User actor, UUID loanId) throws SQLException {
        Loan existing = transactions.findById(loanId)
                .orElseThrow(() -> new IllegalStateException("Loan not found: " + loanId));
        authorizeLoanOwnerOrStaff(actor, existing);
        LocalDate today = LocalDate.now(clock);
        LocalDate base = existing.dueDate().isAfter(today) ? existing.dueDate() : today;
        Loan renewed = transactions.renew(loanId, base.plusDays(loanDays));
        audit.record(
                actor.id(),
                "RENEW",
                "{\"loanId\":\"" + loanId + "\",\"dueDate\":\"" + renewed.dueDate() + "\"}");
        return renewed;
    }

    public int reconcileOverdue(User actor) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_LOANS);
        int updated = transactions.markOverdueBefore(LocalDate.now(clock));
        audit.record(actor.id(), "RECONCILE_OVERDUE", "{\"updated\":" + updated + "}");
        return updated;
    }

    public Loan markLost(User actor, UUID loanId) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_LOANS);
        Loan lost = transactions.markLost(loanId, REPLACEMENT_FINE, LocalDate.now(clock));
        if (actor instanceof Member member) {
            member.removeActiveLoan(loanId);
        }
        audit.record(
                actor.id(),
                "MARK_LOST",
                "{\"loanId\":\"" + loanId + "\",\"fine\":\""
                        + REPLACEMENT_FINE.toPlainString() + "\"}");
        return lost;
    }

    public List<Loan> openLoansFor(User actor, UUID memberId) throws SQLException {
        if (!actor.id().equals(memberId)) {
            authorization.require(actor.role(), Permission.MANAGE_LOANS);
        }
        return transactions.findOpenLoansByUser(memberId);
    }

    public List<Loan> overdueLoans(User actor) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_LOANS);
        return transactions.findOverdueLoans();
    }

    private void authorizeReturn(User actor, Loan loan) {
        authorizeLoanOwnerOrStaff(actor, loan);
    }

    private void authorizeLoanOwnerOrStaff(User actor, Loan loan) {
        if (authorization.isAllowed(actor.role(), Permission.MANAGE_LOANS)) {
            return;
        }
        authorization.require(actor.role(), Permission.BORROW_BOOK);
        if (!(actor instanceof Member member) || !loan.userId().equals(member.id())) {
            throw new SecurityException("Members may only manage their own loans");
        }
    }
}
