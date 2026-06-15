package com.portal.api;

import com.google.gson.JsonObject;
import com.portal.model.Severity;

/**
 * Parses an OWASP ZAP JSON report (zap-baseline.py -J) into severity counts.
 *
 * Report shape (abridged):
 *   { "site": [ { "alerts": [ { "riskcode": "3", "count": "2" }, ... ] }, ... ] }
 *
 * ZAP riskcode: 3=High, 2=Medium, 1=Low, 0=Informational. ZAP has no "Critical",
 * so `critical` stays 0. Informational is rolled into `low`. Each alert's "count"
 * is the number of instances found.
 */
public final class ZapParser {
    private ZapParser() {}

    public static Severity parse(JsonObject report) {
        Severity sev = new Severity();
        if (report == null || !report.has("site")) return sev;
        for (var s : report.getAsJsonArray("site")) {
            JsonObject site = s.getAsJsonObject();
            if (!site.has("alerts") || site.get("alerts").isJsonNull()) continue;
            for (var a : site.getAsJsonArray("alerts")) {
                JsonObject alert = a.getAsJsonObject();
                int risk = asInt(alert, "riskcode");
                int count = Math.max(1, asInt(alert, "count"));   // default 1 instance
                switch (risk) {
                    case 3 -> sev.high += count;
                    case 2 -> sev.medium += count;
                    case 1, 0 -> sev.low += count;  // Low + Informational
                    default -> { }
                }
            }
        }
        return sev;
    }

    private static int asInt(JsonObject o, String key) {
        try {
            return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsInt() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
