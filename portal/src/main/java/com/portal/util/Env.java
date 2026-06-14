package com.portal.util;

/**
 * Central config accessor. All secrets/URLs come from environment variables
 * (or -D system properties as a fallback). Nothing is hardcoded or committed.
 */
public final class Env {
    private Env() {}

    public static String get(String key, String defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) v = System.getProperty(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    public static String require(String key) {
        String v = get(key, null);
        if (v == null) throw new IllegalStateException("Missing required env var: " + key);
        return v;
    }
}
