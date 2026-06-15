package com.portal.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.portal.model.Severity;

/**
 * Parses an OWASP Dependency-Check JSON report into severity counts.
 *
 * Report shape (abridged):
 *   { "dependencies": [ { "vulnerabilities": [ { "severity": "HIGH" }, ... ] }, ... ] }
 *
 * Dependency-Check severities are CRITICAL / HIGH / MEDIUM / LOW (derived from CVSS).
 * Each vulnerability is counted once.
 */
public final class DependencyCheckParser {
    private DependencyCheckParser() {}

    public static Severity parse(JsonObject report) {
        Severity sev = new Severity();
        if (report == null || !report.has("dependencies")) return sev;
        JsonArray deps = report.getAsJsonArray("dependencies");
        for (var d : deps) {
            JsonObject dep = d.getAsJsonObject();
            if (!dep.has("vulnerabilities") || dep.get("vulnerabilities").isJsonNull()) continue;
            for (var v : dep.getAsJsonArray("vulnerabilities")) {
                JsonObject vuln = v.getAsJsonObject();
                String s = vuln.has("severity") && !vuln.get("severity").isJsonNull()
                        ? vuln.get("severity").getAsString().trim().toUpperCase() : "";
                switch (s) {
                    case "CRITICAL" -> sev.critical++;
                    case "HIGH"     -> sev.high++;
                    case "MEDIUM", "MODERATE" -> sev.medium++;
                    case "LOW"      -> sev.low++;
                    default         -> { /* unknown/none -> ignore */ }
                }
            }
        }
        return sev;
    }
}
