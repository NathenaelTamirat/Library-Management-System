package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.AuditRepository;
import com.library.domain.AuditEntry;
import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.security.AuthorizationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditServiceQueryTest {
    @Test
    void onlyAdminsCanQueryAuditLog() throws Exception {
        RecordingAudits audits = new RecordingAudits();
        AuditService service = new AuditService(audits, new AuthorizationService());
        Librarian admin = new Librarian(
                UUID.randomUUID(), "Admin", "admin@example.edu", "hash", "A1", true);
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);

        assertEquals(1, service.recent(admin, 10).size());
        assertThrows(SecurityException.class, () -> service.recent(member, 10));
        assertEquals("CHECKOUT", service.byAction(admin, "CHECKOUT", 5).get(0).action());
    }

    private static final class RecordingAudits implements AuditRepository {
        private final AuditEntry entry = new AuditEntry(
                1L,
                Optional.of(UUID.randomUUID()),
                "CHECKOUT",
                Instant.parse("2026-07-26T00:00:00Z"),
                "{}");

        @Override
        public void record(Optional<UUID> userId, String action, String details) {
        }

        @Override
        public List<AuditEntry> findRecent(int limit) {
            return List.of(entry);
        }

        @Override
        public List<AuditEntry> findByAction(String action, int limit) {
            return List.of(entry);
        }

        @Override
        public List<AuditEntry> findByUser(UUID userId, int limit) {
            return List.of(entry);
        }
    }
}
