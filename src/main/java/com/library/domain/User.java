package com.library.domain;

import java.util.Objects;
import java.util.UUID;

public abstract class User {
    private final UUID id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Role role;

    protected User(UUID id, String name, String email, String passwordHash, Role role) {
        this.id = Objects.requireNonNull(id, "ID is required");
        this.name = requireText(name, "Name");
        this.email = requireText(email, "Email").toLowerCase();
        this.passwordHash = requireText(passwordHash, "Password hash");
        this.role = Objects.requireNonNull(role, "Role is required");
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
