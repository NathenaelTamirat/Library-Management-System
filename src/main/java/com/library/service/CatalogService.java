package com.library.service;

import com.library.data.BookRepository;
import com.library.data.LoanTransactionManager;
import com.library.domain.Book;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
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
        return books.findByIsbn(isbn);
    }

    public Book add(User actor, Book book) throws SQLException {
        authorization.require(actor, Permission.MANAGE_CATALOG);
        requireAuthor(book);
        if (books.findByIsbn(book.isbn()).isPresent()) {
            throw new IllegalStateException("Book already exists: " + book.isbn());
        }
        books.save(book);
        audit.record(actor.id(), "ADD_BOOK", "{\"isbn\":\"" + book.isbn() + "\"}");
        return book;
    }

    public Book update(User actor, Book book) throws SQLException {
        authorization.require(actor, Permission.MANAGE_CATALOG);
        requireAuthor(book);
        if (books.findByIsbn(book.isbn()).isEmpty()) {
            throw new IllegalStateException("Book does not exist: " + book.isbn());
        }
        int checkedOut = loans.countOpenLoansByIsbn(book.isbn());
        if (book.totalCopies() < checkedOut) {
            throw new IllegalStateException(
                    "Total copies cannot drop below checked-out copies (" + checkedOut + ")");
        }
        if (book.totalCopies() - book.availableCopies() < checkedOut) {
            throw new IllegalStateException(
                    "Available copies imply fewer checked-out copies than open loans ("
                            + checkedOut + ")");
        }
        books.update(book);
        audit.record(actor.id(), "UPDATE_BOOK", "{\"isbn\":\"" + book.isbn() + "\"}");
        return book;
    }

    private static void requireAuthor(Book book) {
        if (book.author() == null || book.author().isBlank()) {
            throw new IllegalArgumentException("Author is required");
        }
    }

    public void delete(User actor, String isbn) throws SQLException {
        authorization.require(actor, Permission.MANAGE_CATALOG);
        int open = loans.countOpenLoansByIsbn(isbn);
        if (open > 0) {
            throw new IllegalStateException(
                    "Cannot delete book with " + open + " open loan(s): " + isbn);
        }
        books.delete(isbn);
        audit.record(actor.id(), "DELETE_BOOK", "{\"isbn\":\"" + isbn + "\"}");
    }
}
