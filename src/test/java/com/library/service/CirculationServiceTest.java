package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.FineRepository;
import com.library.data.HoldRepository;
import com.library.data.LoanTransactionManager;
import com.library.domain.Fine;
import com.library.domain.Hold;
import com.library.domain.Librarian;
import com.library.domain.Loan;
import com.library.domain.LoanStatus;
import com.library.domain.Member;
import com.library.domain.ReturnReceipt;
import com.library.security.AuthorizationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CirculationServiceTest {
    @Test
    void checkoutUsesConfiguredPeriodAndAddsTheCommittedLoanToMember() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = service(transactions, audits, clock, new RecordingFines(), 21);
        Member member = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 2);

        Loan loan = circulation.checkout(member, "9780134685991");

        assertEquals(LocalDate.of(2026, 7, 26), transactions.checkoutDate);
        assertEquals(LocalDate.of(2026, 8, 16), transactions.dueDate);
        assertEquals(loan, member.activeLoans().get(0));
        assertEquals("CHECKOUT", audits.actions.get(0));
    }

    @Test
    void checkoutStopsBeforePersistenceWhenBorrowingLimitIsReached() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        CirculationService circulation = service(
                transactions, audits, Clock.systemUTC(), new RecordingFines(), 14);
        Member member = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 1);
        circulation.checkout(member, "9780134685991");

        assertThrows(IllegalStateException.class,
                () -> circulation.checkout(member, "9780321356680"));
        assertEquals(1, transactions.calls);
        assertEquals(1, audits.actions.size());
    }

    @Test
    void checkoutStopsBeforePersistenceWhenMemberHasUnpaidFines() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        RecordingFines fines = new RecordingFines();
        Member member = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 2);
        fines.unpaid.put(member.id(), List.of(new Fine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("2.50"),
                false,
                LocalDate.of(2026, 7, 1))));
        CirculationService circulation = service(
                transactions, audits, Clock.systemUTC(), fines, 14);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> circulation.checkout(member, "9780134685991"));

        assertTrue(failure.getMessage().contains("unpaid fines"));
        assertEquals(0, transactions.calls);
        assertTrue(audits.actions.isEmpty());
    }

    @Test
    void checkoutRejectsJumpAheadWhenAnotherMemberHoldsFirst() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        Member holder = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 2);
        Member jumper = new Member(UUID.randomUUID(), "Grace", "grace@example.edu", "hash", 2);
        HoldService holdService = new HoldService(
                new QueueHolds(holder.id(), "9780134685991"),
                new AuthorizationService(),
                new AuditService(audits),
                Clock.systemUTC());
        CirculationService circulation = new CirculationService(
                transactions,
                new RecordingFines(),
                holdService,
                new AuthorizationService(),
                new AuditService(audits),
                Clock.systemUTC(),
                14);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> circulation.checkout(jumper, "9780134685991"));
        assertTrue(failure.getMessage().contains("hold queue"));
        assertEquals(0, transactions.calls);

        Loan loan = circulation.checkout(holder, "9780134685991");
        assertNotNull(loan);
        assertEquals(1, transactions.calls);
    }

    @Test
    void staffCanCheckoutForMemberWithStaffAudit() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = service(transactions, audits, clock, new RecordingFines(), 14);
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 2);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Libby", "lib@example.edu", "hash", "desk", false);

        Loan loan = circulation.checkoutFor(librarian, member, "9780134685991");

        assertEquals(loan, member.activeLoans().get(0));
        assertEquals(List.of("STAFF_CHECKOUT"), audits.actions);
        assertThrows(
                SecurityException.class,
                () -> circulation.checkoutFor(member, member, "9780321356680"));
    }

    @Test
    void memberCanReturnOwnLoanAndFreesBorrowingCapacity() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = service(transactions, audits, clock, new RecordingFines(), 14);
        Member member = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 1);
        Loan loan = circulation.checkout(member, "9780134685991");

        ReturnReceipt receipt = circulation.returnLoan(member, loan.id());

        assertEquals(LoanStatus.RETURNED, receipt.loan().status());
        assertTrue(member.activeLoans().isEmpty());
        assertTrue(member.canBorrow());
        assertEquals(List.of("CHECKOUT", "RETURN"), audits.actions);
    }

    @Test
    void staffCanMarkLoanLostWithReplacementFineAudit() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = service(transactions, audits, clock, new RecordingFines(), 14);
        Member member = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 1);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Libby", "lib@example.edu", "hash", "desk", false);
        Loan loan = circulation.checkout(member, "9780134685991");

        Loan lost = circulation.markLost(librarian, loan.id());

        assertEquals(LoanStatus.LOST, lost.status());
        assertEquals(List.of("CHECKOUT", "MARK_LOST"), audits.actions);
        assertThrows(SecurityException.class, () -> circulation.markLost(member, loan.id()));
    }

    @Test
    void staffCanReconcileOverdueLoansUsingInjectedClock() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = service(transactions, audits, clock, new RecordingFines(), 14);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Libby", "lib@example.edu", "hash", "desk", false);

        int updated = circulation.reconcileOverdue(librarian);

        assertEquals(LocalDate.of(2026, 7, 26), transactions.overdueAsOf);
        assertEquals(2, updated);
        assertEquals(List.of("RECONCILE_OVERDUE"), audits.actions);
    }

    @Test
    void renewRejectsWhenLimitOrUnpaidFinesOrOverdue() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        RecordingFines fines = new RecordingFines();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = new CirculationService(
                transactions,
                fines,
                null,
                new AuthorizationService(),
                new AuditService(audits),
                clock,
                14,
                1);
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 2);
        Loan loan = circulation.checkout(member, "9780134685991");
        circulation.renew(member, loan.id());

        assertThrows(IllegalStateException.class, () -> circulation.renew(member, loan.id()));

        Member other = new Member(UUID.randomUUID(), "Grace", "grace@example.edu", "hash", 2);
        Loan second = circulation.checkout(other, "9780321356680");
        fines.unpaid.put(other.id(), List.of(new Fine(
                UUID.randomUUID(), second.id(), new BigDecimal("1.00"), false, LocalDate.of(2026, 7, 1))));
        assertThrows(IllegalStateException.class, () -> circulation.renew(other, second.id()));
    }

    @Test
    void memberCanRenewOwnLoan() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = service(transactions, audits, clock, new RecordingFines(), 14);
        Member member = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 1);
        Loan loan = circulation.checkout(member, "9780134685991");

        Loan renewed = circulation.renew(member, loan.id());

        assertEquals(LocalDate.of(2026, 8, 23), renewed.dueDate());
        assertEquals(1, renewed.renewalCount());
        assertEquals(List.of("CHECKOUT", "RENEW"), audits.actions);
    }

    @Test
    void memberCannotReturnAnotherMembersLoan() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingAuditRepository audits = new RecordingAuditRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = service(transactions, audits, clock, new RecordingFines(), 14);
        Member owner = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 1);
        Member stranger = new Member(
                UUID.randomUUID(), "Grace", "grace@example.edu", "hash", 1);
        Loan loan = circulation.checkout(owner, "9780134685991");

        assertThrows(SecurityException.class, () -> circulation.returnLoan(stranger, loan.id()));
        assertEquals(0, transactions.returnCalls);
        assertEquals(List.of("CHECKOUT"), audits.actions);
    }

    private static CirculationService service(
            LoanTransactionManager transactions,
            RecordingAuditRepository audits,
            Clock clock,
            FineRepository fines,
            int loanDays) {
        return new CirculationService(
                transactions,
                fines,
                new AuthorizationService(),
                new AuditService(audits),
                clock,
                loanDays);
    }

    private static final class RecordingAuditRepository implements com.library.data.AuditRepository {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void record(Optional<UUID> userId, String action, String details) {
            actions.add(action);
        }
        @Override
        public java.util.List<com.library.domain.AuditEntry> findRecent(int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.library.domain.AuditEntry> findByAction(String action, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.library.domain.AuditEntry> findByUser(java.util.UUID userId, int limit) {
            return java.util.List.of();
        }

    }

    private static final class RecordingFines implements FineRepository {
        private final Map<UUID, List<Fine>> unpaid = new HashMap<>();

        @Override
        public Optional<Fine> findByLoanId(UUID loanId) {
            return Optional.empty();
        }

        @Override
        public List<Fine> findUnpaidByUser(UUID userId) {
            return unpaid.getOrDefault(userId, List.of());
        }

        @Override
        public List<Fine> findUnpaid() {
            return List.of();
        }

        @Override
        public void markPaid(UUID fineId) {
        }

        @Override
        public void waive(UUID fineId) {
        }
    }

    private static final class RecordingTransactions implements LoanTransactionManager {
        private int calls;
        private int returnCalls;
        private LocalDate checkoutDate;
        private LocalDate dueDate;
        private LocalDate overdueAsOf;
        private final Map<UUID, Loan> loans = new HashMap<>();

        @Override
        public Loan checkout(
                UUID userId,
                String isbn,
                LocalDate checkoutDate,
                LocalDate dueDate,
                int borrowingLimit) {
            calls++;
            this.checkoutDate = checkoutDate;
            this.dueDate = dueDate;
            long active = loans.values().stream()
                    .filter(loan -> loan.userId().equals(userId))
                    .filter(loan -> loan.status() != LoanStatus.RETURNED)
                    .count();
            if (active >= borrowingLimit) {
                throw new IllegalStateException("Borrowing limit reached");
            }
            Loan loan = new Loan(UUID.randomUUID(), userId, isbn, checkoutDate, dueDate);
            loans.put(loan.id(), loan);
            return loan;
        }

        @Override
        public ReturnReceipt returnLoan(UUID loanId, LocalDate returnDate) {
            returnCalls++;
            Loan loan = loans.get(loanId);
            loan.markReturned(returnDate);
            return new ReturnReceipt(loan, Optional.empty());
        }

        @Override
        public Loan renew(UUID loanId, LocalDate newDueDate) {
            Loan loan = loans.get(loanId);
            loan.renew(newDueDate);
            return loan;
        }

        @Override
        public int markOverdueBefore(LocalDate asOfDate) {
            overdueAsOf = asOfDate;
            return 2;
        }

        @Override
        public Loan markLost(UUID loanId, BigDecimal replacementFine, LocalDate issuedDate) {
            Loan loan = loans.get(loanId);
            loan.markLost();
            return loan;
        }

        @Override
        public int countOpenLoansByIsbn(String isbn) {
            return (int) loans.values().stream()
                    .filter(loan -> loan.isbn().equals(isbn))
                    .filter(loan -> loan.status() != LoanStatus.RETURNED
                            && loan.status() != LoanStatus.LOST)
                    .count();
        }

        @Override
        public List<Loan> findOpenLoansByUser(UUID userId) {
            return loans.values().stream()
                    .filter(loan -> loan.userId().equals(userId))
                    .filter(loan -> loan.status() != LoanStatus.RETURNED
                            && loan.status() != LoanStatus.LOST)
                    .toList();
        }

        @Override
        public List<Loan> findOverdueLoans() {
            return loans.values().stream()
                    .filter(loan -> loan.status() == LoanStatus.OVERDUE)
                    .toList();
        }

        @Override
        public Optional<Loan> findById(UUID loanId) {
            return Optional.ofNullable(loans.get(loanId));
        }

        @Override
        public Optional<Loan> findActiveLoanByIsbn(String isbn) {
            return loans.values().stream()
                    .filter(loan -> loan.isbn().equals(isbn))
                    .filter(loan -> loan.status() != LoanStatus.RETURNED
                            && loan.status() != LoanStatus.LOST)
                    .findFirst();
        }
    }

    private static final class QueueHolds implements HoldRepository {
        private final Hold head;

        private QueueHolds(UUID holderId, String isbn) {
            this.head = new Hold(UUID.randomUUID(), holderId, isbn, Instant.now());
        }

        @Override
        public Hold place(UUID userId, String isbn, Instant placedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel(UUID holdId) {
        }

        @Override
        public void fulfill(UUID holdId) {
        }

        @Override
        public Optional<Hold> findById(UUID holdId) {
            return Optional.of(head);
        }

        @Override
        public Optional<Hold> findActiveByUserAndIsbn(UUID userId, String isbn) {
            return head.userId().equals(userId) && head.isbn().equals(isbn)
                    ? Optional.of(head)
                    : Optional.empty();
        }

        @Override
        public Optional<Hold> findFirstActiveByIsbn(String isbn) {
            return head.isbn().equals(isbn) ? Optional.of(head) : Optional.empty();
        }

        @Override
        public List<Hold> findActiveByIsbn(String isbn) {
            return head.isbn().equals(isbn) ? List.of(head) : List.of();
        }

        @Override
        public List<Hold> findActiveByUser(UUID userId) {
            return head.userId().equals(userId) ? List.of(head) : List.of();
        }

        @Override
        public boolean bookExists(String isbn) {
            return true;
        }
    }
}
