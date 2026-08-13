package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.LoanPolicyRepository;
import com.library.domain.Librarian;
import com.library.domain.LoanPolicy;
import com.library.domain.Member;
import com.library.security.AuthorizationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoanPolicyServiceTest {
    @Test
    void adminCanUpdatePolicyAndMembersCannot() throws Exception {
        RecordingPolicies policies = new RecordingPolicies();
        RecordingAudit audits = new RecordingAudit();
        LoanPolicyService service = new LoanPolicyService(
                policies, new AuthorizationService(), new AuditService(audits));
        Librarian admin = new Librarian(
                UUID.randomUUID(), "Admin", "admin@example.edu", "hash", "A1", true);
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        LoanPolicy updated = new LoanPolicy(21, new BigDecimal("0.75"), new BigDecimal("60.00"), 3, 7);

        assertThrows(SecurityException.class, () -> service.update(member, updated));
        assertEquals(updated, service.update(admin, updated));
        assertEquals(updated, policies.saved);
        assertEquals("UPDATE_POLICY", audits.actions.get(0));
    }

    private static final class RecordingPolicies implements LoanPolicyRepository {
        private LoanPolicy saved = LoanPolicy.defaults();

        @Override
        public LoanPolicy load() {
            return saved;
        }

        @Override
        public void save(LoanPolicy policy) {
            saved = policy;
        }
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
}
