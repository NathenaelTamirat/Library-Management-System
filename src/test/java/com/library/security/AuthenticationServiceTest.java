package com.library.security;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.UserAdminRepository;
import com.library.domain.Member;
import com.library.domain.Role;
import com.library.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {
    @Test
    void normalizesEmailBeforeLookup() {
        UUID userId = UUID.randomUUID();
        Member member = new Member(userId, "Ada", "ada@example.edu", "HASH", 5);
        String[] lookedUpEmail = new String[1];
        AuthenticationService auth = new AuthenticationService(
                email -> {
                    lookedUpEmail[0] = email;
                    return Optional.of(member);
                },
                new RecordingHasher());

        Optional<User> authenticated =
                auth.authenticate("  ADA@EXAMPLE.EDU  ", "correctpassword".toCharArray());

        assertEquals(Optional.of(member), authenticated);
        assertEquals("ada@example.edu", lookedUpEmail[0]);
    }

    @Test
    void locksAccountAfterRepeatedFailedLogins() throws Exception {
        UUID userId = UUID.randomUUID();
        Member member = new Member(userId, "Ada", "ada@example.edu", "HASH", 5);
        RecordingAccounts accounts = new RecordingAccounts(new UserAdminRepository.UserRecord(
                userId, "Ada", "ada@example.edu", "HASH", Role.MEMBER, true, 0, null));
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);
        AuthenticationService auth = new AuthenticationService(
                email -> Optional.of(member),
                accounts,
                new RecordingHasher(),
                clock);

        for (int i = 0; i < AuthenticationService.MAX_FAILED_ATTEMPTS; i++) {
            assertTrue(auth.authenticate("ada@example.edu", "wrongpassword1".toCharArray()).isEmpty());
        }

        assertEquals(AuthenticationService.MAX_FAILED_ATTEMPTS, accounts.attempts);
        assertEquals(Instant.parse("2026-07-26T12:15:00Z"), accounts.lockedUntil);
        assertTrue(auth.authenticate("ada@example.edu", "correctpassword".toCharArray()).isEmpty());
    }

    @Test
    void changePasswordRequiresCurrentPassword() throws Exception {
        UUID userId = UUID.randomUUID();
        Member member = new Member(userId, "Ada", "ada@example.edu", "HASH", 5);
        RecordingAccounts accounts = new RecordingAccounts(new UserAdminRepository.UserRecord(
                userId, "Ada", "ada@example.edu", "HASH", Role.MEMBER, true, 0, null));
        AuthenticationService auth = new AuthenticationService(
                email -> Optional.of(member),
                accounts,
                new RecordingHasher(),
                Clock.systemUTC());

        assertThrows(SecurityException.class, () -> auth.changePassword(
                member, "wrongpassword1".toCharArray(), "newpassword123".toCharArray()));
        auth.changePassword(
                member, "correctpassword".toCharArray(), "newpassword123".toCharArray());
        assertEquals("NEW_HASH", accounts.passwordHash);
    }

    private static final class RecordingHasher implements PasswordHasher {
        @Override
        public String hash(char[] password) {
            return "NEW_HASH";
        }

        @Override
        public boolean verify(String encodedHash, char[] password) {
            return password != null && new String(password).equals("correctpassword");
        }
    }

    private static final class RecordingAccounts implements UserAdminRepository {
        private UserRecord record;
        private int attempts;
        private Instant lockedUntil;
        private String passwordHash;

        private RecordingAccounts(UserRecord record) {
            this.record = record;
            this.passwordHash = record.passwordHash();
        }

        @Override
        public UUID create(String name, String email, String passwordHash, Role role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setActive(UUID userId, boolean active) {
        }

        @Override
        public void changeRole(UUID userId, Role role) {
        }

        @Override
        public void updatePasswordHash(UUID userId, String passwordHash) {
            this.passwordHash = passwordHash;
            record = new UserRecord(
                    record.id(),
                    record.name(),
                    record.email(),
                    passwordHash,
                    record.role(),
                    record.active(),
                    record.failedLoginAttempts(),
                    record.lockedUntil());
        }

        @Override
        public void recordFailedLogin(UUID userId, int attempts, Instant lockedUntil) {
            this.attempts = attempts;
            this.lockedUntil = lockedUntil;
            record = new UserRecord(
                    record.id(),
                    record.name(),
                    record.email(),
                    record.passwordHash(),
                    record.role(),
                    record.active(),
                    attempts,
                    lockedUntil);
        }

        @Override
        public void clearFailedLogins(UUID userId) {
            attempts = 0;
            lockedUntil = null;
        }

        @Override
        public Optional<UserRecord> findRecordByEmail(String email) {
            return Optional.of(record);
        }

        @Override
        public Optional<UserRecord> findRecordById(UUID userId) {
            return Optional.of(record);
        }

        @Override
        public List<UserRecord> listUsers() {
            return List.of(record);
        }
    }
}
