package com.library.data;

import com.library.domain.Book;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface BookRepository {
    Optional<Book> findByIsbn(String isbn) throws SQLException;

    List<Book> search(String query) throws SQLException;

    void save(Book book) throws SQLException;

    void update(Book book) throws SQLException;

    void delete(String isbn) throws SQLException;
}
