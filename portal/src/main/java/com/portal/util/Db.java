package com.portal.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Minimal JDBC connection source. Reads DB config from env vars (see .env.example),
 * lazily on each call so config can be set at runtime (e.g. tests pointing at H2).
 * No connection pool — fine for this scale; swap for HikariCP later if needed.
 */
public final class Db {
    private Db() {}

    private static String url() {
        return Env.get("PORTAL_DB_URL",
                "jdbc:mysql://localhost:3306/portal?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    }

    public static Connection getConnection() throws SQLException {
        String url = url();
        // MySQL driver is registered on demand; H2 auto-registers when present (tests).
        if (url.startsWith("jdbc:mysql")) {
            try { Class.forName("com.mysql.cj.jdbc.Driver"); }
            catch (ClassNotFoundException e) { throw new SQLException("MySQL JDBC driver missing", e); }
        }
        return DriverManager.getConnection(url,
                Env.get("PORTAL_DB_USER", "portal"),
                Env.get("PORTAL_DB_PASSWORD", "portalpass"));
    }
}
