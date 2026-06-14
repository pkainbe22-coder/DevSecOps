package com.portal.dao;

import com.portal.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** deployment_approvals — the gate. One row per commit (UNIQUE commit_id). */
public class ApprovalDao {

    /** Create a PENDING row if none exists for this commit (idempotent). */
    public void ensurePending(int commitId) {
        String sql = """
            INSERT INTO deployment_approvals (commit_id, decision)
            SELECT ?, 'PENDING' FROM DUAL
            WHERE NOT EXISTS (SELECT 1 FROM deployment_approvals WHERE commit_id = ?)
            """;
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commitId);
            ps.setInt(2, commitId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ensurePending failed", e);
        }
    }

    /** Security decision: APPROVED or REJECTED, with comment + who decided. */
    public void decide(int commitId, String decision, int securityUserId, String comment) {
        String sql = """
            UPDATE deployment_approvals
            SET decision=?, security_user_id=?, comment=?, decided_at=?
            WHERE commit_id=?
            """;
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, decision);
            ps.setInt(2, securityUserId);
            ps.setString(3, comment);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(5, commitId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("decide failed", e);
        }
    }

    /** Guard for ops actions: confirm a commit is actually APPROVED before deploy. */
    public boolean isApproved(int commitId) {
        String sql = "SELECT 1 FROM deployment_approvals WHERE commit_id=? AND decision='APPROVED'";
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commitId);
            try (var rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            throw new RuntimeException("isApproved failed", e);
        }
    }
}
