package com.portal.util;

import org.mindrot.jbcrypt.BCrypt;

/** BCrypt hashing helpers. Passwords are never stored or logged in plain text. */
public final class Passwords {
    private Passwords() {}

    public static String hash(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(12));
    }

    public static boolean verify(String plain, String hash) {
        if (hash == null || !hash.startsWith("$2")) return false;
        return BCrypt.checkpw(plain, hash);
    }
}
