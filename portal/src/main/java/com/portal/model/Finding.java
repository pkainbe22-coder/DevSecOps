package com.portal.model;

/**
 * One individual security finding (a single CVE from SCA, or a single ZAP alert from DAST),
 * enriched with real-world threat intelligence:
 *   - epss            : EPSS exploit probability (0..1) — likelihood of exploitation in 30 days
 *   - epssPercentile  : where this CVE ranks vs all CVEs (0..1)
 *   - kev             : on CISA's Known Exploited Vulnerabilities list (actively exploited)
 *   - riskScore       : 0..100 contextual score combining CVSS severity + real-world exploitability
 *   - aiSummary/aiFix : AI Security Analyst explanation + suggested remediation
 */
public class Finding {
    public int id;
    public int commitId;
    public String scanType;       // SCA | DAST | SAST
    public String cveId;
    public String pkg;            // affected package / location
    public String title;
    public String severity;       // CRITICAL | HIGH | MEDIUM | LOW
    public double cvss;
    public Double epss;           // nullable until enriched
    public Double epssPercentile;
    public boolean kev;
    public double riskScore;
    public String aiSummary;
    public String aiFix;

    public boolean hasCve() {
        return cveId != null && cveId.startsWith("CVE-");
    }

    public boolean hasAi() {
        return (aiSummary != null && !aiSummary.isBlank()) || (aiFix != null && !aiFix.isBlank());
    }

    /** Risk band for badge colour, derived from the contextual risk score. */
    public String riskBand() {
        if (riskScore >= 80) return "critical";
        if (riskScore >= 60) return "high";
        if (riskScore >= 30) return "medium";
        return "low";
    }

    public String epssPctDisplay() {
        return epss == null ? "—" : Math.round(epss * 100) + "%";
    }
    public String riskDisplay() {
        return String.valueOf(Math.round(riskScore));
    }

    // Getters — JSTL EL needs JavaBean accessors.
    public int getId() { return id; }
    public int getCommitId() { return commitId; }
    public String getScanType() { return scanType; }
    public String getCveId() { return cveId; }
    public String getPkg() { return pkg; }
    public String getTitle() { return title; }
    public String getSeverity() { return severity; }
    public double getCvss() { return cvss; }
    public Double getEpss() { return epss; }
    public Double getEpssPercentile() { return epssPercentile; }
    public boolean isKev() { return kev; }
    public double getRiskScore() { return riskScore; }
    public String getAiSummary() { return aiSummary; }
    public String getAiFix() { return aiFix; }
}
