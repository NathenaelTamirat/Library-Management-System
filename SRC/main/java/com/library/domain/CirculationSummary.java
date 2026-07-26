package com.library.domain;

import java.math.BigDecimal;

public record CirculationSummary(
        long openLoans,
        long overdueLoans,
        long unpaidFines,
        BigDecimal unpaidFineTotal,
        long availableCopies,
        long totalCopies) {
    public CirculationSummary {
        if (unpaidFineTotal == null) {
            unpaidFineTotal = BigDecimal.ZERO;
        }
    }
}
