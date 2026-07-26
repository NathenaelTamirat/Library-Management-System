package com.library.data;

import com.library.domain.Book;
import com.library.domain.BookRecommendation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcRecommendationRepository implements RecommendationRepository {
    private static final String PERSONALIZED = """
            WITH preferred_authors AS (
                SELECT b.author, COUNT(*) AS affinity
                FROM loans member_loans
                INNER JOIN books b ON b.isbn = member_loans.isbn
                WHERE member_loans.user_id = ?
                GROUP BY b.author
            )
            SELECT b.isbn, b.title, b.author, b.total_copies, b.available_copies,
                   preferred.affinity, COUNT(all_loans.id) AS checkout_count
            FROM preferred_authors preferred
            INNER JOIN books b ON b.author = preferred.author
            LEFT JOIN loans all_loans ON all_loans.isbn = b.isbn
            WHERE b.available_copies > 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM loans previous
                  WHERE previous.user_id = ? AND previous.isbn = b.isbn
              )
            GROUP BY b.isbn, b.title, b.author, b.total_copies, b.available_copies,
                     preferred.affinity
            ORDER BY preferred.affinity DESC, checkout_count DESC, b.title
            LIMIT ?
            """;
    private static final String POPULAR = """
            SELECT b.isbn, b.title, b.author, b.total_copies, b.available_copies,
                   COUNT(all_loans.id) AS checkout_count
            FROM books b
            LEFT JOIN loans all_loans ON all_loans.isbn = b.isbn
            WHERE b.available_copies > 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM loans previous
                  WHERE previous.user_id = ? AND previous.isbn = b.isbn
              )
            GROUP BY b.isbn, b.title, b.author, b.total_copies, b.available_copies
            ORDER BY checkout_count DESC, b.title
            LIMIT ?
            """;

    private final DataSource dataSource;

    public JdbcRecommendationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<BookRecommendation> findByReadingHistory(UUID memberId, int limit)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PERSONALIZED)) {
            statement.setObject(1, memberId);
            statement.setObject(2, memberId);
            statement.setInt(3, limit);
            try (ResultSet results = statement.executeQuery()) {
                List<BookRecommendation> recommendations = new ArrayList<>();
                while (results.next()) {
                    long affinity = results.getLong("affinity");
                    long popularity = results.getLong("checkout_count");
                    Book book = mapBook(results);
                    recommendations.add(new BookRecommendation(
                            book,
                            affinity * 100 + popularity,
                            "Because you read " + affinity + " book(s) by " + book.author()));
                }
                return recommendations;
            }
        }
    }

    @Override
    public List<BookRecommendation> findPopularUnread(UUID memberId, int limit)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(POPULAR)) {
            statement.setObject(1, memberId);
            statement.setInt(2, limit);
            try (ResultSet results = statement.executeQuery()) {
                List<BookRecommendation> recommendations = new ArrayList<>();
                while (results.next()) {
                    long popularity = results.getLong("checkout_count");
                    recommendations.add(new BookRecommendation(
                            mapBook(results),
                            popularity,
                            popularity == 0
                                    ? "Available in the library"
                                    : "Popular with " + popularity + " checkout(s)"));
                }
                return recommendations;
            }
        }
    }

    private static Book mapBook(ResultSet results) throws SQLException {
        return new Book(
                results.getString("isbn"),
                results.getString("title"),
                results.getString("author"),
                results.getInt("total_copies"),
                results.getInt("available_copies"));
    }
}
