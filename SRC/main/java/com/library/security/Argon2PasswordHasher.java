package com.library.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import java.util.Arrays;

public final class Argon2PasswordHasher implements PasswordHasher {
    private static final int ITERATIONS = 3;
    private static final int MEMORY_KIB = 65_536;
    private static final int PARALLELISM = 1;

    private final Argon2 argon2;

    public Argon2PasswordHasher() {
        this(Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id));
    }

    Argon2PasswordHasher(Argon2 argon2) {
        this.argon2 = argon2;
    }

    @Override
    public String hash(char[] password) {
        requirePassword(password);
        try {
            return argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public boolean verify(String encodedHash, char[] password) {
        if (encodedHash == null || encodedHash.isBlank()) {
            throw new IllegalArgumentException("Encoded hash is required");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }
        try {
            return argon2.verify(encodedHash, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length < 12) {
            throw new IllegalArgumentException("Password must contain at least 12 characters");
        }
    }
}
