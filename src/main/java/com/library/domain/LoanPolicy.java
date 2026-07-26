package com.library.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record LoanPolicy(
        int loanDays,
        BigDecimal dailyFine,
        BigDecimal replacementFine,
        int maxRenewals,
        int borrowLimit) {
    public LoanPolicy {
        if (loanDays < 1) {
            throw new IllegalArgumentException("Loan days must be positive");
        }
        Objects.requireNonNull(dailyFine);
        Objects.requireNonNull(replacementFine);
        if (dailyFine.signum() < 0 || replacementFine.signum() < 0) {
            throw new IllegalArgumentException("Fine amounts cannot be negative");
        }
        if (maxRenewals < 0) {
            throw new IllegalArgumentException("Max renewals cannot be negative");
        }
        if (borrowLimit < 1) {
            throw new IllegalArgumentException("Borrow limit must be positive");
        }
    }

    public static LoanPolicy defaults() {
        return new LoanPolicy(14, new BigDecimal("0.50"), new BigDecimal("50.00"), 2, 5);
    }
}
