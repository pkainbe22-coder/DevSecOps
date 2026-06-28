package com.portal.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.model.Finding;
import com.portal.util.Env;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * AI Security Analyst — calls the Claude Messages API to turn a raw finding into a
 * plain-English risk explanation + concrete remediation, and to write an executive
 * posture summary. Consistent with the other API clients here: java.net.http + Gson,
 * no SDK dependency.
 *
 * Wire format: POST https://api.anthropic.com/v1/messages
 *   headers: x-api-key, anthropic-version: 2023-06-01, content-type: application/json
 *   body:    { model, max_tokens, system, messages:[{role:"user", content}] }
 *
 * Gated on ANTHROPIC_API_KEY — absent ⇒ isConfigured() is false and the UI hides the
 * AI actions. Model defaults to claude-opus-4-8 (override with CLAUDE_MODEL).
 */
public class AnthropicClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey = Env.get("ANTHROPIC_API_KEY", Env.get("CLAUDE_API_KEY", ""));
    private final String model  = Env.get("CLAUDE_MODEL", "claude-opus-4-8");
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    /** Explanation + suggested fix for one finding. */
    public AiResult analyzeFinding(Finding f) {
        String system = """
            You are a senior application security engineer helping a developer triage a
            vulnerability found by an automated scanner. Be precise, practical, and concise.
            Respond with ONLY a JSON object, no markdown fences, of the exact form:
            {"summary":"2-3 sentence plain-English explanation of the risk and why it matters",
             "fix":"concrete remediation: exact dependency version bump and/or code change"}
            """;
        String user = "Vulnerability finding:\n"
                + "- ID: " + nz(f.cveId) + "\n"
                + "- Package/location: " + nz(f.pkg) + "\n"
                + "- Title: " + nz(f.title) + "\n"
                + "- Severity: " + nz(f.severity) + " (CVSS " + f.cvss + ")\n"
                + "- EPSS exploit probability: " + (f.epss == null ? "unknown" : Math.round(f.epss * 100) + "%") + "\n"
                + "- On CISA Known Exploited Vulnerabilities list: " + (f.kev ? "YES (actively exploited)" : "no") + "\n"
                + "- Scan type: " + nz(f.scanType) + "\n\n"
                + "Explain the risk and give the fix.";

        String text = call(system, user, 700);
        JsonObject j = tryParseJson(text);
        AiResult r = new AiResult();
        if (j != null) {
            r.summary = strOr(j, "summary", text);
            r.fix = strOr(j, "fix", "");
        } else {
            r.summary = text;   // model didn't return JSON — surface the prose
            r.fix = "";
        }
        return r;
    }

    /** Executive posture summary across the top findings. */
    public String executiveSummary(List<Finding> top, int posture, int kev, int exploitable) {
        StringBuilder sb = new StringBuilder();
        sb.append("Security posture score: ").append(posture).append("/100. ")
          .append(kev).append(" actively-exploited (KEV) findings, ")
          .append(exploitable).append(" with high exploit probability.\n")
          .append("Top findings by contextual risk:\n");
        int n = 0;
        for (Finding f : top) {
            if (n++ >= 8) break;
            sb.append("- ").append(nz(f.cveId)).append(" in ").append(nz(f.pkg))
              .append(" — ").append(nz(f.severity)).append(", risk ").append(Math.round(f.riskScore))
              .append(f.kev ? ", KEV" : "").append("\n");
        }
        String system = """
            You are a security lead briefing an executive. In 3-4 sentences, summarise the
            application's security posture, name the single most urgent issue, and state the
            recommended action. Plain prose, no markdown, no preamble.
            """;
        return call(system, sb.toString(), 400);
    }

    // --- HTTP ---

    private String call(String system, String user, int maxTokens) {
        if (!isConfigured()) throw new IllegalStateException("AI analyst not configured (set ANTHROPIC_API_KEY)");
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", maxTokens);
            body.addProperty("system", system);
            JsonArray messages = new JsonArray();
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", user);
            messages.add(msg);
            body.add("messages", messages);

            HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401) throw new RuntimeException("Claude API rejected the key (401)");
            if (resp.statusCode() / 100 != 2)
                throw new RuntimeException("Claude API HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 200));

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            StringBuilder out = new StringBuilder();
            for (var block : content) {
                JsonObject b = block.getAsJsonObject();
                if ("text".equals(strOr(b, "type", "")) && b.has("text")) out.append(b.get("text").getAsString());
            }
            return out.toString().trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    private static JsonObject tryParseJson(String text) {
        if (text == null) return null;
        String t = text.trim();
        // strip ```json ... ``` fences if the model added them
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
            t = t.trim();
        }
        if (!t.startsWith("{")) return null;
        try { return JsonParser.parseString(t).getAsJsonObject(); }
        catch (Exception e) { return null; }
    }

    private static String strOr(JsonObject o, String k, String def) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : def;
    }
    private static String nz(String s) { return s == null ? "—" : s; }
    private static String truncate(String s, int n) { return s == null ? "" : s.length() <= n ? s : s.substring(0, n); }

    /** Result of analysing one finding. */
    public static final class AiResult {
        public String summary = "";
        public String fix = "";
    }
}
