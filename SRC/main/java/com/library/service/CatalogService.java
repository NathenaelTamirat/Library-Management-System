package com.library.service;

import com.library.data.BookRepository;
import com.library.domain.Book;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class CatalogService {
    private final BookRepository books;

    public CatalogService(BookRepository books) {
        this.books = books;
    }

    public List<Book> search(String query) throws SQLException {
        String normalized = query == null ? "" : query.strip();
        return books.search(normalized);
    }

    public Optional<Book> findByIsbn(String isbn) throws SQLException {
        return books.findByIsbn(isbn);
    }
}
