package com.library.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberLookupSupportTest {
    @Test
    void membersUseOwnIdWithoutLookup() {
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        Optional<UUID> id = MemberLookupSupport.resolveMemberId(
                member,
                new AuthorizationService(),
                email -> Optional.empty(),
                Optional.of("ignored@example.edu"));
        assertEquals(Optional.of(member.id()), id);
    }

    @Test
    void staffResolveMemberEmailThroughLookup() {
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Libby", "lib@example.edu", "hash", "desk", false);
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        Optional<UUID> id = MemberLookupSupport.resolveMemberId(
                librarian,
                new AuthorizationService(),
                email -> email.equals("ada@example.edu") ? Optional.of(member) : Optional.empty(),
                Optional.of("Ada@Example.edu"));
        assertEquals(Optional.of(member.id()), id);
    }

    @Test
    void staffCancelOrUnknownEmailYieldsEmpty() {
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Libby", "lib@example.edu", "hash", "desk", false);
        assertTrue(MemberLookupSupport.resolveMemberId(
                librarian,
                new AuthorizationService(),
                email -> Optional.empty(),
                Optional.empty()).isEmpty());
        assertTrue(MemberLookupSupport.resolveMemberId(
                librarian,
                new AuthorizationService(),
                email -> Optional.<User>empty(),
                Optional.of("missing@example.edu")).isEmpty());
    }
}
