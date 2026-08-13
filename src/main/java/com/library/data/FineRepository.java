package com.library.data;

import com.library.domain.Fine;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FineRepository {
    Optional<Fine> findByLoanId(UUID loanId) throws SQLException;

    List<Fine> findUnpaidByUser(UUID userId) throws SQLException;

    List<Fine> findUnpaid() throws SQLException;

    void markPaid(UUID fineId) throws SQLException;

    void payPartial(UUID fineId, BigDecimal payment) throws SQLException;

    void waive(UUID fineId) throws SQLException;
}
