package com.portal.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.model.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FindingExtractorTest {

    @Test
    void extractsIndividualCvesFromDependencyCheck() {
        String json = """
            { "dependencies": [
              { "fileName": "log4j-core-2.14.1.jar", "vulnerabilities": [
                  { "name": "CVE-2021-44228", "severity": "CRITICAL", "cvssv3": { "baseScore": 10.0 } },
                  { "name": "CVE-2021-45046", "severity": "CRITICAL", "cvssv3": { "baseScore": 9.0 } } ] },
              { "fileName": "clean.jar" }
            ] }
            """;
        List<Finding> fs = FindingExtractor.fromDependencyCheck(parse(json));
        assertEquals(2, fs.size());
        Finding f = fs.get(0);
        assertEquals("CVE-2021-44228", f.cveId);
        assertEquals("log4j-core-2.14.1.jar", f.pkg);
        assertEquals(10.0, f.cvss);
        assertEquals("SCA", f.scanType);
        assertTrue(f.hasCve());
        assertEquals(100, Math.round(f.riskScore));   // CVSS-only base before enrichment
    }

    @Test
    void extractsAlertsFromZap() {
        String json = """
            { "site": [ { "@name": "http://staging:8080", "alerts": [
                { "pluginid": "10202", "alert": "Absence of Anti-CSRF Tokens", "riskcode": "2" } ] } ] }
            """;
        List<Finding> fs = FindingExtractor.fromZap(parse(json));
        assertEquals(1, fs.size());
        assertEquals("DAST", fs.get(0).scanType);
        assertEquals("ZAP-10202", fs.get(0).cveId);
        assertEquals("MEDIUM", fs.get(0).severity);
        assertFalse(fs.get(0).hasCve());
    }

    @Test
    void riskScorePrioritisesKevAndEpss() {
        // KEV-listed critical → maxed out.
        assertEquals(100.0, ThreatIntel.riskScore(10.0, 0.97, true));
        // Same CVSS, high EPSS, not KEV → high but below the KEV case.
        double exploitable = ThreatIntel.riskScore(7.5, 0.90, false);
        // Same CVSS, negligible EPSS → markedly lower.
        double theoretical = ThreatIntel.riskScore(7.5, 0.01, false);
        assertTrue(exploitable > theoretical + 20, exploitable + " vs " + theoretical);
        // No CVSS, no intel → zero.
        assertEquals(0.0, ThreatIntel.riskScore(0, null, false));
    }

    private JsonObject parse(String s) { return JsonParser.parseString(s).getAsJsonObject(); }
}
