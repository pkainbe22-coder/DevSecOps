package com.portal.dao;

import com.portal.model.PolicyRule;
import com.portal.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** policy_rules CRUD. Plain JDBC, consistent with the other DAOs. */
public class PolicyRuleDao {

    /** All rules (active + inactive) for the config page, lowest priority first. */
    public List<PolicyRule> findAllOrdered() {
        return query("SELECT * FROM policy_rules ORDER BY priority ASC, id ASC");
    }

    /** Active rules only, evaluation order — used by the PolicyEngine. */
    public List<PolicyRule> findActiveOrdered() {
        return query("SELECT * FROM policy_rules WHERE active = TRUE ORDER BY priority ASC, id ASC");
    }

    private List<PolicyRule> query(String sql) {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<PolicyRule> out = new ArrayList<>();
            while (rs.next()) out.add(map(rs));
            return out;
        } catch (SQLException e) {
            throw new RuntimeException("policy_rules query failed", e);
        }
    }

    /** Update an existing rule's editable fields (Security saves the config table). */
    public void update(PolicyRule r) {
        String sql = """
            UPDATE policy_rules
            SET rule_name=?, condition_field=?, operator=?, threshold_value=?, action=?, priority=?, active=?
            WHERE id=?
            """;
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, r);
            ps.setInt(8, r.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("policy_rules update failed", e);
        }
    }

    /** Insert a new rule; returns the generated id. */
    public int insert(PolicyRule r) {
        String sql = "INSERT INTO policy_rules "
                   + "(rule_name, condition_field, operator, threshold_value, action, priority, active) "
                   + "VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, r);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("policy_rules insert failed", e);
        }
    }

    public void delete(int id) {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM policy_rules WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("policy_rules delete failed", e);
        }
    }

    private void bind(PreparedStatement ps, PolicyRule r) throws SQLException {
        ps.setString(1, r.ruleName);
        ps.setString(2, r.conditionField);
        ps.setString(3, r.operator);
        ps.setInt(4, r.thresholdValue);
        ps.setString(5, r.action);
        ps.setInt(6, r.priority);
        ps.setBoolean(7, r.active);
    }

    private PolicyRule map(ResultSet rs) throws SQLException {
        PolicyRule r = new PolicyRule();
        r.id = rs.getInt("id");
        r.ruleName = rs.getString("rule_name");
        r.conditionField = rs.getString("condition_field");
        r.operator = rs.getString("operator");
        r.thresholdValue = rs.getInt("threshold_value");
        r.action = rs.getString("action");
        r.priority = rs.getInt("priority");
        r.active = rs.getBoolean("active");
        return r;
    }
}
