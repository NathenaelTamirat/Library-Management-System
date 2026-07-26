package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.LoanTransactionManager;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.security.AuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    private static final class RecordingTransactions implements LoanTransactionManager {
        private int calls;
        private LocalDate checkoutDate;
        private LocalDate dueDate;

        @Override
        public Loan checkout(
                UUID userId,
                String isbn,
                LocalDate checkoutDate,
                LocalDate dueDate) {
            calls++;
            this.checkoutDate = checkoutDate;
            this.dueDate = dueDate;
            return new Loan(UUID.randomUUID(), userId, isbn, checkoutDate, dueDate);
        }
    }
}
