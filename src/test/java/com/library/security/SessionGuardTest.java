package com.library.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.library.data.UserAdminRepository;
import com.library.domain.Librarian;
import com.library.domain.Role;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionGuardTest {
    @Test
    void requireRejectsDeactivatedActors() {
        UUID id = UUID.randomUUID();
        RecordingAccounts accounts = new RecordingAccounts();
        accounts.records.put(id, new UserAdminRepository.UserRecord(
                id, "Ada", "ada@example.edu", "hash", Role.ADMIN, false, 0, null));
        AuthorizationService authorization = new AuthorizationService(new SessionGuard(accounts));
        Librarian actor = new Librarian(id, "Ada", "ada@example.edu", "hash", "A1", true);

        assertThrows(
                SecurityException.class,
                () -> authorization.require(actor, Permission.MANAGE_USERS));
    }

    @Test
    void requireAllowsActiveActors() {
        UUID id = UUID.randomUUID();
        RecordingAccounts accounts = new RecordingAccounts();
        accounts.records.put(id, new UserAdminRepository.UserRecord(
                id, "Ada", "ada@example.edu", "hash", Role.ADMIN, true, 0, null));
        AuthorizationService authorization = new AuthorizationService(new SessionGuard(accounts));
        Librarian actor = new Librarian(id, "Ada", "ada@example.edu", "hash", "A1", true);

        assertDoesNotThrow(() -> authorization.require(actor, Permission.MANAGE_USERS));
    }

    private static final class RecordingAccounts implements UserAdminRepository {
        private final Map<UUID, UserRecord> records = new HashMap<>();

        @Override
        public UUID create(String name, String email, String passwordHash, Role role) {
            return UUID.randomUUID();
        }

        @Override
        public void setActive(UUID userId, boolean active) {
        }

        @Override
        public void changeRole(UUID userId, Role role) {
        }

        @Override
        public void updatePasswordHash(UUID userId, String passwordHash) {
        }

        @Override
        public void recordFailedLogin(UUID userId, int attempts, Instant lockedUntil) {
        }

        @Override
        public void clearFailedLogins(UUID userId) {
        }

        @Override
        public Optional<UserRecord> findRecordByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<UserRecord> findRecordById(UUID userId) {
            return Optional.ofNullable(records.get(userId));
        }

        @Override
        public List<UserRecord> listUsers() {
            return List.of();
        }
    }
}
