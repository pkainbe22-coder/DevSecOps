package com.portal.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Minimal JDBC connection source. Reads DB config from env vars (see .env.example).
 * No connection pool — fine for this scale; swap for HikariCP later if needed.
 */
public final class Db {
    private Db() {}

    private static final String URL =
            Env.get("PORTAL_DB_URL",
                    "jdbc:mysql://localhost:3306/portal?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    private static final String USER = Env.get("PORTAL_DB_USER", "portal");
    private static final String PASSWORD = Env.get("PORTAL_DB_PASSWORD", "portalpass");

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("MySQL JDBC driver not found on classpath");
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
