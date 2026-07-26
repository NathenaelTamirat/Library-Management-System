package com.library.data;

import com.library.domain.Book;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcBookRepository implements BookRepository {
    private final DataSource dataSource;

    public JdbcBookRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) throws SQLException {
        String sql = """
                SELECT isbn, title, author, total_copies, available_copies
                FROM books
                WHERE isbn = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, isbn);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Book> search(String query) throws SQLException {
        String sql = """
                SELECT isbn, title, author, total_copies, available_copies
                FROM books
                WHERE LOWER(title) LIKE ? OR LOWER(author) LIKE ? OR isbn = ?
                ORDER BY title
                """;
        String pattern = "%" + query.toLowerCase() + "%";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            statement.setString(3, query);
            try (ResultSet results = statement.executeQuery()) {
                List<Book> books = new ArrayList<>();
                while (results.next()) {
                    books.add(map(results));
                }
                return books;
            }
        }
    }

    @Override
    public void save(Book book) throws SQLException {
        String sql = """
                INSERT INTO books (isbn, title, author, total_copies, available_copies)
                VALUES (?, ?, ?, ?, ?)
                """;
        executeUpdate(sql, book);
    }

    @Override
    public void update(Book book) throws SQLException {
        String sql = """
                UPDATE books
                SET title = ?, author = ?, total_copies = ?, available_copies = ?
                WHERE isbn = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, book.title());
            statement.setString(2, book.author());
            statement.setInt(3, book.totalCopies());
            statement.setInt(4, book.availableCopies());
            statement.setString(5, book.isbn());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(String isbn) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM books WHERE isbn = ?")) {
            statement.setString(1, isbn);
            statement.executeUpdate();
        }
    }

    private void executeUpdate(String sql, Book book) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, book.isbn());
            statement.setString(2, book.title());
            statement.setString(3, book.author());
            statement.setInt(4, book.totalCopies());
            statement.setInt(5, book.availableCopies());
            statement.executeUpdate();
        }
    }

    private static Book map(ResultSet results) throws SQLException {
        return new Book(
                results.getString("isbn"),
                results.getString("title"),
                results.getString("author"),
                results.getInt("total_copies"),
                results.getInt("available_copies"));
    }
}
