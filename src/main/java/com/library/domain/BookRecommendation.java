package com.library.domain;

import java.util.Objects;

public record BookRecommendation(Book book, long score, String reason) {
    public BookRecommendation {
        Objects.requireNonNull(book, "Book is required");
        if (score < 0) {
            throw new IllegalArgumentException("Recommendation score cannot be negative");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Recommendation reason is required");
        }
        reason = reason.strip();
    }
}
