package com.library.domain;

import java.util.Objects;

public final class Book {
    private final String isbn;
    private String title;
    private String author;
    private int totalCopies;
    private int availableCopies;
    private String genre;
    private Integer publicationYear;
    private String publisher;
    private String subject;

    public Book(String isbn, String title, String author, int totalCopies, int availableCopies) {
        this(isbn, title, author, totalCopies, availableCopies, null, null);
    }

    public Book(
            String isbn,
            String title,
            String author,
            int totalCopies,
            int availableCopies,
            String genre,
            Integer publicationYear) {
        this(isbn, title, author, totalCopies, availableCopies, genre, publicationYear, null, null);
    }

    public Book(
            String isbn,
            String title,
            String author,
            int totalCopies,
            int availableCopies,
            String genre,
            Integer publicationYear,
            String publisher,
            String subject) {
        this.isbn = requireText(isbn, "ISBN");
        this.title = requireText(title, "Title");
        this.author = requireText(author, "Author");
        setInventory(totalCopies, availableCopies);
        setMetadata(genre, publicationYear, publisher, subject);
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

    public String genre() {
        return genre;
    }

    public Integer publicationYear() {
        return publicationYear;
    }

    public String publisher() {
        return publisher;
    }

    public String subject() {
        return subject;
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

    public void setMetadata(String genre, Integer publicationYear) {
        setMetadata(genre, publicationYear, publisher, subject);
    }

    public void setMetadata(
            String genre, Integer publicationYear, String publisher, String subject) {
        this.genre = normalizeOptional(genre);
        if (publicationYear != null && (publicationYear < 0 || publicationYear > 9999)) {
            throw new IllegalArgumentException("Publication year must be between 0 and 9999");
        }
        this.publicationYear = publicationYear;
        this.publisher = normalizeOptional(publisher);
        this.subject = normalizeOptional(subject);
    }

    private void setInventory(int totalCopies, int availableCopies) {
        if (totalCopies < 0 || availableCopies < 0 || availableCopies > totalCopies) {
            throw new IllegalArgumentException("Inventory must satisfy 0 <= available <= total");
        }
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
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
