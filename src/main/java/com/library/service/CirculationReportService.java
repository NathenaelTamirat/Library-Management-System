package com.library.service;

import com.library.data.CirculationReportRepository;
import com.library.domain.Book;
import com.library.domain.CirculationSummary;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.util.List;

public final class CirculationReportService {
    private final CirculationReportRepository reports;
    private final AuthorizationService authorization;

    public CirculationReportService(
            CirculationReportRepository reports, AuthorizationService authorization) {
        this.reports = reports;
        this.authorization = authorization;
    }

    public CirculationSummary summarize(User actor) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        return reports.summarize();
    }

    public List<Book> listZeroAvailability(User actor) throws SQLException {
        authorization.require(actor, Permission.MANAGE_CATALOG);
        return reports.listZeroAvailability();
    }
}
