package com.library.domain;

import java.util.UUID;

public final class Librarian extends User {
    private final String auditTag;

    public Librarian(UUID id, String name, String email, String passwordHash, String auditTag, boolean admin) {
        super(id, name, email, passwordHash, admin ? Role.ADMIN : Role.LIBRARIAN);
        if (auditTag == null || auditTag.isBlank()) {
            throw new IllegalArgumentException("Audit tag is required");
        }
        this.auditTag = auditTag.strip();
    }

    public String auditTag() {
        return auditTag;
    }
}
