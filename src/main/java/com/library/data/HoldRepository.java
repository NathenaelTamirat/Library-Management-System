package com.library.data;

import com.library.domain.Hold;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldRepository {
    Hold place(UUID userId, String isbn, Instant placedAt) throws SQLException;

    void cancel(UUID holdId) throws SQLException;

    void fulfill(UUID holdId) throws SQLException;

    Optional<Hold> findById(UUID holdId) throws SQLException;

    Optional<Hold> findActiveByUserAndIsbn(UUID userId, String isbn) throws SQLException;

    Optional<Hold> findFirstActiveByIsbn(String isbn) throws SQLException;

    List<Hold> findActiveByIsbn(String isbn) throws SQLException;

    List<Hold> findActiveByUser(UUID userId) throws SQLException;

    boolean bookExists(String isbn) throws SQLException;
}
