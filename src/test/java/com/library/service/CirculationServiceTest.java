package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.LoanTransactionManager;
import com.library.domain.Loan;
import com.library.domain.LoanStatus;
import com.library.domain.Member;
import com.library.domain.ReturnReceipt;
import com.library.security.AuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CirculationServiceTest {
    @Test
    void checkoutUsesConfiguredPeriodAndAddsTheCommittedLoanToMember() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = new CirculationService(
                transactions, new AuthorizationService(), clock, 21);
        Member member = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 2);

        Loan loan = circulation.checkout(member, "9780134685991");

        assertEquals(LocalDate.of(2026, 7, 26), transactions.checkoutDate);
        assertEquals(LocalDate.of(2026, 8, 16), transactions.dueDate);
        assertEquals(loan, member.activeLoans().get(0));
    }

    @Test
    void checkoutStopsBeforePersistenceWhenBorrowingLimitIsReached() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        CirculationService circulation = new CirculationService(
                transactions,
                new AuthorizationService(),
                Clock.systemUTC(),
                14);
        Member member = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 1);
        circulation.checkout(member, "9780134685991");

        assertThrows(IllegalStateException.class,
                () -> circulation.checkout(member, "9780321356680"));
        assertEquals(1, transactions.calls);
    }

    @Test
    void memberCanReturnOwnLoanAndFreesBorrowingCapacity() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = new CirculationService(
                transactions, new AuthorizationService(), clock, 14);
        Member member = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 1);
        Loan loan = circulation.checkout(member, "9780134685991");

        ReturnReceipt receipt = circulation.returnLoan(member, loan.id());

        assertEquals(LoanStatus.RETURNED, receipt.loan().status());
        assertTrue(member.activeLoans().isEmpty());
        assertTrue(member.canBorrow());
    }

    @Test
    void memberCannotReturnAnotherMembersLoan() throws Exception {
        RecordingTransactions transactions = new RecordingTransactions();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        CirculationService circulation = new CirculationService(
                transactions, new AuthorizationService(), clock, 14);
        Member owner = new Member(
                UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 1);
        Member stranger = new Member(
                UUID.randomUUID(), "Grace", "grace@example.edu", "hash", 1);
        Loan loan = circulation.checkout(owner, "9780134685991");

        assertThrows(SecurityException.class, () -> circulation.returnLoan(stranger, loan.id()));
        assertEquals(0, transactions.returnCalls);
    }

    private static final class RecordingTransactions implements LoanTransactionManager {
        private int calls;
        private int returnCalls;
        private LocalDate checkoutDate;
        private LocalDate dueDate;
        private final Map<UUID, Loan> loans = new HashMap<>();

        @Override
        public Loan checkout(
                UUID userId,
                String isbn,
                LocalDate checkoutDate,
                LocalDate dueDate) {
            calls++;
            this.checkoutDate = checkoutDate;
            this.dueDate = dueDate;
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
        public Optional<Loan> findById(UUID loanId) {
            return Optional.ofNullable(loans.get(loanId));
        }

        @Override
        public Optional<Loan> findActiveLoanByIsbn(String isbn) {
            return loans.values().stream()
                    .filter(loan -> loan.isbn().equals(isbn))
                    .filter(loan -> loan.status() != com.library.domain.LoanStatus.RETURNED)
                    .findFirst();
        }
    }
}
