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
        SecurityException denied =
                assertThrows(SecurityException.class, () -> service.pay(member, fine.id()));
        assertTrue(denied.getMessage().contains("MANAGE_FINES"));

        service.pay(librarian, fine.id());
        assertEquals(fine.id(), repository.paidFineId);
        assertEquals("PAY_FINE", audits.actions.get(0));
    }

    @Test
    void staffCanRecordPartialFinePayments() throws Exception {
        RecordingFines repository = new RecordingFines();
        RecordingEvents events = new RecordingEvents();
        RecordingAudits audits = new RecordingAudits();
        FineService service = new FineService(
                repository,
                events,
                new AuthorizationService(),
                new AuditService(audits),
                java.time.Clock.systemUTC());
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Lib", "lib@example.edu", "hash", "AUD-1", false);
        UUID fineId = UUID.randomUUID();

        SecurityException denied = assertThrows(
                SecurityException.class,
                () -> service.payPartial(member, fineId, new BigDecimal("1.25")));
        assertTrue(denied.getMessage().contains("MANAGE_FINES"));
        service.payPartial(librarian, fineId, new BigDecimal("1.25"));

        assertEquals(fineId, repository.paidFineId);
        assertEquals(new BigDecimal("1.25"), repository.partialPayment);
        assertEquals("PAY_FINE_PARTIAL", audits.actions.get(0));
        assertEquals(1, events.recorded.size());
        assertEquals(com.library.domain.FineEventType.PAY_PARTIAL, events.recorded.get(0).type());
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

        SecurityException denied =
                assertThrows(SecurityException.class, () -> service.waive(member, fine.id()));
        assertTrue(denied.getMessage().contains("MANAGE_FINES"));
        service.waive(librarian, fine.id());
        assertEquals(fine.id(), repository.waivedFineId);
        assertEquals("WAIVE_FINE", audits.actions.get(0));
    }

    private static final class RecordingEvents implements com.library.data.FineEventRepository {
        private final List<com.library.domain.FineEvent> recorded = new ArrayList<>();

        @Override
        public com.library.domain.FineEvent record(
                UUID fineId,
                UUID actorId,
                com.library.domain.FineEventType type,
                BigDecimal amount,
                java.time.Instant occurredAt) {
            com.library.domain.FineEvent event = new com.library.domain.FineEvent(
                    UUID.randomUUID(), fineId, actorId, type, amount, occurredAt);
            recorded.add(event);
            return event;
        }

        @Override
        public List<com.library.domain.FineEvent> findByFineId(UUID fineId) {
            return recorded.stream().filter(event -> event.fineId().equals(fineId)).toList();
        }
    }

    private static final class RecordingFines implements FineRepository {
        private final java.util.Map<UUID, List<Fine>> unpaid = new java.util.HashMap<>();
        private UUID paidFineId;
        private UUID waivedFineId;
        private BigDecimal partialPayment;

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
        public void payPartial(UUID fineId, BigDecimal payment) {
            paidFineId = fineId;
            partialPayment = payment;
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

        @Override
        public java.util.List<com.library.domain.AuditEntry> findBetween(java.time.Instant from, java.time.Instant to, int limit) {
            return java.util.List.of();
        }

    }
}
