package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.UserAdminRepository;
import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.domain.Role;
import com.library.security.AuthorizationService;
import com.library.security.PasswordHasher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAdminServiceTest {
    @Test
    void adminCanCreateUserWithHashedPassword() throws Exception {
        RecordingUsers users = new RecordingUsers();
        RecordingHasher hasher = new RecordingHasher();
        RecordingAudit audits = new RecordingAudit();
        UserAdminService admin = new UserAdminService(
                users, hasher, new AuthorizationService(), new AuditService(audits));
        Librarian actor = new Librarian(
                UUID.randomUUID(), "Admin", "admin@example.edu", "hash", "A1", true);

        UUID id = admin.create(
                actor, "Ada", "Ada@Example.edu", "password12345".toCharArray(), Role.MEMBER);

        assertEquals(id, users.createdId);
        assertEquals("ada@example.edu", users.createdEmail);
        assertEquals("HASHED", users.createdHash);
        assertEquals(Role.MEMBER, users.createdRole);
        assertEquals(List.of("CREATE_USER"), audits.actions);
    }

    @Test
    void membersCannotManageUsers() {
        UserAdminService admin = new UserAdminService(
                new RecordingUsers(),
                new RecordingHasher(),
                new AuthorizationService(),
                new AuditService(new RecordingAudit()));
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);

        assertThrows(SecurityException.class, () -> admin.list(member));
        assertThrows(SecurityException.class, () -> admin.deactivate(member, UUID.randomUUID()));
    }

    @Test
    void adminCanDeactivateAndChangeRoles() throws Exception {
        RecordingUsers users = new RecordingUsers();
        RecordingAudit audits = new RecordingAudit();
        UserAdminService admin = new UserAdminService(
                users, new RecordingHasher(), new AuthorizationService(), new AuditService(audits));
        Librarian actor = new Librarian(
                UUID.randomUUID(), "Admin", "admin@example.edu", "hash", "A1", true);
        UUID target = UUID.randomUUID();

        admin.deactivate(actor, target);
        admin.changeRole(actor, target, Role.LIBRARIAN);

        assertEquals(Boolean.FALSE, users.activeStates.get(target));
        assertEquals(Role.LIBRARIAN, users.roles.get(target));
        assertEquals(List.of("DEACTIVATE_USER", "CHANGE_ROLE"), audits.actions);
    }

    private static final class RecordingAudit implements com.library.data.AuditRepository {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void record(Optional<UUID> userId, String action, String details) {
            actions.add(action);
        }
    }

    private static final class RecordingHasher implements PasswordHasher {
        @Override
        public String hash(char[] password) {
            return "HASHED";
        }

        @Override
        public boolean verify(String encodedHash, char[] password) {
            return false;
        }
    }

    private static final class RecordingUsers implements UserAdminRepository {
        private UUID createdId;
        private String createdEmail;
        private String createdHash;
        private Role createdRole;
        private final Map<UUID, Boolean> activeStates = new HashMap<>();
        private final Map<UUID, Role> roles = new HashMap<>();

        @Override
        public UUID create(String name, String email, String passwordHash, Role role) {
            createdId = UUID.randomUUID();
            createdEmail = email;
            createdHash = passwordHash;
            createdRole = role;
            return createdId;
        }

        @Override
        public void setActive(UUID userId, boolean active) {
            activeStates.put(userId, active);
        }

        @Override
        public void changeRole(UUID userId, Role role) {
            roles.put(userId, role);
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
            return Optional.empty();
        }

        @Override
        public List<UserRecord> listUsers() {
            return List.of();
        }
    }
}
