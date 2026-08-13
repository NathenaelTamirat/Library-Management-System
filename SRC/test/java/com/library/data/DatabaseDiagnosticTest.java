package com.library.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class DatabaseDiagnosticTest {
    @Test
    void verifiesReachableDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:diagnostic");

        assertDoesNotThrow(() -> DatabaseDiagnostic.verify(dataSource));
    }

    @Test
    void reportsConnectionFailure() {
        SQLException expected = new SQLException("database offline");

        SQLException actual = assertThrows(
                SQLException.class, () -> DatabaseDiagnostic.verify(failingDataSource(expected)));

        assertSame(expected, actual);
    }

    private static DataSource failingDataSource(SQLException failure) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw failure;
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw failure;
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                throw new SQLException("Not a wrapper");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }
}
