package com.library.service;

import com.library.data.BookRepository;
import com.library.domain.Book;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class CatalogService {
    private final BookRepository books;
    private final AuthorizationService authorization;

    public CatalogService(BookRepository books, AuthorizationService authorization) {
        this.books = books;
        this.authorization = authorization;
    }

    public List<Book> search(String query) throws SQLException {
        String normalized = query == null ? "" : query.strip();
        return books.search(normalized);
    }

    public Optional<Book> findByIsbn(String isbn) throws SQLException {
        return books.findByIsbn(isbn);
    }

    public Book add(User actor, Book book) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_CATALOG);
        if (books.findByIsbn(book.isbn()).isPresent()) {
            throw new IllegalStateException("Book already exists: " + book.isbn());
        }
        books.save(book);
        return book;
    }

    public Book update(User actor, Book book) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_CATALOG);
        if (books.findByIsbn(book.isbn()).isEmpty()) {
            throw new IllegalStateException("Book does not exist: " + book.isbn());
        }
        books.update(book);
        return book;
    }

    public void delete(User actor, String isbn) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_CATALOG);
        books.delete(isbn);
    }
}
