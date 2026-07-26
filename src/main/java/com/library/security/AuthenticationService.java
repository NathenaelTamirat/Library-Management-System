package com.library.security;

import com.library.domain.User;
import java.util.Optional;

public final class AuthenticationService {
    private final UserLookup users;
    private final PasswordHasher passwordHasher;

    public AuthenticationService(UserLookup users, PasswordHasher passwordHasher) {
        this.users = users;
        this.passwordHasher = passwordHasher;
    }

    public Optional<User> authenticate(String email, char[] password) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(email.strip().toLowerCase())
                .filter(user -> passwordHasher.verify(user.passwordHash(), password));
    }

    @FunctionalInterface
    public interface UserLookup {
        Optional<User> findByEmail(String email);
    }
}
