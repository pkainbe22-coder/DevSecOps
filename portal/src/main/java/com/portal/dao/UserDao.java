package com.portal.dao;

import com.portal.model.User;
import com.portal.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Data access for users. Plain JDBC + PreparedStatement (no ORM). */
public class UserDao {

    public User findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, role FROM users WHERE username = ?";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("role"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByUsername failed", e);
        }
        return null;
    }

    /** Insert a user only if the username does not already exist. Returns true if inserted. */
    public boolean insertIfAbsent(String username, String passwordHash, String role) {
        String sql = "INSERT INTO users (username, password_hash, role) "
                   + "SELECT ?, ?, ? FROM DUAL "
                   + "WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = ?)";
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, role);
            ps.setString(4, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("insertIfAbsent failed", e);
        }
    }
}
