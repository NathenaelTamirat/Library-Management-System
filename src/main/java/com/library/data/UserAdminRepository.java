package com.library.data;

import com.library.domain.Role;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAdminRepository {
    UUID create(String name, String email, String passwordHash, Role role) throws SQLException;

    void setActive(UUID userId, boolean active) throws SQLException;

    void changeRole(UUID userId, Role role) throws SQLException;

    void updatePasswordHash(UUID userId, String passwordHash) throws SQLException;

    void recordFailedLogin(UUID userId, int attempts, Instant lockedUntil) throws SQLException;

    void clearFailedLogins(UUID userId) throws SQLException;

    Optional<UserRecord> findRecordByEmail(String email) throws SQLException;

    Optional<UserRecord> findRecordById(UUID userId) throws SQLException;

    List<UserRecord> listUsers() throws SQLException;

    record UserRecord(
            UUID id,
            String name,
            String email,
            String passwordHash,
            Role role,
            boolean active,
            int failedLoginAttempts,
            Instant lockedUntil) {
    }
}
