package com.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.library.data.CirculationReportRepository;
import com.library.domain.CirculationSummary;
import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.security.AuthorizationService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CirculationReportServiceTest {
    @Test
    void staffCanLoadSummaryWhileMembersCannot() throws Exception {
        CirculationReportService reports = new CirculationReportService(
                () -> new CirculationSummary(3, 2, 1, 2, new BigDecimal("3.00"), 4, 10),
                new AuthorizationService());
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Libby", "lib@example.edu", "hash", "desk", false);
        Member member = new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);

        CirculationSummary summary = reports.summarize(librarian);
        assertEquals(3, summary.openLoans());
        assertEquals(2, summary.membersWithOpenLoans());
        assertThrows(SecurityException.class, () -> reports.summarize(member));
    }
}
