package com.library.service;

import com.library.data.FineRepository;
import com.library.data.LoanTransactionManager;
import com.library.domain.Hold;
import com.library.domain.Loan;
import com.library.domain.LoanStatus;
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
import java.util.Optional;
import java.util.UUID;

public final class CirculationService {
    public static final BigDecimal REPLACEMENT_FINE = new BigDecimal("50.00");

    private final LoanTransactionManager transactions;
    private final FineRepository fines;
    private final HoldService holds;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Clock clock;
    private final int loanDays;
    private final int maxRenewals;

    public CirculationService(
            LoanTransactionManager transactions,
            FineRepository fines,
            AuthorizationService authorization,
            AuditService audit,
            Clock clock,
            int loanDays) {
        this(transactions, fines, null, authorization, audit, clock, loanDays, 2);
    }

    public CirculationService(
            LoanTransactionManager transactions,
            FineRepository fines,
            HoldService holds,
            AuthorizationService authorization,
            AuditService audit,
            Clock clock,
            int loanDays) {
        this(transactions, fines, holds, authorization, audit, clock, loanDays, 2);
    }

    public CirculationService(
            LoanTransactionManager transactions,
            FineRepository fines,
            HoldService holds,
            AuthorizationService authorization,
            AuditService audit,
            Clock clock,
            int loanDays,
            int maxRenewals) {
        if (loanDays < 1) {
            throw new IllegalArgumentException("Loan period must be positive");
        }
        if (maxRenewals < 0) {
            throw new IllegalArgumentException("Max renewals cannot be negative");
        }
        this.transactions = transactions;
        this.fines = fines;
        this.holds = holds;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
        this.loanDays = loanDays;
        this.maxRenewals = maxRenewals;
    }

    public Loan checkout(Member member, String isbn) throws SQLException {
        authorization.require(member, Permission.BORROW_BOOK);
        return completeCheckout(member, isbn, member.id(), "CHECKOUT");
    }

    public Loan checkoutFor(User actor, Member member, String isbn) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        return completeCheckout(member, isbn, actor.id(), "STAFF_CHECKOUT");
    }

    private Loan completeCheckout(Member member, String isbn, UUID auditorId, String action)
            throws SQLException {
        if (transactions.findOpenLoansByUser(member.id()).size() >= member.borrowingLimit()) {
            throw new IllegalStateException("Member has reached the borrowing limit");
        }
        if (!fines.findUnpaidByUser(member.id()).isEmpty()) {
            throw new IllegalStateException("Member has unpaid fines");
        }
        enforceHoldQueue(member.id(), isbn);
        LocalDate checkoutDate = LocalDate.now(clock);
        Loan loan = transactions.checkout(
                member.id(),
                isbn,
                checkoutDate,
                checkoutDate.plusDays(loanDays),
                member.borrowingLimit());
        member.addLoan(loan);
        if (holds != null) {
            holds.fulfillIfOwned(member.id(), isbn);
        }
        audit.record(
                auditorId,
                action,
                "{\"loanId\":\"" + loan.id() + "\",\"isbn\":\"" + isbn
                        + "\",\"memberId\":\"" + member.id() + "\"}");
        return loan;
    }

    private void enforceHoldQueue(UUID memberId, String isbn) throws SQLException {
        if (holds == null) {
            return;
        }
        Optional<Hold> first = holds.firstActive(isbn);
        if (first.isPresent() && !first.orElseThrow().userId().equals(memberId)) {
            throw new IllegalStateException(
                    "Another member is first in the hold queue for " + isbn);
        }
    }

    public ReturnReceipt returnLoan(User actor, UUID loanId) throws SQLException {
        Loan existing = transactions.findById(loanId)
                .orElseThrow(() -> new IllegalStateException("Loan not found: " + loanId));
        authorizeReturn(actor, existing);

        ReturnReceipt receipt = transactions.returnLoan(loanId, LocalDate.now(clock));
        if (actor instanceof Member member) {
            member.removeActiveLoan(loanId);
        }
        if (holds != null) {
            holds.readyNextForIsbn(receipt.loan().isbn());
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
        if (existing.status() == LoanStatus.OVERDUE) {
            throw new IllegalStateException("Cannot renew an overdue loan");
        }
        if (existing.renewalCount() >= maxRenewals) {
            throw new IllegalStateException("Renewal limit reached: " + maxRenewals);
        }
        if (!fines.findUnpaidByUser(existing.userId()).isEmpty()) {
            throw new IllegalStateException("Cannot renew while member has unpaid fines");
        }
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
        authorization.require(actor, Permission.MANAGE_LOANS);
        int updated = transactions.markOverdueBefore(LocalDate.now(clock));
        audit.record(actor.id(), "RECONCILE_OVERDUE", "{\"updated\":" + updated + "}");
        return updated;
    }

    public Loan markLost(User actor, UUID loanId) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
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
            authorization.require(actor, Permission.MANAGE_LOANS);
        }
        return transactions.findOpenLoansByUser(memberId);
    }

    public List<Loan> overdueLoans(User actor) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        return transactions.findOverdueLoans();
    }

    private void authorizeReturn(User actor, Loan loan) {
        authorizeLoanOwnerOrStaff(actor, loan);
    }

    private void authorizeLoanOwnerOrStaff(User actor, Loan loan) {
        if (authorization.isAllowed(actor.role(), Permission.MANAGE_LOANS)) {
            authorization.require(actor, Permission.MANAGE_LOANS);
            return;
        }
        authorization.require(actor, Permission.BORROW_BOOK);
        if (!(actor instanceof Member member) || !loan.userId().equals(member.id())) {
            throw new SecurityException("Members may only manage their own loans");
        }
    }
}
