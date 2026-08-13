package com.library.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;

public final class DataSourceFactory {
    private DataSourceFactory() {
    }

    public static HikariDataSource create(DatabaseConfig database) {
        Objects.requireNonNull(database);
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(database.jdbcUrl());
        hikari.setUsername(database.username());
        hikari.setPassword(database.password());
        hikari.setMaximumPoolSize(database.maximumPoolSize());
        hikari.setMinimumIdle(Math.min(2, database.maximumPoolSize()));
        hikari.setAutoCommit(true);
        hikari.setPoolName("library-pool");
        hikari.setConnectionTimeout(10_000);
        hikari.setValidationTimeout(3_000);
        return new HikariDataSource(hikari);
    }

    public record DatabaseConfig(String jdbcUrl, String username, String password, int maximumPoolSize) {
        public DatabaseConfig {
            if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql:")) {
                throw new IllegalArgumentException("A PostgreSQL JDBC URL is required");
            }
            if (maximumPoolSize < 1) {
                throw new IllegalArgumentException("Pool size must be positive");
            }
            username = Objects.requireNonNull(username);
            password = Objects.requireNonNull(password);
        }
    }
}
