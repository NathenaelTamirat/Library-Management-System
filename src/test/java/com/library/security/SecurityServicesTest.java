package com.library.security;

import static org.junit.jupiter.api.Assertions.*;

import com.library.domain.Member;
import com.library.domain.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecurityServicesTest {
    @Test
    void argon2idHashAuthenticatesCorrectPasswordAndRejectsWrongPassword() {
        PasswordHasher hasher = new Argon2PasswordHasher();
        String hash = hasher.hash("correct horse battery staple".toCharArray());

        assertTrue(hash.startsWith("$argon2id$"));
        assertTrue(hasher.verify(hash, "correct horse battery staple".toCharArray()));
        assertFalse(hasher.verify(hash, "bad".toCharArray()));
    }

    @Test
    void authenticationUsesStoredHashRatherThanPlaintext() {
        PasswordHasher hasher = new Argon2PasswordHasher();
        String hash = hasher.hash("university-library-2026".toCharArray());
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", hash, 5);
        AuthenticationService service = new AuthenticationService(
                email -> email.equals(member.email()) ? Optional.of(member) : Optional.empty(),
                hasher);

        assertEquals(member, service.authenticate(
                "ADA@example.edu", "university-library-2026".toCharArray()).orElseThrow());
        assertTrue(service.authenticate(
                "ada@example.edu", "bad".toCharArray()).isEmpty());
    }

    @Test
    void rolePermissionsDenyMembersAdministrativeOperations() {
        AuthorizationService authorization = new AuthorizationService();

        assertTrue(authorization.isAllowed(Role.MEMBER, Permission.SEARCH_CATALOG));
        assertFalse(authorization.isAllowed(Role.MEMBER, Permission.MANAGE_CATALOG));
        assertFalse(authorization.isAllowed(Role.MEMBER, Permission.MANAGE_FINES));
        assertTrue(authorization.isAllowed(Role.LIBRARIAN, Permission.MANAGE_FINES));
        assertTrue(authorization.isAllowed(Role.ADMIN, Permission.MANAGE_FINES));
        assertTrue(authorization.isAllowed(Role.ADMIN, Permission.VIEW_AUDIT_LOG));
        assertThrows(SecurityException.class,
                () -> authorization.require(Role.MEMBER, Permission.MANAGE_USERS));
    }
}
