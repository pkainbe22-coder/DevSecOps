package com.portal.auth;

import com.portal.util.Db;
import com.portal.util.Env;
import com.portal.util.Passwords;

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
        scan(st, 5, "SAST", "SonarQube", 0, 0, 2, 1);
        scan(st, 5, "DAST", "ZAP", 0, 0, 1, 9);
        scan(st, 6, "SCA", "DependencyCheck", 3, 4, 2, 0);   // Log4Shell-style critical
        scan(st, 6, "SAST", "SonarQube", 0, 1, 1, 0);

        // approvals : (commit, decision, secUser, comment)
        approve(st, 1, "APPROVED", 3, "Only a minor finding - acceptable.");
        approve(st, 2, "PENDING", null, null);
        approve(st, 3, "APPROVED", 3, "Reviewed DAST warnings - non-blocking headers.");
        approve(st, 4, "APPROVED", 3, "Clean scan.");
        approve(st, 5, "PENDING", null, null);
        approve(st, 6, "REJECTED", 3, "Critical CVE (Log4Shell) in dependency - blocked.");

        // deployments : (commit, status, env, opsUser)
        deploy(st, 1, "DEPLOYED", "production", 4);
        deploy(st, 4, "DEPLOYED", "production", 4);
        // commit 3 approved but not yet deployed (sits in ops queue)
    }

    private void commit(Statement st, String hash, String author, String msg, String branch,
                        String when, String url) throws SQLException {
        st.execute("INSERT INTO commits (commit_hash, author, message, branch, repo, gitea_url, committed_at) VALUES ('"
                 + hash + "','" + author + "','" + esc(msg) + "','" + branch + "','my-app','" + url + "','" + when + "')");
    }
    private void scan(Statement st, int cid, String type, String tool, int cr, int hi, int me, int lo) throws SQLException {
        st.execute("INSERT INTO scan_results (commit_id, scan_type, tool, critical, high, medium, low, scanned_at) VALUES ("
                 + cid + ",'" + type + "','" + tool + "'," + cr + "," + hi + "," + me + "," + lo + ", CURRENT_TIMESTAMP)");
    }
    private void approve(Statement st, int cid, String dec, Integer u, String comment) throws SQLException {
        st.execute("INSERT INTO deployment_approvals (commit_id, decision, security_user_id, comment, decided_at) VALUES ("
                 + cid + ",'" + dec + "'," + (u == null ? "NULL" : u) + ","
                 + (comment == null ? "NULL" : "'" + esc(comment) + "'") + ","
                 + (u == null ? "NULL" : "CURRENT_TIMESTAMP") + ")");
    }
    private void deploy(Statement st, int cid, String status, String env, int u) throws SQLException {
        st.execute("INSERT INTO deployments (commit_id, status, environment, ops_user_id, deployed_at) VALUES ("
                 + cid + ",'" + status + "','" + env + "'," + u + ", CURRENT_TIMESTAMP)");
    }
    private String esc(String s) { return s == null ? "" : s.replace("'", "''"); }
}
