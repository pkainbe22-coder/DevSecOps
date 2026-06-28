package com.portal.auth;

import com.portal.util.Db;
import com.portal.util.Env;
import com.portal.util.Passwords;
import com.portal.util.PolicySchema;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Self-contained DEMO bootstrap. Only runs when DEMO_MODE=true (set in the demo
 * Docker image), so production (MySQL) deployments are unaffected.
 *
 * It creates the schema in an embedded H2 database (MySQL-compatibility mode),
 * seeds the three role users, and populates realistic demo commits, scan results,
 * approvals, and deployments — so every dashboard is populated with no pipeline,
 * no MySQL, and no external services. This is what makes a single-container,
 * permanently hostable demo possible.
 */
@WebListener
public class DemoBootstrap implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        if (!"true".equalsIgnoreCase(Env.get("DEMO_MODE", "false"))) return;
        try (Connection c = Db.getConnection(); Statement st = c.createStatement()) {
            createSchema(st);
            PolicySchema.apply(c);          // decision_source column + policy_rules + default ruleset
            seedUsers(st);
            if (isEmpty(st)) seedDemoData(st);
            System.out.println("[demo] bootstrap complete");
        } catch (Exception e) {
            sce.getServletContext().log("[demo] bootstrap failed: " + e.getMessage());
        }
    }

    private void createSchema(Statement st) throws SQLException {
        st.execute("CREATE TABLE IF NOT EXISTS users (id INT AUTO_INCREMENT PRIMARY KEY, "
                 + "username VARCHAR(100) UNIQUE NOT NULL, password_hash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL)");
        st.execute("CREATE TABLE IF NOT EXISTS commits (id INT AUTO_INCREMENT PRIMARY KEY, "
                 + "commit_hash VARCHAR(60) UNIQUE NOT NULL, author VARCHAR(100), message TEXT, branch VARCHAR(100), "
                 + "repo VARCHAR(150), gitea_url VARCHAR(500), committed_at DATETIME)");
        st.execute("CREATE TABLE IF NOT EXISTS scan_results (id INT AUTO_INCREMENT PRIMARY KEY, commit_id INT, "
                 + "scan_type VARCHAR(10) NOT NULL, tool VARCHAR(50), critical INT DEFAULT 0, high INT DEFAULT 0, "
                 + "medium INT DEFAULT 0, low INT DEFAULT 0, report_url VARCHAR(500), scanned_at DATETIME, "
                 + "CONSTRAINT uq_commit_scan UNIQUE (commit_id, scan_type))");
        st.execute("CREATE TABLE IF NOT EXISTS deployment_approvals (id INT AUTO_INCREMENT PRIMARY KEY, "
                 + "commit_id INT UNIQUE, decision VARCHAR(10) DEFAULT 'PENDING', security_user_id INT, comment TEXT, decided_at DATETIME)");
        st.execute("CREATE TABLE IF NOT EXISTS deployments (id INT AUTO_INCREMENT PRIMARY KEY, commit_id INT, "
                 + "status VARCHAR(15) DEFAULT 'NOT_DEPLOYED', environment VARCHAR(50), ops_user_id INT, deployed_at DATETIME)");
    }

    private void seedUsers(Statement st) throws SQLException {
        user(st, "praks", "praks123", "DEVELOPER");
        user(st, "dev", "dev123", "DEVELOPER");
        user(st, "sec", "sec123", "SECURITY");
        user(st, "ops", "ops123", "OPERATIONS");
    }

    private void user(Statement st, String u, String pw, String role) throws SQLException {
        st.execute("INSERT INTO users (username, password_hash, role) SELECT '" + u + "','"
                 + Passwords.hash(pw) + "','" + role + "' WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='" + u + "')");
    }

    private boolean isEmpty(Statement st) throws SQLException {
        try (var rs = st.executeQuery("SELECT COUNT(*) FROM commits")) { rs.next(); return rs.getInt(1) == 0; }
    }

    /** A small but realistic dataset spanning all gate states + all three scan types. */
    private void seedDemoData(Statement st) throws SQLException {
        // commits (id 1..6)
        commit(st, "a1b2c3d4e5", "praks", "Add login endpoint and user model", "main", "2026-06-18 10:12:00",
               "https://github.com/pkainbe22-coder/DevSecOps");
        commit(st, "f6e5d4c3b2", "praks", "Bump dependencies, add report export", "main", "2026-06-18 12:40:00",
               "https://github.com/pkainbe22-coder/DevSecOps");
        commit(st, "9a8b7c6d5e", "dev", "Refactor scan-results receiver", "main", "2026-06-18 14:05:00",
               "https://github.com/pkainbe22-coder/DevSecOps");
        commit(st, "1f2e3d4c5b", "praks", "Hotfix: null check in approval gate", "main", "2026-06-18 16:22:00",
               "https://github.com/pkainbe22-coder/DevSecOps");
        commit(st, "7g8h9i0j1k", "dev", "Add severity badges to dashboard", "feature/ui", "2026-06-19 09:30:00",
               "https://github.com/pkainbe22-coder/DevSecOps");
        commit(st, "2b3c4d5e6f", "praks", "Introduce log4j logging (vulnerable dep)", "main", "2026-06-19 11:15:00",
               "https://github.com/pkainbe22-coder/DevSecOps");

        // scan_results : (commit, type, tool, crit, high, med, low)
        scan(st, 1, "SAST", "SonarQube", 0, 0, 1, 0);
        scan(st, 1, "DAST", "ZAP", 0, 0, 2, 7);
        scan(st, 2, "SAST", "SonarQube", 0, 1, 0, 2);
        scan(st, 2, "SCA", "DependencyCheck", 0, 2, 3, 1);
        scan(st, 3, "SAST", "SonarQube", 0, 0, 0, 1);
        scan(st, 3, "DAST", "ZAP", 0, 0, 4, 13);
        scan(st, 4, "SAST", "SonarQube", 0, 0, 0, 0);
        scan(st, 5, "SAST", "SonarQube", 0, 1, 2, 1);        // one high → manual review queue
        scan(st, 5, "DAST", "ZAP", 0, 0, 1, 9);
        scan(st, 6, "SCA", "DependencyCheck", 3, 4, 2, 0);   // Log4Shell-style critical
        scan(st, 6, "SAST", "SonarQube", 0, 1, 1, 0);

        // approvals : (commit, decision, decision_source, secUser, comment)
        // The Policy-as-Code gate auto-resolves the clear-cut cases and routes the rest
        // (commits with high findings) to the manual review queue.
        approve(st, 1, "APPROVED", "AUTO_APPROVED", null, "Auto-approved: no critical or high severity findings");
        approve(st, 2, "PENDING",  "MANUAL", null, null);
        approve(st, 3, "APPROVED", "AUTO_APPROVED", null, "Auto-approved: no critical or high severity findings");
        approve(st, 4, "APPROVED", "AUTO_APPROVED", null, "Auto-approved: no critical or high severity findings");
        approve(st, 5, "PENDING",  "MANUAL", null, null);
        approve(st, 6, "REJECTED", "AUTO_REJECTED", null,
                "Auto-rejected: 3 critical severity finding(s) require remediation before deployment");

        // deployments : (commit, status, env, opsUser)
        deploy(st, 1, "DEPLOYED", "production", 4);
        deploy(st, 4, "DEPLOYED", "production", 4);
        // commit 3 approved but not yet deployed (sits in ops queue)

        seedFindings(st);
    }

    /**
     * Individual findings for the Risk Intelligence dashboard — each CVE enriched with
     * EPSS exploit-probability, CISA KEV membership, and a contextual risk score. The
     * Log4Shell CVEs carry pre-generated AI analysis so the demo shows it without a key.
     */
    private void seedFindings(Statement st) throws SQLException {
        String l4sSummary = "Apache Log4j2 allows unauthenticated remote code execution via JNDI lookups "
            + "embedded in logged strings (Log4Shell). Because logging is everywhere and the exploit is "
            + "trivial to trigger, this is one of the most exploited vulnerabilities ever recorded - it is on "
            + "CISA's Known Exploited Vulnerabilities list with a near-certain exploit probability.";
        String l4sFix = "Upgrade log4j-core to 2.17.1 or later. As an immediate mitigation, set the system "
            + "property log4j2.formatMsgNoLookups=true, or remove the JndiLookup class from the classpath.";

        // commit 6 — SCA: the log4j CVE cluster (the headline)
        finding(st, 6, "SCA", "CVE-2021-44228", "log4j-core-2.14.1.jar",
                "Remote code execution via JNDI lookup substitution (Log4Shell)",
                "CRITICAL", 10.0, 0.97, true, 100, l4sSummary, l4sFix);
        finding(st, 6, "SCA", "CVE-2021-45046", "log4j-core-2.14.1.jar",
                "RCE / denial of service via Thread Context Map lookups",
                "CRITICAL", 9.0, 0.94, true, 90,
                "A follow-up to Log4Shell: incomplete fixes in 2.15.0 still allow RCE and DoS via crafted Thread "
                + "Context Map input. Also actively exploited and on the CISA KEV list.",
                "Upgrade log4j-core to 2.17.1 or later - earlier patches were insufficient.");
        finding(st, 6, "SCA", "CVE-2021-44832", "log4j-core-2.14.1.jar",
                "RCE via JDBC Appender with attacker-controlled configuration",
                "MEDIUM", 6.6, 0.31, false, 53, null, null);
        finding(st, 6, "SCA", "CVE-2021-45105", "log4j-core-2.14.1.jar",
                "Infinite recursion denial of service in lookup evaluation",
                "MEDIUM", 5.9, 0.18, false, 47, null, null);
        finding(st, 6, "SAST", "java:S2076", "CommandRunner.java",
                "OS command built from user-controlled input (command injection)",
                "HIGH", 0, null, false, 70, null, null);

        // commit 2 — SCA + SAST: a mix of severities
        finding(st, 2, "SCA", "CVE-2022-42003", "jackson-databind-2.9.0.jar",
                "Deeply nested wrapper array deserialization causes stack overflow (DoS)",
                "HIGH", 7.5, 0.02, false, 41, null, null);
        finding(st, 2, "SCA", "CVE-2020-8908", "guava-30.0-jre.jar",
                "Temp directory created with insecure permissions (information disclosure)",
                "LOW", 3.3, 0.004, false, 16, null, null);
        finding(st, 2, "SAST", "java:S5852", "InputValidator.java",
                "Regular expression vulnerable to catastrophic backtracking (ReDoS)",
                "HIGH", 0, null, false, 70, null, null);

        // DAST (ZAP) alerts — no CVE/CVSS; risk derived from severity
        finding(st, 1, "DAST", "ZAP-10202", "http://staging:8080",
                "Absence of Anti-CSRF Tokens", "MEDIUM", 0, null, false, 40, null, null);
        finding(st, 1, "DAST", "ZAP-10021", "http://staging:8080",
                "X-Content-Type-Options header missing", "LOW", 0, null, false, 15, null, null);
        finding(st, 3, "DAST", "ZAP-10038", "http://staging:8080",
                "Content Security Policy (CSP) header not set", "MEDIUM", 0, null, false, 40, null, null);
        finding(st, 3, "DAST", "ZAP-10011", "http://staging:8080",
                "Cookie set without Secure flag", "LOW", 0, null, false, 15, null, null);
        finding(st, 5, "DAST", "ZAP-10098", "http://staging:8080",
                "Cross-Domain Misconfiguration (permissive CORS)", "MEDIUM", 0, null, false, 40, null, null);
    }

    private void finding(Statement st, int cid, String type, String cve, String pkg, String title,
                         String sev, double cvss, Double epss, boolean kev, double risk,
                         String aiSummary, String aiFix) throws SQLException {
        st.execute("INSERT INTO findings (commit_id, scan_type, cve_id, package, title, severity, cvss, "
                 + "epss, epss_percentile, kev, risk_score, ai_summary, ai_fix, created_at) VALUES ("
                 + cid + ",'" + type + "'," + sval(cve) + ",'" + esc(pkg) + "','" + esc(title) + "','" + sev + "',"
                 + cvss + "," + dval(epss) + "," + dval(epss) + "," + (kev ? "TRUE" : "FALSE") + "," + risk + ","
                 + sval(aiSummary) + "," + sval(aiFix) + ", CURRENT_TIMESTAMP)");
    }
    private String sval(String s) { return s == null ? "NULL" : "'" + esc(s) + "'"; }
    private String dval(Double d) { return d == null ? "NULL" : String.valueOf(d); }

    private void commit(Statement st, String hash, String author, String msg, String branch,
                        String when, String url) throws SQLException {
        st.execute("INSERT INTO commits (commit_hash, author, message, branch, repo, gitea_url, committed_at) VALUES ('"
                 + hash + "','" + author + "','" + esc(msg) + "','" + branch + "','my-app','" + url + "','" + when + "')");
    }
    private void scan(Statement st, int cid, String type, String tool, int cr, int hi, int me, int lo) throws SQLException {
        st.execute("INSERT INTO scan_results (commit_id, scan_type, tool, critical, high, medium, low, scanned_at) VALUES ("
                 + cid + ",'" + type + "','" + tool + "'," + cr + "," + hi + "," + me + "," + lo + ", CURRENT_TIMESTAMP)");
    }
    private void approve(Statement st, int cid, String dec, String source, Integer u, String comment) throws SQLException {
        st.execute("INSERT INTO deployment_approvals (commit_id, decision, decision_source, security_user_id, comment, decided_at) VALUES ("
                 + cid + ",'" + dec + "','" + source + "'," + (u == null ? "NULL" : u) + ","
                 + (comment == null ? "NULL" : "'" + esc(comment) + "'") + ","
                 + ("PENDING".equals(dec) ? "NULL" : "CURRENT_TIMESTAMP") + ")");
    }
    private void deploy(Statement st, int cid, String status, String env, int u) throws SQLException {
        st.execute("INSERT INTO deployments (commit_id, status, environment, ops_user_id, deployed_at) VALUES ("
                 + cid + ",'" + status + "','" + env + "'," + u + ", CURRENT_TIMESTAMP)");
    }
    private String esc(String s) { return s == null ? "" : s.replace("'", "''"); }
}
