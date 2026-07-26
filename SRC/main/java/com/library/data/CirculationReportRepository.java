package com.library.data;

import com.library.domain.CirculationSummary;
import java.sql.SQLException;

public interface CirculationReportRepository {
    CirculationSummary summarize() throws SQLException;
}
