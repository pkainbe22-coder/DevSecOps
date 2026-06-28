package com.portal.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Idempotent schema migration for the Policy-as-Code feature. Runs at startup against
 * whatever DB the portal is pointed at — live MySQL (where db/schema.sql's initdb pass
 * already happened and won't re-run) or the embedded H2 demo. Safe to call repeatedly.
 *
 * Adds: deployment_approvals.decision_source, the policy_rules table, and the default
 * ruleset. Uses portable DDL (VARCHAR rather than ENUM, information_schema column check)
 * so the same code works on both MySQL 8 and H2 (MySQL mode).
 */
public final class PolicySchema {
    private PolicySchema() {}

    public static void apply(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS policy_rules (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  rule_name VARCHAR(100),
                  condition_field VARCHAR(10),
                  operator VARCHAR(5),
                  threshold_value INT,
                  action VARCHAR(20),
                  priority INT,
                  active BOOLEAN DEFAULT TRUE
                )""");

            if (!columnExists(c, "deployment_approvals", "decision_source")) {
                st.execute("ALTER TABLE deployment_approvals "
                         + "ADD COLUMN decision_source VARCHAR(20) DEFAULT 'MANUAL'");
            }

            // Risk Intelligence: individual findings (one row per CVE/alert) enriched with
            // EPSS exploit-probability, CISA KEV membership, a computed risk score, and AI text.
            st.execute("""
                CREATE TABLE IF NOT EXISTS findings (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  commit_id INT,
                  scan_type VARCHAR(10),
                  cve_id VARCHAR(50),
                  package VARCHAR(255),
                  title VARCHAR(500),
                  severity VARCHAR(12),
                  cvss DOUBLE DEFAULT 0,
                  epss DOUBLE,
                  epss_percentile DOUBLE,
                  kev BOOLEAN DEFAULT FALSE,
                  risk_score DOUBLE DEFAULT 0,
                  ai_summary TEXT,
                  ai_fix TEXT,
                  created_at DATETIME
                )""");

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM policy_rules")) {
                rs.next();
                if (rs.getInt(1) == 0) seedDefaults(st);
            }
        }
    }

    /** Default governance ruleset (see PolicyEngine for the resulting cascade). */
    private static void seedDefaults(Statement st) throws SQLException {
        rule(st, "Block critical vulnerabilities", "critical", "gt", 0, "AUTO_REJECT", 10);
        rule(st, "Escalate excessive high findings", "high", "gt", 3, "ESCALATE", 20);
        rule(st, "Review high severity findings", "high", "gt", 0, "MANUAL_REVIEW", 30);
        rule(st, "Auto-approve clean builds", "critical", "lte", 0, "AUTO_APPROVE", 40);
    }

    private static void rule(Statement st, String name, String field, String op,
                             int threshold, String action, int priority) throws SQLException {
        st.execute("INSERT INTO policy_rules "
                 + "(rule_name, condition_field, operator, threshold_value, action, priority, active) VALUES ("
                 + "'" + name + "','" + field + "','" + op + "'," + threshold + ",'" + action + "'," + priority + ",TRUE)");
    }

    private static boolean columnExists(Connection c, String table, String col) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                   + "WHERE LOWER(table_name)=? AND LOWER(column_name)=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table.toLowerCase());
            ps.setString(2, col.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1) > 0; }
        }
    }
}
