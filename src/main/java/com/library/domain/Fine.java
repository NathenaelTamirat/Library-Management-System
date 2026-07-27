package com.library.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class Fine {
    private final UUID id;
    private final UUID loanId;
    private final BigDecimal amount;
    private final BigDecimal amountPaid;
    private final boolean paid;
    private final LocalDate issuedDate;

    public Fine(UUID id, UUID loanId, BigDecimal amount, boolean paid, LocalDate issuedDate) {
        this(id, loanId, amount, paid ? amount : BigDecimal.ZERO, paid, issuedDate);
    }

    public Fine(
            UUID id,
            UUID loanId,
            BigDecimal amount,
            BigDecimal amountPaid,
            boolean paid,
            LocalDate issuedDate) {
        this.id = Objects.requireNonNull(id);
        this.loanId = Objects.requireNonNull(loanId);
        this.amount = Objects.requireNonNull(amount);
        this.amountPaid = Objects.requireNonNull(amountPaid);
        if (amount.signum() < 0 || amountPaid.signum() < 0) {
            throw new IllegalArgumentException("Fine amounts cannot be negative");
        }
        if (amountPaid.compareTo(amount) > 0) {
            throw new IllegalArgumentException("Paid amount cannot exceed fine amount");
        }
        this.paid = paid;
        this.issuedDate = Objects.requireNonNull(issuedDate);
    }

    public UUID id() {
        return id;
    }

    public UUID loanId() {
        return loanId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public BigDecimal amountPaid() {
        return amountPaid;
    }

    public BigDecimal remaining() {
        if (paid) {
            return BigDecimal.ZERO;
        }
        return amount.subtract(amountPaid).max(BigDecimal.ZERO);
    }

    public boolean paid() {
        return paid;
    }

    public LocalDate issuedDate() {
        return issuedDate;
    }
}
