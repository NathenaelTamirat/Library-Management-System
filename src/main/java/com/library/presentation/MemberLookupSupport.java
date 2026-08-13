package com.library.presentation;

import com.library.domain.Member;
import com.library.domain.User;
import com.library.security.AuthenticationService;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.util.Optional;
import java.util.UUID;

final class MemberLookupSupport {
    private MemberLookupSupport() {
    }

    static Optional<UUID> resolveMemberId(
            User actor,
            AuthorizationService authorization,
            AuthenticationService.UserLookup lookup,
            Optional<String> staffEmail) {
        if (!(actor instanceof Member)
                && authorization.isAllowed(actor.role(), Permission.MANAGE_LOANS)) {
            if (staffEmail.isEmpty() || staffEmail.orElseThrow().isBlank()) {
                return Optional.empty();
            }
            Optional<User> found = lookup.findByEmail(staffEmail.orElseThrow().strip().toLowerCase());
            if (found.isEmpty() || !(found.orElseThrow() instanceof Member)) {
                return Optional.empty();
            }
            return Optional.of(found.orElseThrow().id());
        }
        return Optional.of(actor.id());
    }
}
