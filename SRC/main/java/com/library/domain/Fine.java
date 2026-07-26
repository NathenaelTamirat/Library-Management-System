package com.library.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class Fine {
    private final UUID id;
    private final UUID loanId;
    private final BigDecimal amount;
    private final boolean paid;
    private final LocalDate issuedDate;

    public Fine(UUID id, UUID loanId, BigDecimal amount, boolean paid, LocalDate issuedDate) {
        this.id = Objects.requireNonNull(id);
        this.loanId = Objects.requireNonNull(loanId);
        this.amount = Objects.requireNonNull(amount);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Fine amount cannot be negative");
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

    public boolean paid() {
        return paid;
    }

    public LocalDate issuedDate() {
        return issuedDate;
    }
}
