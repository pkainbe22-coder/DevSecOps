package com.portal.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.model.Finding;
import com.portal.model.Severity;
import com.portal.util.Env;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Pulls vulnerability counts from the SonarQube Web API and maps them to our
 * critical/high/medium/low buckets.
 *
 *   GET /api/issues/search?componentKeys=<key>&types=VULNERABILITY&facets=severities
 *
 * Sonar severities: BLOCKER, CRITICAL, MAJOR, MINOR, INFO.
 * Mapping: BLOCKER+CRITICAL -> critical, MAJOR -> high, MINOR -> medium, INFO -> low.
 * Auth: SONAR_API_TOKEN as HTTP Basic username (empty password).
 */
public class SonarClient {

    private final String baseUrl = Env.get("SONAR_BASE_URL", "http://localhost:9000");
    private final String token = Env.get("SONAR_API_TOKEN", "");
    private final HttpClient http = HttpClient.newHttpClient();

    public Severity vulnerabilityCounts(String projectKey) {
        Severity sev = new Severity();
        try {
            String url = baseUrl + "/api/issues/search?componentKeys=" + projectKey
                       + "&types=VULNERABILITY&facets=severities&ps=1";
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
            if (!token.isBlank()) {
                String basic = Base64.getEncoder()
                        .encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
                b.header("Authorization", "Basic " + basic);
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                System.err.println("[SonarClient] HTTP " + resp.statusCode() + " for " + projectKey);
                return sev;
            }
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            var facets = root.getAsJsonArray("facets");
            for (var f : facets) {
                JsonObject fo = f.getAsJsonObject();
                if (!"severities".equals(fo.get("property").getAsString())) continue;
                for (var v : fo.getAsJsonArray("values")) {
                    JsonObject vo = v.getAsJsonObject();
                    String key = vo.get("val").getAsString();
                    int count = vo.get("count").getAsInt();
                    switch (key) {
                        case "BLOCKER", "CRITICAL" -> sev.critical += count;
                        case "MAJOR" -> sev.high += count;
                        case "MINOR" -> sev.medium += count;
                        case "INFO"  -> sev.low += count;
                        default -> {}
                    }
                }
            }
        } catch (Exception e) {
            // Don't let a Sonar hiccup fail the whole receiver — record zeros, log it.
            System.err.println("[SonarClient] failed: " + e.getMessage());
        }
        return sev;
    }

    /**
     * Individual SAST vulnerabilities (not just counts) for the Risk Intelligence table.
     *   GET /api/issues/search?componentKeys=<key>&types=VULNERABILITY&ps=100
     * Mapped to Finding rows (no CVE/CVSS — SAST findings carry the Sonar rule + message).
     */
    public List<Finding> findings(String projectKey) {
        List<Finding> out = new ArrayList<>();
        try {
            String url = baseUrl + "/api/issues/search?componentKeys=" + projectKey
                       + "&types=VULNERABILITY&ps=100&p=1";
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
            if (!token.isBlank()) {
                String basic = Base64.getEncoder()
                        .encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
                b.header("Authorization", "Basic " + basic);
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return out;
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray issues = root.has("issues") ? root.getAsJsonArray("issues") : new JsonArray();
            for (var ie : issues) {
                JsonObject i = ie.getAsJsonObject();
                Finding f = new Finding();
                f.scanType = "SAST";
                f.cveId = str(i, "rule");                       // e.g. "java:S2076" — not a CVE
                f.pkg = basename(str(i, "component"));
                f.title = str(i, "message");
                f.severity = mapSeverity(str(i, "severity"));
                f.cvss = 0;
                f.riskScore = riskFor(f.severity);
                out.add(f);
            }
        } catch (Exception e) {
            System.err.println("[SonarClient] findings failed: " + e.getMessage());
        }
        return out;
    }

    private static String mapSeverity(String s) {
        return switch (s == null ? "" : s) {
            case "BLOCKER", "CRITICAL" -> "CRITICAL";
            case "MAJOR" -> "HIGH";
            case "MINOR" -> "MEDIUM";
            default -> "LOW";   // INFO
        };
    }
    private static double riskFor(String sev) {
        return switch (sev) { case "CRITICAL" -> 90; case "HIGH" -> 70; case "MEDIUM" -> 40; default -> 15; };
    }
    private static String basename(String component) {
        if (component == null) return null;
        String p = component;
        int colon = p.indexOf(':');
        if (colon >= 0) p = p.substring(colon + 1);
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }
    private static String str(JsonObject o, String k) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }

    public String reportUrl(String projectKey) {
        return baseUrl + "/dashboard?id=" + projectKey;
    }
}
