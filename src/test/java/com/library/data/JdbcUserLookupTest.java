package com.library.data;

import static org.junit.jupiter.api.Assertions.*;

import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.domain.Role;
import com.library.domain.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcUserLookupTest {
    private JdbcDataSource dataSource;
    private JdbcUserLookup users;

    @BeforeEach
    void createDatabase() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:users;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("""
                    CREATE TABLE users (
                        id UUID PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        email VARCHAR(320) NOT NULL UNIQUE,
                        password_hash VARCHAR(500) NOT NULL,
                        role VARCHAR(20) NOT NULL
                    )
                    """);
        }
        users = new JdbcUserLookup(dataSource, 7);
    }

    @Test
    void mapsEveryPersistedRoleToTheCorrectDomainType() throws Exception {
        insert("member@example.edu", Role.MEMBER);
        insert("librarian@example.edu", Role.LIBRARIAN);
        insert("admin@example.edu", Role.ADMIN);

        User member = users.findByEmail("member@example.edu").orElseThrow();
        User librarian = users.findByEmail("librarian@example.edu").orElseThrow();
        User admin = users.findByEmail("admin@example.edu").orElseThrow();

        assertInstanceOf(Member.class, member);
        assertEquals(7, ((Member) member).borrowingLimit());
        assertInstanceOf(Librarian.class, librarian);
        assertEquals(Role.LIBRARIAN, librarian.role());
        assertEquals(Role.ADMIN, admin.role());
    }

    @Test
    void emailLookupUsesAParameterAndCannotBeInjected() throws Exception {
        insert("member@example.edu", Role.MEMBER);

        assertTrue(users.findByEmail("' OR 1=1 --").isEmpty());
        assertTrue(users.findByEmail("member@example.edu").isPresent());
    }

    private void insert(String email, Role role) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, role + " User");
            statement.setString(3, email);
            statement.setString(4, "$argon2id$test");
            statement.setString(5, role.name());
            statement.executeUpdate();
        }
    }
}
