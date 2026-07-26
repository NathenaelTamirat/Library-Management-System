package com.library.domain;

import java.util.Optional;

public record ReturnReceipt(Loan loan, Optional<Fine> fine) {
    public ReturnReceipt {
        if (loan == null) {
            throw new IllegalArgumentException("Loan is required");
        }
        if (fine == null) {
            fine = Optional.empty();
        }
    }
}
