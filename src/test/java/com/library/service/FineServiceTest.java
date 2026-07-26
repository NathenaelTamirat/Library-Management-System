package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.FineRepository;
import com.library.domain.Fine;
import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.security.AuthorizationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FineServiceTest {
    @Test
    void membersCanViewOwnBalanceButOnlyStaffCanMarkPaid() throws Exception {
        RecordingFines repository = new RecordingFines();
        RecordingAudits audits = new RecordingAudits();
        FineService service = new FineService(
                repository, new AuthorizationService(), new AuditService(audits));
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Lib", "lib@example.edu", "hash", "AUD-1", false);
        Fine fine = new Fine(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("2.00"), false, LocalDate.now());
        repository.unpaid.put(member.id(), List.of(fine));

        assertEquals(new BigDecimal("2.00"), service.unpaidBalance(member, member.id()));
        assertThrows(SecurityException.class, () -> service.pay(member, fine.id()));

        service.pay(librarian, fine.id());
        assertEquals(fine.id(), repository.paidFineId);
        assertEquals("PAY_FINE", audits.actions.get(0));
    }

    @Test
    void staffCanWaiveFinesButMembersCannot() throws Exception {
        RecordingFines repository = new RecordingFines();
        RecordingAudits audits = new RecordingAudits();
        FineService service = new FineService(
                repository, new AuthorizationService(), new AuditService(audits));
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Lib", "lib@example.edu", "hash", "AUD-1", false);
        Fine fine = new Fine(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("4.00"), false, LocalDate.now());

        assertThrows(SecurityException.class, () -> service.waive(member, fine.id()));
        service.waive(librarian, fine.id());
        assertEquals(fine.id(), repository.waivedFineId);
        assertEquals("WAIVE_FINE", audits.actions.get(0));
    }

    private static final class RecordingFines implements FineRepository {
        private final java.util.Map<UUID, List<Fine>> unpaid = new java.util.HashMap<>();
        private UUID paidFineId;
        private UUID waivedFineId;

        @Override
        public Optional<Fine> findByLoanId(UUID loanId) {
            return Optional.empty();
        }

        @Override
        public List<Fine> findUnpaidByUser(UUID userId) {
            return unpaid.getOrDefault(userId, List.of());
        }

        @Override
        public List<Fine> findUnpaid() {
            return List.of();
        }

        @Override
        public void markPaid(UUID fineId) {
            paidFineId = fineId;
        }

        @Override
        public void waive(UUID fineId) {
            waivedFineId = fineId;
        }
    }

    private static final class RecordingAudits implements com.library.data.AuditRepository {
        private final List<String> actions = new ArrayList<>();

        @Override
        public void record(Optional<UUID> userId, String action, String details) {
            actions.add(action);
        }
        @Override
        public java.util.List<com.library.domain.AuditEntry> findRecent(int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.library.domain.AuditEntry> findByAction(String action, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.library.domain.AuditEntry> findByUser(java.util.UUID userId, int limit) {
            return java.util.List.of();
        }

    }
}
