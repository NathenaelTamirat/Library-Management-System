package com.library.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public final class Loan {
    public static final BigDecimal DAILY_FINE = new BigDecimal("0.50");

    private final UUID id;
    private final UUID userId;
    private final String isbn;
    private final LocalDate checkoutDate;
    private final Clock clock;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private LoanStatus status;
    private int renewalCount;

    public Loan(UUID id, UUID userId, String isbn, LocalDate checkoutDate, LocalDate dueDate) {
        this(id, userId, isbn, checkoutDate, dueDate, Clock.systemDefaultZone());
    }

    Loan(UUID id, UUID userId, String isbn, LocalDate checkoutDate, LocalDate dueDate, Clock clock) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.isbn = Objects.requireNonNull(isbn);
        this.checkoutDate = Objects.requireNonNull(checkoutDate);
        this.dueDate = Objects.requireNonNull(dueDate);
        this.clock = Objects.requireNonNull(clock);
        if (dueDate.isBefore(checkoutDate)) {
            throw new IllegalArgumentException("Due date cannot precede checkout date");
        }
        this.status = LoanStatus.ACTIVE;
        this.renewalCount = 0;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String isbn() {
        return isbn;
    }

    public LocalDate checkoutDate() {
        return checkoutDate;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public int renewalCount() {
        return renewalCount;
    }

    public LocalDate returnDate() {
        return returnDate;
    }

    public LoanStatus status() {
        if (status == LoanStatus.ACTIVE && LocalDate.now(clock).isAfter(dueDate)) {
            return LoanStatus.OVERDUE;
        }
        return status;
    }

    public void renew(LocalDate newDueDate) {
        LoanStatus current = status();
        if (current == LoanStatus.RETURNED || current == LoanStatus.LOST) {
            throw new IllegalStateException("Cannot renew a " + current + " loan");
        }
        if (newDueDate.isBefore(dueDate) || newDueDate.isEqual(dueDate)) {
            throw new IllegalArgumentException("Renewal must extend the due date");
        }
        dueDate = newDueDate;
        renewalCount++;
        if (status == LoanStatus.OVERDUE) {
            status = LoanStatus.ACTIVE;
        }
    }

    public void markLost() {
        if (status == LoanStatus.RETURNED || status == LoanStatus.LOST) {
            throw new IllegalStateException("Cannot mark a " + status + " loan as lost");
        }
        status = LoanStatus.LOST;
    }

    public void markOverdue() {
        if (status == LoanStatus.ACTIVE) {
            status = LoanStatus.OVERDUE;
        }
    }

    public void restoreRenewalCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Renewal count cannot be negative");
        }
        renewalCount = count;
    }

    public BigDecimal calculateOverdueFine() {
        LocalDate effectiveDate = returnDate == null ? LocalDate.now(clock) : returnDate;
        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, effectiveDate));
        return DAILY_FINE.multiply(BigDecimal.valueOf(overdueDays)).setScale(2, RoundingMode.UNNECESSARY);
    }

    public void markReturned(LocalDate date) {
        if (status == LoanStatus.RETURNED) {
            throw new IllegalStateException("Loan is already returned");
        }
        if (date.isBefore(checkoutDate)) {
            throw new IllegalArgumentException("Return date cannot precede checkout date");
        }
        returnDate = date;
        status = LoanStatus.RETURNED;
    }
}
