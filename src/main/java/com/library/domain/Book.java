package com.library.domain;

import java.util.Objects;

public final class Book {
    private final String isbn;
    private String title;
    private String author;
    private int totalCopies;
    private int availableCopies;

    public Book(String isbn, String title, String author, int totalCopies, int availableCopies) {
        this.isbn = requireText(isbn, "ISBN");
        this.title = requireText(title, "Title");
        this.author = requireText(author, "Author");
        setInventory(totalCopies, availableCopies);
    }

    public String isbn() {
        return isbn;
    }

    public String title() {
        return title;
    }

    public String author() {
        return author;
    }

    public int totalCopies() {
        return totalCopies;
    }

    public int availableCopies() {
        return availableCopies;
    }

    public boolean isAvailable() {
        return availableCopies > 0;
    }

    public void checkOutCopy() {
        if (!isAvailable()) {
            throw new IllegalStateException("No copy is available");
        }
        availableCopies--;
    }

    public void returnCopy() {
        if (availableCopies == totalCopies) {
            throw new IllegalStateException("All copies are already available");
        }
        availableCopies++;
    }

    public void rename(String title, String author) {
        this.title = requireText(title, "Title");
        this.author = requireText(author, "Author");
    }

    private void setInventory(int totalCopies, int availableCopies) {
        if (totalCopies < 0 || availableCopies < 0 || availableCopies > totalCopies) {
            throw new IllegalArgumentException("Inventory must satisfy 0 <= available <= total");
        }
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Book book && isbn.equals(book.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isbn);
    }
}
