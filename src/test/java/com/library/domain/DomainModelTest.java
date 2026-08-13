package com.library.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainModelTest {
    @Test
    void bookEnforcesInventoryAndAvailability() {
        Book book = new Book("9780134685991", "Effective Java", "Joshua Bloch", 1, 1);

        book.checkOutCopy();

        assertFalse(book.isAvailable());
        assertEquals(0, book.availableCopies());
        assertThrows(IllegalStateException.class, book::checkOutCopy);

        book.returnCopy();
        assertTrue(book.isAvailable());
    }

    @Test
    void loanCalculatesFineUsingAnInjectedClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        Loan loan = new Loan(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "9780134685991",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 20),
                clock);

        assertEquals(LoanStatus.OVERDUE, loan.status());
        assertEquals(new BigDecimal("3.00"), loan.calculateOverdueFine());
    }

    @Test
    void memberEnforcesBorrowingLimitAndAggregatesFines() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);
        Member member = new Member(UUID.randomUUID(), "Ada", "ADA@example.edu", "hash", 1);
        Loan loan = new Loan(
                UUID.randomUUID(),
                member.id(),
                "9780134685991",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 20),
                clock);

        member.addLoan(loan);

        assertFalse(member.canBorrow());
        assertEquals("ada@example.edu", member.email());
        assertEquals(new BigDecimal("3.00"), member.currentFinesBalance());
        assertThrows(IllegalStateException.class, () -> member.addLoan(loan));
    }

    @Test
    void fineRemainingReflectsPartialPayments() {
        Fine fine = new Fine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                new BigDecimal("3.25"),
                false,
                LocalDate.of(2026, 7, 26));

        assertEquals(new BigDecimal("6.75"), fine.remaining());
        assertTrue(fine.remaining().signum() >= 0);
    }

    @Test
    void waivedFineHasNoRemainingBalance() {
        Fine waived = new Fine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                true,
                LocalDate.of(2026, 7, 26));

        assertEquals(BigDecimal.ZERO, waived.remaining());
    }

    @Test
    void fineRejectsPaymentsGreaterThanItsAmount() {
        assertThrows(IllegalArgumentException.class, () -> new Fine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                new BigDecimal("10.01"),
                false,
                LocalDate.of(2026, 7, 26)));
    }
}
