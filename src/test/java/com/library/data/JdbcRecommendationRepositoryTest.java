package com.library.data;

import static org.junit.jupiter.api.Assertions.*;

import com.library.domain.BookRecommendation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcRecommendationRepositoryTest {
    private JdbcDataSource dataSource;
    private JdbcRecommendationRepository recommendations;
    private UUID memberId;
    private UUID otherUserId;

    @BeforeEach
    void createDatabase() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:recommendations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        memberId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS loans");
            statement.execute("DROP TABLE IF EXISTS books");
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            statement.execute("""
                    CREATE TABLE books (
                        isbn VARCHAR(20) PRIMARY KEY,
                        title VARCHAR(500) NOT NULL,
                        author VARCHAR(300) NOT NULL,
                        total_copies INTEGER NOT NULL,
                        available_copies INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE loans (
                        id UUID PRIMARY KEY,
                        user_id UUID NOT NULL REFERENCES users(id),
                        isbn VARCHAR(20) NOT NULL REFERENCES books(isbn)
                    )
                    """);
        }
        insertUser(memberId);
        insertUser(otherUserId);
        insertBook("1111111111", "Read Foundation", "Isaac Asimov", 1, 0);
        insertBook("2222222222", "Unread Robots", "Isaac Asimov", 2, 1);
        insertBook("3333333333", "Popular Dune", "Frank Herbert", 3, 2);
        insertBook("4444444444", "Unavailable Robots", "Isaac Asimov", 1, 0);
        insertLoan(memberId, "1111111111");
        insertLoan(otherUserId, "3333333333");
        insertLoan(otherUserId, "3333333333");
        recommendations = new JdbcRecommendationRepository(dataSource);
    }

    @Test
    void historyRecommendationsPreferUnreadAvailableBooksByKnownAuthors() throws Exception {
        List<BookRecommendation> result =
                recommendations.findByReadingHistory(memberId, 10);

        assertEquals(1, result.size());
        assertEquals("2222222222", result.get(0).book().isbn());
        assertTrue(result.get(0).reason().contains("Isaac Asimov"));
        assertTrue(result.get(0).score() >= 100);
    }

    @Test
    void popularFallbackExcludesPreviouslyReadAndUnavailableBooks() throws Exception {
        List<BookRecommendation> result =
                recommendations.findPopularUnread(memberId, 10);

        assertEquals(List.of("3333333333", "2222222222"),
                result.stream().map(item -> item.book().isbn()).toList());
        assertEquals("Popular with 2 checkout(s)", result.get(0).reason());
    }

    @Test
    void coldStartMemberReceivesPopularAvailableBooks() throws Exception {
        UUID newMember = UUID.randomUUID();
        insertUser(newMember);

        List<BookRecommendation> result =
                recommendations.findPopularUnread(newMember, 1);

        assertEquals(1, result.size());
        assertEquals("3333333333", result.get(0).book().isbn());
    }

    private void insertUser(UUID userId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement("INSERT INTO users (id) VALUES (?)")) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    private void insertBook(
            String isbn, String title, String author, int totalCopies, int availableCopies)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO books
                         (isbn, title, author, total_copies, available_copies)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, isbn);
            statement.setString(2, title);
            statement.setString(3, author);
            statement.setInt(4, totalCopies);
            statement.setInt(5, availableCopies);
            statement.executeUpdate();
        }
    }

    private void insertLoan(UUID userId, String isbn) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO loans (id, user_id, isbn) VALUES (?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, isbn);
            statement.executeUpdate();
        }
    }
}
