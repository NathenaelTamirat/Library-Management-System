package com.library.data;

import com.library.domain.Book;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcBookRepository implements BookRepository {
    private static final String COLUMNS =
            "isbn, title, author, total_copies, available_copies, genre, publication_year, "
                    + "publisher, subject";
    private static final String SEARCH_LIKE = """
            SELECT isbn, title, author, total_copies, available_copies, genre, publication_year,
                   publisher, subject
            FROM books
            WHERE LOWER(title) LIKE ?
               OR LOWER(author) LIKE ?
               OR LOWER(COALESCE(genre, '')) LIKE ?
               OR CAST(publication_year AS VARCHAR) LIKE ?
               OR LOWER(COALESCE(publisher, '')) LIKE ?
               OR LOWER(COALESCE(subject, '')) LIKE ?
               OR isbn = ?
            ORDER BY title
            """;
    private static final String SEARCH_FTS = """
            SELECT isbn, title, author, total_copies, available_copies, genre, publication_year,
                   publisher, subject,
                   ts_rank(search_document, plainto_tsquery('english', ?)) AS rank
            FROM books
            WHERE search_document @@ plainto_tsquery('english', ?)
               OR isbn = ?
            ORDER BY rank DESC, title
            """;

    private final DataSource dataSource;
    private final boolean postgresFullText;

    public JdbcBookRepository(DataSource dataSource) {
        this(dataSource, false);
    }

    public JdbcBookRepository(DataSource dataSource, boolean postgresFullText) {
        this.dataSource = dataSource;
        this.postgresFullText = postgresFullText;
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM books WHERE isbn = ?";
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
        if (postgresFullText && query != null && !query.isBlank()) {
            return searchFullText(query);
        }
        return searchLike(query == null ? "" : query);
    }

    private List<Book> searchLike(String query) throws SQLException {
        String pattern = "%" + query.toLowerCase() + "%";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SEARCH_LIKE)) {
            statement.setString(1, pattern);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            statement.setString(4, pattern);
            statement.setString(5, pattern);
            statement.setString(6, pattern);
            statement.setString(7, query);
            return readBooks(statement);
        }
    }

    private List<Book> searchFullText(String query) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SEARCH_FTS)) {
            statement.setString(1, query);
            statement.setString(2, query);
            statement.setString(3, query);
            return readBooks(statement);
        }
    }

    private static List<Book> readBooks(PreparedStatement statement) throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            List<Book> books = new ArrayList<>();
            while (results.next()) {
                books.add(map(results));
            }
            return books;
        }
    }

    @Override
    public void save(Book book) throws SQLException {
        String sql = """
                INSERT INTO books (
                    isbn, title, author, total_copies, available_copies, genre, publication_year,
                    publisher, subject)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        executeUpdate(sql, book);
    }

    @Override
    public void update(Book book) throws SQLException {
        String lockSql = "SELECT isbn FROM books WHERE isbn = ? FOR UPDATE";
        String sql = """
                UPDATE books
                SET title = ?, author = ?, total_copies = ?, available_copies = ?,
                    genre = ?, publication_year = ?, publisher = ?, subject = ?
                WHERE isbn = ?
                """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                    lock.setString(1, book.isbn());
                    try (ResultSet results = lock.executeQuery()) {
                        if (!results.next()) {
                            throw new SQLException("Book not found: " + book.isbn());
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, book.title());
                    statement.setString(2, book.author());
                    statement.setInt(3, book.totalCopies());
                    statement.setInt(4, book.availableCopies());
                    setOptionalText(statement, 5, book.genre());
                    setOptionalYear(statement, 6, book.publicationYear());
                    setOptionalText(statement, 7, book.publisher());
                    setOptionalText(statement, 8, book.subject());
                    statement.setString(9, book.isbn());
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Book could not be updated: " + book.isbn());
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
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
            setOptionalText(statement, 6, book.genre());
            setOptionalYear(statement, 7, book.publicationYear());
            setOptionalText(statement, 8, book.publisher());
            setOptionalText(statement, 9, book.subject());
            statement.executeUpdate();
        }
    }

    private static void setOptionalText(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setOptionalYear(PreparedStatement statement, int index, Integer year)
            throws SQLException {
        if (year == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, year);
        }
    }

    private static Book map(ResultSet results) throws SQLException {
        int year = results.getInt("publication_year");
        Integer publicationYear = results.wasNull() ? null : year;
        return new Book(
                results.getString("isbn"),
                results.getString("title"),
                results.getString("author"),
                results.getInt("total_copies"),
                results.getInt("available_copies"),
                results.getString("genre"),
                publicationYear,
                results.getString("publisher"),
                results.getString("subject"));
    }
}
