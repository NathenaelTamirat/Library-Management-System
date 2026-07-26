package com.library.security;

import com.library.domain.Role;
import com.library.domain.User;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class AuthorizationService {
    private final Map<Role, Set<Permission>> permissions = new EnumMap<>(Role.class);
    private final SessionGuard sessionGuard;

    public AuthorizationService() {
        this(SessionGuard.noop());
    }

    public AuthorizationService(SessionGuard sessionGuard) {
        this.sessionGuard = sessionGuard == null ? SessionGuard.noop() : sessionGuard;
        permissions.put(Role.MEMBER, EnumSet.of(
                Permission.SEARCH_CATALOG,
                Permission.BORROW_BOOK));
        permissions.put(Role.LIBRARIAN, EnumSet.of(
                Permission.SEARCH_CATALOG,
                Permission.MANAGE_CATALOG,
                Permission.MANAGE_LOANS));
        permissions.put(Role.ADMIN, EnumSet.allOf(Permission.class));
    }

    public boolean isAllowed(Role role, Permission permission) {
        return permissions.getOrDefault(role, Set.of()).contains(permission);
    }

    public void require(Role role, Permission permission) {
        if (!isAllowed(role, permission)) {
            throw new SecurityException(role + " is not allowed to " + permission);
        }
    }

    public void require(User actor, Permission permission) {
        sessionGuard.requireActive(actor);
        require(actor.role(), permission);
    }
}
