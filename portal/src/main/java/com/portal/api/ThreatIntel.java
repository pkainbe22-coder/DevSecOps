package com.portal.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.dao.FindingDao;
import com.portal.model.Finding;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real-world threat intelligence enrichment — what turns "47 findings" into "fix THIS one".
 *
 *   EPSS  (FIRST.org)  — probability a CVE will be exploited in the next 30 days.
 *   CISA KEV           — catalogue of vulnerabilities KNOWN to be exploited in the wild.
 *
 * Both are free, no-key public feeds. Enrichment is best-effort: any network failure
 * leaves findings with their CVSS-only base score (the scan still succeeds). The KEV
 * catalogue (~1300 CVEs) is fetched once and cached in-process.
 */
public class ThreatIntel {

    private static final String EPSS_API = "https://api.first.org/data/v1/epss?cve=";
    private static final String KEV_FEED =
            "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json";

    private final FindingDao findingDao = new FindingDao();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6)).build();

    // Cached KEV catalogue (refreshed every 6h).
    private static volatile Set<String> kevCache = null;
    private static volatile Instant kevFetchedAt = Instant.EPOCH;

    /** Enrich all CVE findings of a commit with EPSS + KEV, then recompute risk scores. */
    public void enrichCommit(int commitId) {
        try {
            List<Finding> findings = findingDao.findByCommit(commitId);
            Set<String> cves = new LinkedHashSet<>();
            Map<String, Double> cvssByCve = new HashMap<>();
            for (Finding f : findings) {
                if (f.hasCve()) {
                    cves.add(f.cveId);
                    cvssByCve.merge(f.cveId, f.cvss, Math::max);
                }
            }
            if (cves.isEmpty()) return;

            Map<String, double[]> epss = fetchEpss(cves);   // cve -> [score, percentile]
            Set<String> kev = kevCatalogue();

            for (String cve : cves) {
                double[] e = epss.get(cve);
                Double score = e == null ? null : e[0];
                Double pct   = e == null ? null : e[1];
                boolean onKev = kev.contains(cve);
                double risk = riskScore(cvssByCve.getOrDefault(cve, 0.0), score, onKev);
                findingDao.enrich(cve, score, pct, onKev, risk);
            }
        } catch (Exception e) {
            System.err.println("[ThreatIntel] enrichCommit best-effort failure: " + e.getMessage());
        }
    }

    /**
     * Contextual risk score (0..100): CVSS severity weighted by real-world exploitability.
     * KEV membership (actively exploited) dominates; otherwise EPSS scales the base score.
     */
    public static double riskScore(double cvss, Double epss, boolean kev) {
        double base = cvss > 0 ? Math.min(100, cvss * 10) : 0;
        double exploit = kev ? 1.0 : (epss != null ? epss : 0.0);
        double risk = base * (0.5 + 0.5 * exploit);
        if (kev) risk = Math.max(risk, 85);     // actively-exploited floor
        return Math.round(Math.min(100, risk) * 10) / 10.0;
    }

    private Map<String, double[]> fetchEpss(Set<String> cves) {
        Map<String, double[]> out = new HashMap<>();
        try {
            String url = EPSS_API + String.join(",", cves);
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return out;
            JsonArray data = JsonParser.parseString(resp.body()).getAsJsonObject().getAsJsonArray("data");
            for (var d : data) {
                JsonObject o = d.getAsJsonObject();
                out.put(o.get("cve").getAsString(), new double[]{
                        parseD(o, "epss"), parseD(o, "percentile") });
            }
        } catch (Exception e) {
            System.err.println("[ThreatIntel] EPSS lookup failed: " + e.getMessage());
        }
        return out;
    }

    private Set<String> kevCatalogue() {
        Set<String> cached = kevCache;
        if (cached != null && Duration.between(kevFetchedAt, Instant.now()).toHours() < 6) return cached;
        try {
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(KEV_FEED)).timeout(Duration.ofSeconds(12)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) {
                Set<String> set = new HashSet<>();
                JsonArray vulns = JsonParser.parseString(resp.body())
                        .getAsJsonObject().getAsJsonArray("vulnerabilities");
                for (var v : vulns) set.add(v.getAsJsonObject().get("cveID").getAsString());
                kevCache = set;
                kevFetchedAt = Instant.now();
                System.out.println("[ThreatIntel] KEV catalogue loaded: " + set.size() + " CVEs");
                return set;
            }
        } catch (Exception e) {
            System.err.println("[ThreatIntel] KEV feed failed: " + e.getMessage());
        }
        return cached != null ? cached : Set.of();   // fall back to stale cache or empty
    }

    private static double parseD(JsonObject o, String k) {
        try { return Double.parseDouble(o.get(k).getAsString()); } catch (Exception e) { return 0; }
    }
}
