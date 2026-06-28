package com.portal.dao;

import com.portal.model.Finding;
import com.portal.util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** findings table: individual CVE/alert rows + threat-intel enrichment. Plain JDBC. */
public class FindingDao {

    /**
     * Replace all findings of one scan type for a commit (idempotent on re-push):
     * delete the old rows, insert the fresh set in a single transaction.
     */
    public void replaceForScan(int commitId, String scanType, List<Finding> findings) {
        try (Connection conn = Db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM findings WHERE commit_id=? AND scan_type=?")) {
                del.setInt(1, commitId);
                del.setString(2, scanType);
                del.executeUpdate();
            }
            String ins = """
                INSERT INTO findings
                  (commit_id, scan_type, cve_id, package, title, severity, cvss,
                   epss, epss_percentile, kev, risk_score, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(ins)) {
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                for (Finding f : findings) {
                    ps.setInt(1, commitId);
                    ps.setString(2, f.scanType);
                    ps.setString(3, f.cveId);
                    ps.setString(4, f.pkg);
                    ps.setString(5, f.title);
                    ps.setString(6, f.severity);
                    ps.setDouble(7, f.cvss);
                    if (f.epss == null) ps.setNull(8, java.sql.Types.DOUBLE); else ps.setDouble(8, f.epss);
                    if (f.epssPercentile == null) ps.setNull(9, java.sql.Types.DOUBLE); else ps.setDouble(9, f.epssPercentile);
                    ps.setBoolean(10, f.kev);
                    ps.setDouble(11, f.riskScore);
                    ps.setTimestamp(12, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("findings replaceForScan failed", e);
        }
    }

    /** All findings for a commit, highest risk first (for the drill-down view). */
    public List<Finding> findByCommit(int commitId) {
        return query("SELECT * FROM findings WHERE commit_id=? ORDER BY risk_score DESC, cvss DESC",
                ps -> ps.setInt(1, commitId));
    }

    /** Top findings across all commits by risk — the org-wide "fix these first" list. */
    public List<Finding> findTopByRisk(int limit) {
        return query("SELECT * FROM findings ORDER BY risk_score DESC, cvss DESC LIMIT ?",
                ps -> ps.setInt(1, limit));
    }

    /** Distinct CVE ids needing enrichment (no EPSS yet), for batch threat-intel lookup. */
    public List<String> cveIdsMissingEpss() {
        List<String> out = new ArrayList<>();
        String sql = "SELECT DISTINCT cve_id FROM findings WHERE epss IS NULL AND cve_id LIKE 'CVE-%'";
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("cveIdsMissingEpss failed", e);
        }
        return out;
    }

    /** Apply threat-intel enrichment to every finding of a CVE (across commits). */
    public void enrich(String cveId, Double epss, Double percentile, boolean kev, double riskScore) {
        String sql = "UPDATE findings SET epss=?, epss_percentile=?, kev=?, risk_score=? WHERE cve_id=?";
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (epss == null) ps.setNull(1, java.sql.Types.DOUBLE); else ps.setDouble(1, epss);
            if (percentile == null) ps.setNull(2, java.sql.Types.DOUBLE); else ps.setDouble(2, percentile);
            ps.setBoolean(3, kev);
            ps.setDouble(4, riskScore);
            ps.setString(5, cveId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("findings enrich failed", e);
        }
    }

    public Finding findById(int id) {
        List<Finding> rows = query("SELECT * FROM findings WHERE id=?", ps -> ps.setInt(1, id));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Store the AI Security Analyst's explanation + suggested fix for a finding. */
    public void saveAi(int id, String summary, String fix) {
        String sql = "UPDATE findings SET ai_summary=?, ai_fix=? WHERE id=?";
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, summary);
            ps.setString(2, fix);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("findings saveAi failed", e);
        }
    }

    /** Counts for the posture KPIs: total findings, KEV-listed, exploitable (epss>=0.5). */
    public int[] postureCounts() {
        String sql = """
            SELECT COUNT(*) total,
                   COALESCE(SUM(CASE WHEN kev THEN 1 ELSE 0 END),0) kev,
                   COALESCE(SUM(CASE WHEN epss >= 0.5 THEN 1 ELSE 0 END),0) exploitable
            FROM findings
            """;
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new int[]{ rs.getInt("total"), rs.getInt("kev"), rs.getInt("exploitable") };
        } catch (SQLException e) {
            throw new RuntimeException("postureCounts failed", e);
        }
    }

    /** Finding counts by severity: [critical, high, medium, low] — for the analytics charts. */
    public int[] severityCounts() {
        String sql = """
            SELECT COALESCE(SUM(CASE WHEN severity='CRITICAL' THEN 1 ELSE 0 END),0) c,
                   COALESCE(SUM(CASE WHEN severity='HIGH'     THEN 1 ELSE 0 END),0) h,
                   COALESCE(SUM(CASE WHEN severity='MEDIUM'   THEN 1 ELSE 0 END),0) m,
                   COALESCE(SUM(CASE WHEN severity='LOW'      THEN 1 ELSE 0 END),0) l
            FROM findings
            """;
        return four(sql, "c", "h", "m", "l");
    }

    /** Finding counts by scanner: [SAST, SCA, DAST]. */
    public int[] scanTypeCounts() {
        String sql = """
            SELECT COALESCE(SUM(CASE WHEN scan_type='SAST' THEN 1 ELSE 0 END),0) a,
                   COALESCE(SUM(CASE WHEN scan_type='SCA'  THEN 1 ELSE 0 END),0) b,
                   COALESCE(SUM(CASE WHEN scan_type='DAST' THEN 1 ELSE 0 END),0) c
            FROM findings
            """;
        int[] r = four(sql + " ", "a", "b", "c", "a");   // reuse helper; 4th col ignored
        return new int[]{ r[0], r[1], r[2] };
    }

    private int[] four(String sql, String c1, String c2, String c3, String c4) {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new int[]{ rs.getInt(c1), rs.getInt(c2), rs.getInt(c3), rs.getInt(c4) };
        } catch (SQLException e) {
            throw new RuntimeException("aggregate query failed", e);
        }
    }

    @FunctionalInterface private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<Finding> query(String sql, Binder binder) {
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<Finding> out = new ArrayList<>();
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findings query failed", e);
        }
    }

    private Finding map(ResultSet rs) throws SQLException {
        Finding f = new Finding();
        f.id = rs.getInt("id");
        f.commitId = rs.getInt("commit_id");
        f.scanType = rs.getString("scan_type");
        f.cveId = rs.getString("cve_id");
        f.pkg = rs.getString("package");
        f.title = rs.getString("title");
        f.severity = rs.getString("severity");
        f.cvss = rs.getDouble("cvss");
        double epss = rs.getDouble("epss"); f.epss = rs.wasNull() ? null : epss;
        double pct = rs.getDouble("epss_percentile"); f.epssPercentile = rs.wasNull() ? null : pct;
        f.kev = rs.getBoolean("kev");
        f.riskScore = rs.getDouble("risk_score");
        f.aiSummary = rs.getString("ai_summary");
        f.aiFix = rs.getString("ai_fix");
        return f;
    }
}
