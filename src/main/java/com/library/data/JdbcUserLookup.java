package com.library.data;

import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.domain.Role;
import com.library.domain.User;
import com.library.security.AuthenticationService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcUserLookup implements AuthenticationService.UserLookup {
    private static final String FIND_BY_EMAIL = """
            SELECT id, name, email, password_hash, role
            FROM users
            WHERE email = ?
            """;

    private final DataSource dataSource;
    private final int borrowingLimit;

    public JdbcUserLookup(DataSource dataSource, int borrowingLimit) {
        this.dataSource = dataSource;
        this.borrowingLimit = borrowingLimit;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_EMAIL)) {
            statement.setString(1, email);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new UserLookupException("Unable to load user", failure);
        }
    }

    private User map(ResultSet results) throws SQLException {
        UUID id = results.getObject("id", UUID.class);
        String name = results.getString("name");
        String email = results.getString("email");
        String passwordHash = results.getString("password_hash");
        Role role = Role.valueOf(results.getString("role"));
        return switch (role) {
            case MEMBER -> new Member(id, name, email, passwordHash, borrowingLimit);
            case LIBRARIAN -> new Librarian(
                    id, name, email, passwordHash, "LIB-" + id, false);
            case ADMIN -> new Librarian(
                    id, name, email, passwordHash, "ADMIN-" + id, true);
        };
    }

    public static final class UserLookupException extends RuntimeException {
        public UserLookupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
