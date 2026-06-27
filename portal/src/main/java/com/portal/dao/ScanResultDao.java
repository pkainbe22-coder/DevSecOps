package com.portal.dao;

import com.portal.model.Severity;
import com.portal.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** scan_results writes. Idempotent per (commit_id, scan_type) — see UNIQUE in schema. */
public class ScanResultDao {

    /** Insert-or-update one scan's severity counts for a commit (M8 idempotency). */
    public void upsert(int commitId, String scanType, String tool, Severity sev, String reportUrl) {
        String sql = """
            INSERT INTO scan_results
              (commit_id, scan_type, tool, critical, high, medium, low, report_url, scanned_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
              tool=VALUES(tool), critical=VALUES(critical), high=VALUES(high),
              medium=VALUES(medium), low=VALUES(low), report_url=VALUES(report_url),
              scanned_at=VALUES(scanned_at)
            """;
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commitId);
            ps.setString(2, scanType);
            ps.setString(3, tool);
            ps.setInt(4, sev.critical);
            ps.setInt(5, sev.high);
            ps.setInt(6, sev.medium);
            ps.setInt(7, sev.low);
            ps.setString(8, reportUrl);
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("scan_results upsert failed", e);
        }
    }

    /** Aggregate severity across all scan types (SAST+SCA+DAST) for one commit. */
    public Severity totalsForCommit(int commitId) {
        String sql = """
            SELECT COALESCE(SUM(critical),0) c, COALESCE(SUM(high),0) h,
                   COALESCE(SUM(medium),0) m, COALESCE(SUM(low),0) l
            FROM scan_results WHERE commit_id = ?
            """;
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new Severity();
                return new Severity(rs.getInt("c"), rs.getInt("h"), rs.getInt("m"), rs.getInt("l"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("totalsForCommit failed", e);
        }
    }
}
