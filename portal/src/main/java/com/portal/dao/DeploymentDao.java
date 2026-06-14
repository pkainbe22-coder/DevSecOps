package com.portal.dao;

import com.portal.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** deployments — recorded by Operations after the gate is passed. */
public class DeploymentDao {

    public void record(int commitId, String status, String environment, int opsUserId) {
        String sql = """
            INSERT INTO deployments (commit_id, status, environment, ops_user_id, deployed_at)
            VALUES (?,?,?,?,?)
            """;
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commitId);
            ps.setString(2, status);
            ps.setString(3, environment);
            ps.setInt(4, opsUserId);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deployment record failed", e);
        }
    }
}
