package com.library.data;

import static org.junit.jupiter.api.Assertions.*;

import com.library.domain.Role;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcUserAdminRepositoryTest {
    private JdbcUserAdminRepository users;

    @BeforeEach
    void createDatabase() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:useradmin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS users");
            statement.execute("""
                    CREATE TABLE users (
                        id UUID PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        email VARCHAR(320) NOT NULL UNIQUE,
                        password_hash VARCHAR(500) NOT NULL,
                        role VARCHAR(20) NOT NULL,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        failed_login_attempts INTEGER NOT NULL DEFAULT 0,
                        locked_until TIMESTAMP
                    )
                    """);
        }
        users = new JdbcUserAdminRepository(dataSource);
    }

    @Test
    void createDeactivateAndChangeRolePersist() throws Exception {
        UUID id = users.create("Ada", "ada@example.edu", "hash", Role.MEMBER);

        users.changeRole(id, Role.LIBRARIAN);
        users.setActive(id, false);

        UserAdminRepository.UserRecord record = users.findRecordById(id).orElseThrow();
        assertEquals(Role.LIBRARIAN, record.role());
        assertFalse(record.active());
        assertEquals(1, users.listUsers().size());
    }
}
