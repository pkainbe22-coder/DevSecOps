package com.portal.dao;

import com.portal.policy.PolicyDecision;
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

    /**
     * Policy-as-Code gate: create the approval row from an automatic policy decision,
     * but only if none exists yet — re-pushing a commit must never silently overturn a
     * decision a human (or a prior policy run) already recorded. Idempotent on commit_id.
     */
    public void ensureDecision(int commitId, PolicyDecision d) {
        String sql = """
            INSERT INTO deployment_approvals (commit_id, decision, decision_source, comment, decided_at)
            SELECT ?, ?, ?, ?, ? FROM DUAL
            WHERE NOT EXISTS (SELECT 1 FROM deployment_approvals WHERE commit_id = ?)
            """;
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commitId);
            ps.setString(2, d.decision);
            ps.setString(3, d.source);
            ps.setString(4, d.comment);
            ps.setTimestamp(5, d.decidedNow ? Timestamp.valueOf(LocalDateTime.now()) : null);
            ps.setInt(6, commitId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ensureDecision failed", e);
        }
    }

    /** Security decision: APPROVED or REJECTED, with comment + who decided. A human
     *  decision is always recorded as MANUAL (overrides any prior auto decision_source). */
    public void decide(int commitId, String decision, int securityUserId, String comment) {
        String sql = """
            UPDATE deployment_approvals
            SET decision=?, decision_source='MANUAL', security_user_id=?, comment=?, decided_at=?
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
