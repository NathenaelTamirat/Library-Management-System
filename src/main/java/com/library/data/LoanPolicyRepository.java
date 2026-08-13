package com.library.data;

import com.library.domain.LoanPolicy;
import java.sql.SQLException;

public interface LoanPolicyRepository {
    LoanPolicy load() throws SQLException;

    void save(LoanPolicy policy) throws SQLException;
}
