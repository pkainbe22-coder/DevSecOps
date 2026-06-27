package com.portal;

import com.portal.util.Db;
import com.portal.util.PolicySchema;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test harness DB: an in-memory H2 in MySQL-compat mode, pointed at via the same
 * PORTAL_DB_URL the app reads. Mirrors db/schema.sql closely enough to exercise the
 * DAO/gate logic (enum-typed columns are modelled as VARCHAR — the DAOs pass strings).
 */
public final class TestDb {
    private TestDb() {}

    private static final String URL =
            "jdbc:h2:mem:portal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    /** Point the app's Db at H2 and (re)create a clean schema. Call in @BeforeEach. */
    public static void reset() {
        System.setProperty("PORTAL_DB_URL", URL);
        System.setProperty("PORTAL_DB_USER", "sa");
        System.setProperty("PORTAL_DB_PASSWORD", "");
        try (Connection c = Db.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("""
                CREATE TABLE users (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  username VARCHAR(100) UNIQUE NOT NULL,
                  password_hash VARCHAR(255) NOT NULL,
                  role VARCHAR(20) NOT NULL
                )""");
            st.execute("""
                CREATE TABLE commits (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  commit_hash VARCHAR(60) UNIQUE NOT NULL,
                  author VARCHAR(100), message TEXT, branch VARCHAR(100),
                  repo VARCHAR(150), gitea_url VARCHAR(500), committed_at DATETIME
                )""");
            st.execute("""
                CREATE TABLE scan_results (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  commit_id INT, scan_type VARCHAR(10) NOT NULL, tool VARCHAR(50),
                  critical INT DEFAULT 0, high INT DEFAULT 0, medium INT DEFAULT 0, low INT DEFAULT 0,
                  report_url VARCHAR(500), scanned_at DATETIME,
                  CONSTRAINT uq_commit_scan UNIQUE (commit_id, scan_type)
                )""");
            st.execute("""
                CREATE TABLE deployment_approvals (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  commit_id INT UNIQUE, decision VARCHAR(10) DEFAULT 'PENDING',
                  security_user_id INT, comment TEXT, decided_at DATETIME
                )""");
            st.execute("""
                CREATE TABLE deployments (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  commit_id INT, status VARCHAR(15) DEFAULT 'NOT_DEPLOYED',
                  environment VARCHAR(50), ops_user_id INT, deployed_at DATETIME
                )""");
            PolicySchema.apply(c);   // decision_source column + policy_rules + default ruleset
        } catch (SQLException e) {
            throw new RuntimeException("test schema setup failed", e);
        }
    }
}
