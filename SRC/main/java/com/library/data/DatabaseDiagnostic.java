package com.library.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

public final class DatabaseDiagnostic {
    private DatabaseDiagnostic() {
    }

    public static void verify(DataSource dataSource) throws SQLException {
        Objects.requireNonNull(dataSource);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new SQLException("Database connectivity check returned an unexpected result");
            }
        }
    }
}
