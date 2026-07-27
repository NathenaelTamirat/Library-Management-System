package com.library.service;

import com.library.data.BookRepository;
import com.library.data.LoanTransactionManager;
import com.library.domain.Book;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.time.Year;
import java.util.List;
import java.util.Optional;

public final class CatalogService {
    private final BookRepository books;
    private final LoanTransactionManager loans;
    private final AuthorizationService authorization;
    private final AuditService audit;

    public CatalogService(
            BookRepository books,
            LoanTransactionManager loans,
            AuthorizationService authorization,
            AuditService audit) {
        this.books = books;
        this.loans = loans;
        this.authorization = authorization;
        this.audit = audit;
    }

    public List<Book> search(String query) throws SQLException {
        String normalized = query == null ? "" : query.strip();
        return books.search(normalized);
    }

    public Optional<Book> findByIsbn(String isbn) throws SQLException {
        return books.findByIsbn(normalizeIsbn(isbn));
    }

    public Book add(User actor, Book book) throws SQLException {
        authorization.require(actor, Permission.MANAGE_CATALOG);
        Book normalized = withNormalizedIsbn(book);
        requireAuthor(normalized);
        validatePublicationYear(normalized.publicationYear());
        if (books.findByIsbn(normalized.isbn()).isPresent()) {
            throw new IllegalStateException("Book already exists: " + normalized.isbn());
        }
        books.save(normalized);
        audit.record(actor.id(), "ADD_BOOK", "{\"isbn\":\"" + normalized.isbn() + "\"}");
        return normalized;
    }

    public Book update(User actor, Book book) throws SQLException {
        authorization.require(actor, Permission.MANAGE_CATALOG);
        Book normalized = withNormalizedIsbn(book);
        requireAuthor(normalized);
        validatePublicationYear(normalized.publicationYear());
        if (books.findByIsbn(normalized.isbn()).isEmpty()) {
            throw new IllegalStateException("Book does not exist: " + normalized.isbn());
        }
        int checkedOut = loans.countOpenLoansByIsbn(normalized.isbn());
        if (normalized.totalCopies() < checkedOut) {
            throw new IllegalStateException(
                    "Total copies cannot drop below checked-out copies (" + checkedOut + ")");
        }
        if (normalized.totalCopies() - normalized.availableCopies() < checkedOut) {
            throw new IllegalStateException(
                    "Available copies imply fewer checked-out copies than open loans ("
                            + checkedOut + ")");
        }
        books.update(normalized);
        audit.record(actor.id(), "UPDATE_BOOK", "{\"isbn\":\"" + normalized.isbn() + "\"}");
        return normalized;
    }

    private static void requireAuthor(Book book) {
        if (book.author() == null || book.author().isBlank()) {
            throw new IllegalArgumentException("Author is required");
        }
    }

    private static void validatePublicationYear(Integer publicationYear) {
        int maximumYear = Year.now().getValue() + 1;
        if (publicationYear != null && (publicationYear < 1400 || publicationYear > maximumYear)) {
            throw new IllegalArgumentException(
                    "Publication year must be between 1400 and " + maximumYear);
        }
    }

    public void delete(User actor, String isbn) throws SQLException {
        authorization.require(actor, Permission.MANAGE_CATALOG);
        String normalizedIsbn = normalizeIsbn(isbn);
        int open = loans.countOpenLoansByIsbn(normalizedIsbn);
        if (open > 0) {
            throw new IllegalStateException(
                    "Cannot delete book with " + open + " open loan(s): " + normalizedIsbn);
        }
        books.delete(normalizedIsbn);
        audit.record(actor.id(), "DELETE_BOOK", "{\"isbn\":\"" + normalizedIsbn + "\"}");
    }

    static String normalizeIsbn(String isbn) {
        if (isbn == null) {
            throw new IllegalArgumentException("ISBN is required");
        }
        String normalized = isbn.replace("-", "").replace(" ", "").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ISBN is required");
        }
        return normalized;
    }

    private static Book withNormalizedIsbn(Book book) {
        String normalizedIsbn = normalizeIsbn(book.isbn());
        if (normalizedIsbn.equals(book.isbn())) {
            return book;
        }
        return new Book(
                normalizedIsbn,
                book.title(),
                book.author(),
                book.totalCopies(),
                book.availableCopies(),
                book.genre(),
                book.publicationYear(),
                book.publisher(),
                book.subject());
    }
}
