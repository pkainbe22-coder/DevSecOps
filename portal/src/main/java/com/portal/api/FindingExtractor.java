package com.portal.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.portal.model.Finding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts INDIVIDUAL findings (one per CVE / per ZAP alert) from the raw scanner reports —
 * the granular data the Risk Intelligence layer needs, beyond the severity counts the
 * existing parsers produce.
 *
 * Dependency-Check JSON:  dependencies[].vulnerabilities[]  → one Finding per CVE
 * OWASP ZAP JSON:         site[].alerts[]                   → one Finding per alert
 *
 * A provisional risk score (CVSS-only) is set here; the threat-intel layer recomputes it
 * once EPSS / KEV enrichment is available.
 */
public final class FindingExtractor {
    private FindingExtractor() {}

    public static List<Finding> fromDependencyCheck(JsonObject report) {
        // Dependency-Check often reports the same CVE against both the standalone jar and
        // the war-embedded jar — dedupe by CVE, keeping the highest-CVSS occurrence.
        Map<String, Finding> byCve = new LinkedHashMap<>();
        if (report == null || !report.has("dependencies")) return new ArrayList<>();
        for (JsonElement de : report.getAsJsonArray("dependencies")) {
            JsonObject dep = de.getAsJsonObject();
            if (!dep.has("vulnerabilities") || dep.get("vulnerabilities").isJsonNull()) continue;
            String pkg = cleanPackage(str(dep, "fileName"));
            for (JsonElement ve : dep.getAsJsonArray("vulnerabilities")) {
                JsonObject v = ve.getAsJsonObject();
                Finding f = new Finding();
                f.scanType = "SCA";
                f.cveId = str(v, "name");
                f.pkg = pkg;
                f.title = trim(str(v, "description"), 480);
                f.severity = upper(str(v, "severity"));
                f.cvss = cvssScore(v);
                f.riskScore = baseRisk(f);
                Finding prev = byCve.get(f.cveId);
                if (prev == null || f.cvss > prev.cvss) byCve.put(f.cveId, f);
            }
        }
        return new ArrayList<>(byCve.values());
    }

    /** Strip the "my-app.war: " wrapper Dependency-Check prefixes onto embedded jars. */
    private static String cleanPackage(String pkg) {
        if (pkg == null) return null;
        int i = pkg.indexOf(": ");
        return i >= 0 ? pkg.substring(i + 2) : pkg;
    }

    public static List<Finding> fromZap(JsonObject report) {
        List<Finding> out = new ArrayList<>();
        if (report == null || !report.has("site")) return out;
        for (JsonElement se : report.getAsJsonArray("site")) {
            JsonObject site = se.getAsJsonObject();
            if (!site.has("alerts") || site.get("alerts").isJsonNull()) continue;
            String host = str(site, "@name");
            for (JsonElement ae : site.getAsJsonArray("alerts")) {
                JsonObject a = ae.getAsJsonObject();
                Finding f = new Finding();
                f.scanType = "DAST";
                f.cveId = "ZAP-" + str(a, "pluginid");     // no CVE for dynamic alerts
                f.pkg = host;
                f.title = trim(str(a, "alert"), 480);
                f.severity = riskName(asInt(a, "riskcode"));
                f.cvss = 0;                                  // ZAP has no CVSS
                f.riskScore = baseRisk(f);
                out.add(f);
            }
        }
        return out;
    }

    /** Provisional risk (0..100) from CVSS, or from severity when no CVSS (e.g. ZAP). */
    private static double baseRisk(Finding f) {
        if (f.cvss > 0) return Math.min(100, f.cvss * 10);
        return switch (f.severity == null ? "" : f.severity) {
            case "CRITICAL" -> 90; case "HIGH" -> 70; case "MEDIUM" -> 40; case "LOW" -> 15;
            default -> 0;
        };
    }

    private static double cvssScore(JsonObject v) {
        for (String k : new String[]{"cvssv4", "cvssv3", "cvssv2"}) {
            if (v.has(k) && v.get(k).isJsonObject()) {
                JsonObject c = v.getAsJsonObject(k);
                if (c.has("baseScore")) return c.get("baseScore").getAsDouble();
                if (c.has("score"))     return c.get("score").getAsDouble();
            }
        }
        return 0;
    }

    private static String riskName(int code) {
        return switch (code) { case 3 -> "HIGH"; case 2 -> "MEDIUM"; default -> "LOW"; };
    }

    private static String str(JsonObject o, String k) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
    private static int asInt(JsonObject o, String k) {
        try { return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsInt() : 0; }
        catch (NumberFormatException e) { return 0; }
    }
    private static String upper(String s) { return s == null ? null : s.trim().toUpperCase(); }
    private static String trim(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }
}
