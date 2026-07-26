package com.library.security;

import com.library.data.UserAdminRepository;
import com.library.data.UserAdminRepository.UserRecord;
import com.library.domain.User;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

public final class AuthenticationService {
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserLookup users;
    private final UserAdminRepository accounts;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public AuthenticationService(UserLookup users, PasswordHasher passwordHasher) {
        this(users, null, passwordHasher, Clock.systemUTC());
    }

    public AuthenticationService(
            UserLookup users,
            UserAdminRepository accounts,
            PasswordHasher passwordHasher,
            Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    public Optional<User> authenticate(String email, char[] password) {
        if (email == null || email.isBlank() || password == null) {
            wipe(password);
            return Optional.empty();
        }
        String normalized = email.strip().toLowerCase();
        try {
            if (accounts != null) {
                Optional<UserRecord> record = accounts.findRecordByEmail(normalized);
                if (record.isEmpty() || !record.orElseThrow().active()) {
                    wipe(password);
                    return Optional.empty();
                }
                UserRecord account = record.orElseThrow();
                Instant now = clock.instant();
                if (account.lockedUntil() != null && account.lockedUntil().isAfter(now)) {
                    wipe(password);
                    return Optional.empty();
                }
                char[] passwordCopy = Arrays.copyOf(password, password.length);
                boolean matched = passwordHasher.verify(account.passwordHash(), password);
                if (matched) {
                    accounts.clearFailedLogins(account.id());
                    return users.findByEmail(normalized);
                }
                int attempts = account.failedLoginAttempts() + 1;
                Instant lockedUntil = attempts >= MAX_FAILED_ATTEMPTS
                        ? now.plus(LOCKOUT_DURATION)
                        : null;
                accounts.recordFailedLogin(account.id(), attempts, lockedUntil);
                wipe(passwordCopy);
                return Optional.empty();
            }
            return users.findByEmail(normalized)
                    .filter(user -> passwordHasher.verify(user.passwordHash(), password));
        } catch (SQLException failure) {
            wipe(password);
            throw new IllegalStateException("Unable to authenticate", failure);
        }
    }

    public void changePassword(User actor, char[] currentPassword, char[] newPassword)
            throws SQLException {
        if (accounts == null) {
            throw new IllegalStateException("Password changes require an account repository");
        }
        UserRecord record = accounts.findRecordById(actor.id())
                .orElseThrow(() -> new IllegalStateException("User not found: " + actor.id()));
        char[] currentCopy = currentPassword == null
                ? null
                : Arrays.copyOf(currentPassword, currentPassword.length);
        boolean matches = passwordHasher.verify(record.passwordHash(), currentPassword);
        if (!matches) {
            wipe(currentCopy);
            wipe(newPassword);
            throw new SecurityException("Current password is incorrect");
        }
        String hash = passwordHasher.hash(newPassword);
        accounts.updatePasswordHash(actor.id(), hash);
        accounts.clearFailedLogins(actor.id());
        wipe(currentCopy);
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    @FunctionalInterface
    public interface UserLookup {
        Optional<User> findByEmail(String email);
    }
}
