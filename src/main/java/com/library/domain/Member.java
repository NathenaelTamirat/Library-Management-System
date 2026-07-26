package com.library.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class Member extends User {
    private final int borrowingLimit;
    private final List<Loan> activeLoans = new ArrayList<>();

    public Member(UUID id, String name, String email, String passwordHash, int borrowingLimit) {
        super(id, name, email, passwordHash, Role.MEMBER);
        if (borrowingLimit < 1) {
            throw new IllegalArgumentException("Borrowing limit must be positive");
        }
        this.borrowingLimit = borrowingLimit;
    }

    public int borrowingLimit() {
        return borrowingLimit;
    }

    public List<Loan> activeLoans() {
        return Collections.unmodifiableList(activeLoans);
    }

    public boolean canBorrow() {
        return activeLoans.size() < borrowingLimit;
    }

    public void addLoan(Loan loan) {
        if (!canBorrow()) {
            throw new IllegalStateException("Borrowing limit reached");
        }
        activeLoans.add(loan);
    }

    public void removeActiveLoan(UUID loanId) {
        activeLoans.removeIf(loan -> loan.id().equals(loanId));
    }

    public BigDecimal currentFinesBalance() {
        return activeLoans.stream()
                .map(Loan::calculateOverdueFine)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
