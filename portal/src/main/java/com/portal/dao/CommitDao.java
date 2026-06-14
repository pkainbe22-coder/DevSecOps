package com.portal.dao;

import com.portal.model.Commit;
import com.portal.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Commits + the joined view the dashboards need. Plain JDBC. */
public class CommitDao {

    /**
     * Idempotent upsert on commit_hash (M8: re-pushing the same hash must not duplicate).
     * Returns the commit id (existing or newly inserted).
     */
    public int upsert(Commit c) {
        Integer existing = findIdByHash(c.commitHash);
        if (existing != null) {
            update(existing, c);
            return existing;
        }
        String sql = "INSERT INTO commits (commit_hash, author, message, branch, repo, gitea_url, committed_at) "
                   + "VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, c);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("commit upsert failed", e);
        }
    }

    private void update(int id, Commit c) {
        String sql = "UPDATE commits SET author=?, message=?, branch=?, repo=?, gitea_url=?, committed_at=? "
                   + "WHERE id=?";
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.author);
            ps.setString(2, c.message);
            ps.setString(3, c.branch);
            ps.setString(4, c.repo);
            ps.setString(5, c.giteaUrl);
            ps.setTimestamp(6, c.committedAt == null ? null : Timestamp.valueOf(c.committedAt));
            ps.setInt(7, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("commit update failed", e);
        }
    }

    private void bind(PreparedStatement ps, Commit c) throws SQLException {
        ps.setString(1, c.commitHash);
        ps.setString(2, c.author);
        ps.setString(3, c.message);
        ps.setString(4, c.branch);
        ps.setString(5, c.repo);
        ps.setString(6, c.giteaUrl);
        ps.setTimestamp(7, c.committedAt == null ? null : Timestamp.valueOf(c.committedAt));
    }

    public Integer findIdByHash(String hash) {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id FROM commits WHERE commit_hash=?")) {
            ps.setString(1, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findIdByHash failed", e);
        }
    }

    /** Developer view: this author's own commits, newest first. */
    public List<Commit> findByAuthor(String author) {
        String sql = "SELECT * FROM commits WHERE author=? ORDER BY committed_at DESC, id DESC";
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, author);
            return mapList(ps);
        } catch (SQLException e) {
            throw new RuntimeException("findByAuthor failed", e);
        }
    }

    /** Security view: all commits + summed severity + decision, newest first. */
    public List<Commit> findAllForSecurity() {
        String sql = """
            SELECT c.*, a.decision, a.comment AS approval_comment,
                   COALESCE(SUM(s.critical),0) crit, COALESCE(SUM(s.high),0) hi,
                   COALESCE(SUM(s.medium),0) med, COALESCE(SUM(s.low),0) lo
            FROM commits c
            LEFT JOIN deployment_approvals a ON a.commit_id = c.id
            LEFT JOIN scan_results s ON s.commit_id = c.id
            GROUP BY c.id, a.decision, a.comment
            ORDER BY c.committed_at DESC, c.id DESC
            """;
        return runJoined(sql, null);
    }

    /** Ops view: only APPROVED commits + their deploy status. */
    public List<Commit> findApprovedForOps() {
        String sql = """
            SELECT c.*, a.decision, a.comment AS approval_comment,
                   d.status AS deploy_status,
                   0 crit, 0 hi, 0 med, 0 lo
            FROM commits c
            JOIN deployment_approvals a ON a.commit_id = c.id AND a.decision = 'APPROVED'
            LEFT JOIN deployments d ON d.commit_id = c.id
            ORDER BY c.committed_at DESC, c.id DESC
            """;
        return runJoined(sql, null);
    }

    private List<Commit> runJoined(String sql, String param) {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<Commit> out = new ArrayList<>();
                while (rs.next()) {
                    Commit c = map(rs);
                    c.critical = rs.getInt("crit");
                    c.high = rs.getInt("hi");
                    c.medium = rs.getInt("med");
                    c.low = rs.getInt("lo");
                    c.decision = rs.getString("decision");
                    c.approvalComment = optional(rs, "approval_comment");
                    c.deployStatus = optional(rs, "deploy_status");
                    out.add(c);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("joined query failed", e);
        }
    }

    private List<Commit> mapList(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Commit> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        }
    }

    private Commit map(ResultSet rs) throws SQLException {
        Commit c = new Commit();
        c.id = rs.getInt("id");
        c.commitHash = rs.getString("commit_hash");
        c.author = rs.getString("author");
        c.message = rs.getString("message");
        c.branch = rs.getString("branch");
        c.repo = rs.getString("repo");
        c.giteaUrl = rs.getString("gitea_url");
        Timestamp ts = rs.getTimestamp("committed_at");
        c.committedAt = ts == null ? null : ts.toLocalDateTime();
        return c;
    }

    private String optional(ResultSet rs, String col) {
        try { return rs.getString(col); } catch (SQLException e) { return null; }
    }
}
