package com.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.library.data.NotificationRepository;
import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.domain.Notification;
import com.library.domain.NotificationKind;
import com.library.security.AuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    @Test
    void staffCanInspectAndMarkPendingNotifications() throws Exception {
        RecordingNotifications repository = new RecordingNotifications();
        Clock clock = Clock.fixed(Instant.parse("2026-07-26T12:00:00Z"), ZoneOffset.UTC);
        NotificationService service = new NotificationService(
                repository, new AuthorizationService(), clock);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Lib", "lib@example.edu", "hash", "desk", false);
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);

        Notification created = service.enqueue(
                member.id(), NotificationKind.OVERDUE, "{\"loanId\":\"x\"}");
        assertEquals(1, service.pending(librarian, 10).size());
        service.markSent(librarian, created.id());
        assertTrue(service.pending(librarian, 10).isEmpty());
        assertThrows(SecurityException.class, () -> service.pending(member, 10));
    }

    private static final class RecordingNotifications implements NotificationRepository {
        private final List<Notification> rows = new ArrayList<>();

        @Override
        public Notification enqueue(
                UUID userId, NotificationKind kind, String payload, Instant createdAt) {
            Notification notification = new Notification(
                    UUID.randomUUID(), userId, kind, payload, createdAt, null);
            rows.add(notification);
            return notification;
        }

        @Override
        public List<Notification> findPending(int limit) {
            return rows.stream().filter(Notification::pending).limit(limit).toList();
        }

        @Override
        public void markSent(UUID notificationId, Instant sentAt) {
            for (int i = 0; i < rows.size(); i++) {
                Notification current = rows.get(i);
                if (current.id().equals(notificationId)) {
                    rows.set(i, new Notification(
                            current.id(),
                            current.userId(),
                            current.kind(),
                            current.payload(),
                            current.createdAt(),
                            sentAt));
                    return;
                }
            }
            throw new IllegalStateException("missing");
        }
    }
}
