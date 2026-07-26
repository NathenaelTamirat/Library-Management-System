package com.library.service;

import com.library.data.UserAdminRepository;
import com.library.data.UserAdminRepository.UserRecord;
import com.library.domain.Role;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.PasswordHasher;
import com.library.security.Permission;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class UserAdminService {
    private final UserAdminRepository users;
    private final PasswordHasher passwordHasher;
    private final AuthorizationService authorization;
    private final AuditService audit;

    public UserAdminService(
            UserAdminRepository users,
            PasswordHasher passwordHasher,
            AuthorizationService authorization,
            AuditService audit) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.authorization = authorization;
        this.audit = audit;
    }

    public UUID create(
            User actor, String name, String email, char[] password, Role role) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_USERS);
        String normalizedEmail = email == null ? "" : email.strip().toLowerCase();
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (users.findRecordByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException("User already exists: " + normalizedEmail);
        }
        String hash = passwordHasher.hash(password);
        UUID id = users.create(name.strip(), normalizedEmail, hash, role);
        audit.record(
                actor.id(),
                "CREATE_USER",
                "{\"userId\":\"" + id + "\",\"role\":\"" + role + "\"}");
        return id;
    }

    public void deactivate(User actor, UUID userId) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_USERS);
        if (actor.id().equals(userId)) {
            throw new IllegalStateException("Administrators cannot deactivate themselves");
        }
        users.setActive(userId, false);
        audit.record(actor.id(), "DEACTIVATE_USER", "{\"userId\":\"" + userId + "\"}");
    }

    public void activate(User actor, UUID userId) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_USERS);
        users.setActive(userId, true);
        audit.record(actor.id(), "ACTIVATE_USER", "{\"userId\":\"" + userId + "\"}");
    }

    public void changeRole(User actor, UUID userId, Role role) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_USERS);
        if (actor.id().equals(userId) && role != Role.ADMIN) {
            throw new IllegalStateException("Administrators cannot remove their own admin role");
        }
        users.changeRole(userId, role);
        audit.record(
                actor.id(),
                "CHANGE_ROLE",
                "{\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}");
    }

    public List<UserRecord> list(User actor) throws SQLException {
        authorization.require(actor.role(), Permission.MANAGE_USERS);
        return users.listUsers();
    }
}
