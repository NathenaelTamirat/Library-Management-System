package com.library.data;

import static org.junit.jupiter.api.Assertions.*;

import com.library.domain.Book;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcBookRepositoryTest {
    private JdbcDataSource dataSource;
    private JdbcBookRepository repository;

    @BeforeEach
    void createDatabase() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:books;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS books");
            statement.execute("""
                    CREATE TABLE books (
                        isbn VARCHAR(20) PRIMARY KEY,
                        title VARCHAR(500) NOT NULL,
                        author VARCHAR(300) NOT NULL,
                        total_copies INTEGER NOT NULL,
                        available_copies INTEGER NOT NULL,
                        genre VARCHAR(100),
                        publication_year INTEGER,
                        publisher TEXT,
                        subject TEXT,
                        CHECK (available_copies BETWEEN 0 AND total_copies)
                    )
                    """);
        }
        repository = new JdbcBookRepository(dataSource);
    }

    @Test
    void persistsMetadataAndSearchesByGenreOrYear() throws Exception {
        Book book = new Book(
                "9780134685991", "Effective Java", "Joshua Bloch", 1, 1, "Programming", 2018);
        repository.save(book);

        Book loaded = repository.findByIsbn(book.isbn()).orElseThrow();
        assertEquals("Programming", loaded.genre());
        assertEquals(2018, loaded.publicationYear());
        assertEquals(List.of(book), repository.search("programming"));
        assertEquals(List.of(book), repository.search("2018"));
    }

    @Test
    void insertsUpdatesAndSearchesByPublisherOrSubject() throws Exception {
        Book book = new Book(
                "9780134685991",
                "Effective Java",
                "Joshua Bloch",
                1,
                1,
                "Programming",
                2018,
                "Addison-Wesley",
                "Java language");
        repository.save(book);

        Book loaded = repository.findByIsbn(book.isbn()).orElseThrow();
        assertEquals("Addison-Wesley", loaded.publisher());
        assertEquals("Java language", loaded.subject());
        assertEquals(List.of(book), repository.search("addison"));
        assertEquals(List.of(book), repository.search("java language"));

        Book revised = new Book(
                book.isbn(),
                book.title(),
                book.author(),
                book.totalCopies(),
                book.availableCopies(),
                book.genre(),
                book.publicationYear(),
                "Pearson",
                "Software engineering");
        repository.update(revised);

        Book updated = repository.findByIsbn(book.isbn()).orElseThrow();
        assertEquals("Pearson", updated.publisher());
        assertEquals("Software engineering", updated.subject());
        assertEquals(List.of(revised), repository.search("pearson"));
        assertEquals(List.of(revised), repository.search("software engineering"));
        assertTrue(repository.search("addison").isEmpty());
    }

    @Test
    void persistsSearchesUpdatesAndDeletesBooks() throws Exception {
        Book book = new Book("9780134685991", "Effective Java", "Joshua Bloch", 3, 2);
        repository.save(book);

        assertEquals(book, repository.findByIsbn(book.isbn()).orElseThrow());
        assertEquals(List.of(book), repository.search("bloch"));

        book.rename("Effective Java, Third Edition", "Joshua Bloch");
        repository.update(book);
        assertEquals("Effective Java, Third Edition",
                repository.findByIsbn(book.isbn()).orElseThrow().title());

        repository.delete(book.isbn());
        assertTrue(repository.findByIsbn(book.isbn()).isEmpty());
    }

    @Test
    void searchInputCannotEscapePreparedStatement() throws Exception {
        Book book = new Book("9780134685991", "Effective Java", "Joshua Bloch", 1, 1);
        repository.save(book);

        assertTrue(repository.search("x%' OR 1=1; DROP TABLE books; --").isEmpty());
        assertEquals(book, repository.findByIsbn(book.isbn()).orElseThrow());
    }

    @Test
    void updateLocksBookRowBeforeChangingInventory() throws Exception {
        Book book = new Book("9780134685991", "Effective Java", "Joshua Bloch", 2, 1);
        repository.save(book);
        book.rename("Effective Java 3e", "Joshua Bloch");
        Book revised = new Book(
                book.isbn(), book.title(), book.author(), 3, 2, book.genre(), book.publicationYear());
        repository.update(revised);
        Book loaded = repository.findByIsbn(book.isbn()).orElseThrow();
        assertEquals(3, loaded.totalCopies());
        assertEquals(2, loaded.availableCopies());
        assertEquals("Effective Java 3e", loaded.title());
    }

    @Test
    void h2KeepsSafeLikeFallbackWhenFullTextIsDisabled() throws Exception {
        Book book = new Book("9780134685991", "Effective Java", "Joshua Bloch", 1, 1);
        repository.save(book);

        assertEquals(List.of(book), new JdbcBookRepository(dataSource, false).search("ffective"));
    }

    @Test
    void databaseConfigurationRequiresPostgresqlAndPositivePoolSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new DataSourceFactory.DatabaseConfig("jdbc:h2:mem:test", "sa", "", 10));
        assertThrows(IllegalArgumentException.class,
                () -> new DataSourceFactory.DatabaseConfig(
                        "jdbc:postgresql://localhost/library", "library", "secret", 0));
    }
}
