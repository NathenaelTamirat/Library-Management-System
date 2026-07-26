package com.library.security;

public interface PasswordHasher {
    String hash(char[] password);

    boolean verify(String encodedHash, char[] password);
}
