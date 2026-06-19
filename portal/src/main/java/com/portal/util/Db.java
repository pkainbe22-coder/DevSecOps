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
        // Explicitly load the driver: in a webapp, classloader isolation prevents
        // DriverManager's ServiceLoader auto-registration from finding WEB-INF/lib drivers.
        String driver = url.startsWith("jdbc:mysql") ? "com.mysql.cj.jdbc.Driver"
                      : url.startsWith("jdbc:h2")    ? "org.h2.Driver" : null;
        if (driver != null) {
            try { Class.forName(driver); }
            catch (ClassNotFoundException e) { throw new SQLException("JDBC driver missing: " + driver, e); }
        }
        return DriverManager.getConnection(url,
                Env.get("PORTAL_DB_USER", "portal"),
                Env.get("PORTAL_DB_PASSWORD", "portalpass"));
    }
}
