package com.library.data;

import com.library.domain.CirculationSummary;
import com.library.domain.Book;
import java.sql.SQLException;
import java.util.List;

public interface CirculationReportRepository {
    CirculationSummary summarize() throws SQLException;

    List<Book> listZeroAvailability() throws SQLException;
}
