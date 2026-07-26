package com.library.security;

import com.library.data.UserAdminRepository;
import com.library.data.UserAdminRepository.UserRecord;
import com.library.domain.User;
import java.sql.SQLException;
import java.util.Optional;

public final class SessionGuard {
    private final UserAdminRepository accounts;

    public SessionGuard(UserAdminRepository accounts) {
        this.accounts = accounts;
    }

    public static SessionGuard noop() {
        return new SessionGuard(null);
    }

    public void requireActive(User actor) {
        if (accounts == null || actor == null) {
            return;
        }
        try {
            Optional<UserRecord> record = accounts.findRecordById(actor.id());
            if (record.isEmpty() || !record.orElseThrow().active()) {
                throw new SecurityException("Account is deactivated");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to verify account status", failure);
        }
    }
}
