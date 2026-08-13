package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.HoldRepository;
import com.library.domain.Hold;
import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.security.AuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldServiceTest {
    @Test
    void memberCanPlaceAndCancelHold() throws Exception {
        RecordingHolds holds = new RecordingHolds();
        holds.books.add("9780134685991");
        RecordingAudit audits = new RecordingAudit();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);
        HoldService service = new HoldService(
                holds, new AuthorizationService(), new AuditService(audits), clock);
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);

        Hold hold = service.place(member, "9780134685991");
        assertEquals(member.id(), hold.userId());
        assertEquals(List.of("PLACE_HOLD"), audits.actions);

        service.cancel(member, hold.id());
        assertEquals(List.of("PLACE_HOLD", "CANCEL_HOLD"), audits.actions);
        assertTrue(holds.cancelled.contains(hold.id()));
    }

    @Test
    void cannotPlaceDuplicateActiveHold() throws Exception {
        RecordingHolds holds = new RecordingHolds();
        holds.books.add("9780134685991");
        HoldService service = new HoldService(
                holds,
                new AuthorizationService(),
                new AuditService(new RecordingAudit()),
                Clock.systemUTC());
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        service.place(member, "9780134685991");

        assertThrows(IllegalStateException.class, () -> service.place(member, "9780134685991"));
    }

    @Test
    void memberCannotCancelAnotherMembersHold() throws Exception {
        RecordingHolds holds = new RecordingHolds();
        holds.books.add("9780134685991");
        HoldService service = new HoldService(
                holds,
                new AuthorizationService(),
                new AuditService(new RecordingAudit()),
                Clock.systemUTC());
        Member owner = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        Member stranger = new Member(UUID.randomUUID(), "Grace", "grace@example.edu", "hash", 5);
        Hold hold = service.place(owner, "9780134685991");

        assertThrows(SecurityException.class, () -> service.cancel(stranger, hold.id()));
    }

    @Test
    void staffCanViewHoldQueue() throws Exception {
        RecordingHolds holds = new RecordingHolds();
        holds.books.add("9780134685991");
        HoldService service = new HoldService(
                holds,
                new AuthorizationService(),
                new AuditService(new RecordingAudit()),
                Clock.systemUTC());
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        service.place(member, "9780134685991");
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Lib", "lib@example.edu", "hash", "desk", false);

        assertEquals(1, service.queueForIsbn(librarian, "9780134685991").size());
    }

    @Test
    void readyNextForIsbnMarksFifoHeadReadyWithExpiry() throws Exception {
        RecordingHolds holds = new RecordingHolds();
        holds.books.add("9780134685991");
        RecordingAudit audits = new RecordingAudit();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);
        HoldService service = new HoldService(
                holds, new AuthorizationService(), new AuditService(audits), clock);
        Member first = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        Member second = new Member(UUID.randomUUID(), "Grace", "grace@example.edu", "hash", 5);
        Hold head = service.place(first, "9780134685991");
        holds.place(second.id(), "9780134685991", Instant.parse("2026-07-26T13:00:00Z"));

        Hold ready = service.readyNextForIsbn("9780134685991").orElseThrow();

        assertEquals(head.id(), ready.id());
        assertEquals("READY", ready.status().name());
        assertEquals(Instant.parse("2026-07-28T12:00:00Z"), ready.expiresAt());
        assertEquals(head.id(), holds.readyIds.get(0));
        assertTrue(audits.actions.contains("HOLD_READY"));
    }

    @Test
    void staffCanExpireStaleReadyHoldsUsingInjectedClock() throws Exception {
        RecordingHolds holds = new RecordingHolds();
        RecordingAudit audits = new RecordingAudit();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);
        HoldService service = new HoldService(
                holds, new AuthorizationService(), new AuditService(audits), clock);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Lib", "lib@example.edu", "hash", "desk", false);

        int expired = service.expireStale(librarian);

        assertEquals(Instant.parse("2026-07-26T12:00:00Z"), holds.expiredAsOf);
        assertEquals(1, expired);
        assertEquals(List.of("EXPIRE_HOLDS"), audits.actions);
        assertThrows(
                SecurityException.class,
                () -> service.expireStale(new Member(
                        UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5)));
    }

    private static final class RecordingAudit implements com.library.data.AuditRepository {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void record(Optional<UUID> userId, String action, String details) {
            actions.add(action);
        }

        @Override
        public List<com.library.domain.AuditEntry> findRecent(int limit) {
            return List.of();
        }

        @Override
        public List<com.library.domain.AuditEntry> findByAction(String action, int limit) {
            return List.of();
        }

        @Override
        public List<com.library.domain.AuditEntry> findByUser(UUID userId, int limit) {
            return List.of();
        }

        @Override
        public List<com.library.domain.AuditEntry> findBetween(Instant from, Instant to, int limit) {
            return List.of();
        }
    }

    private static final class RecordingHolds implements HoldRepository {
        private final Map<UUID, Hold> byId = new HashMap<>();
        private final List<String> books = new ArrayList<>();
        private final List<UUID> cancelled = new ArrayList<>();
        private final List<UUID> readyIds = new ArrayList<>();
        private Instant expiredAsOf;

        @Override
        public Hold place(UUID userId, String isbn, Instant placedAt) {
            Hold hold = new Hold(UUID.randomUUID(), userId, isbn, placedAt);
            byId.put(hold.id(), hold);
            return hold;
        }

        @Override
        public void cancel(UUID holdId) {
            cancelled.add(holdId);
            byId.get(holdId).cancel();
        }

        @Override
        public void fulfill(UUID holdId) {
            byId.get(holdId).fulfill();
        }

        @Override
        public void markReady(UUID holdId, Instant expiresAt) {
            readyIds.add(holdId);
            byId.get(holdId).markReady(expiresAt);
        }

        @Override
        public int expireReadyBefore(Instant asOf) {
            expiredAsOf = asOf;
            return 1;
        }

        @Override
        public Optional<Hold> findById(UUID holdId) {
            return Optional.ofNullable(byId.get(holdId));
        }

        @Override
        public Optional<Hold> findActiveByUserAndIsbn(UUID userId, String isbn) {
            return byId.values().stream()
                    .filter(hold -> hold.userId().equals(userId) && hold.isbn().equals(isbn))
                    .filter(hold -> hold.status().name().equals("WAITING")
                            || hold.status().name().equals("READY"))
                    .findFirst();
        }

        @Override
        public Optional<Hold> findFirstActiveByIsbn(String isbn) {
            return byId.values().stream()
                    .filter(hold -> hold.isbn().equals(isbn))
                    .filter(hold -> hold.status().name().equals("WAITING")
                            || hold.status().name().equals("READY"))
                    .sorted((a, b) -> a.placedAt().compareTo(b.placedAt()))
                    .findFirst();
        }

        @Override
        public List<Hold> findActiveByIsbn(String isbn) {
            return byId.values().stream()
                    .filter(hold -> hold.isbn().equals(isbn))
                    .filter(hold -> hold.status().name().equals("WAITING")
                            || hold.status().name().equals("READY"))
                    .toList();
        }

        @Override
        public List<Hold> findActiveByUser(UUID userId) {
            return byId.values().stream()
                    .filter(hold -> hold.userId().equals(userId))
                    .filter(hold -> hold.status().name().equals("WAITING")
                            || hold.status().name().equals("READY"))
                    .toList();
        }

        @Override
        public boolean bookExists(String isbn) {
            return books.contains(isbn);
        }
    }
}
