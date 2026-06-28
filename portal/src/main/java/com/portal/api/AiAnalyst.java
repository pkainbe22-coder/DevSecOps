package com.portal.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.model.Finding;

import java.util.List;

/**
 * AI Security Analyst — provider-agnostic. Owns the security prompts and response
 * parsing; delegates the actual completion to whichever LLM provider has a key
 * (Claude preferred, then Groq). Absent any key, isConfigured() is false and the UI
 * hides the AI actions.
 */
public class AiAnalyst {

    private final LlmProvider provider;

    public AiAnalyst() {
        LlmProvider claude = new AnthropicClient();
        LlmProvider groq = new GroqClient();
        this.provider = claude.isConfigured() ? claude : groq.isConfigured() ? groq : null;
    }

    public boolean isConfigured() { return provider != null; }
    public String providerName() { return provider == null ? "none" : provider.name(); }

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

        String text = require().complete(system, user, 700);
        JsonObject j = tryParseJson(text);
        AiResult r = new AiResult();
        if (j != null) {
            r.summary = strOr(j, "summary", text);
            r.fix = strOr(j, "fix", "");
        } else {
            r.summary = text;   // provider didn't return JSON — surface the prose
        }
        return r;
    }

    /** Executive posture summary across the top findings. */
    public String executiveSummary(List<Finding> top, int posture, int kev, int exploitable) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("Security posture score: ").append(posture).append("/100. ")
           .append(kev).append(" actively-exploited (KEV) findings, ")
           .append(exploitable).append(" with high exploit probability.\n")
           .append("Top findings by contextual risk:\n");
        int n = 0;
        for (Finding f : top) {
            if (n++ >= 8) break;
            ctx.append("- ").append(nz(f.cveId)).append(" in ").append(nz(f.pkg))
               .append(" — ").append(nz(f.severity)).append(", risk ").append(Math.round(f.riskScore))
               .append(f.kev ? ", KEV" : "").append("\n");
        }
        String system = """
            You are a security lead briefing an executive. In 3-4 sentences, summarise the
            application's security posture, name the single most urgent issue, and state the
            recommended action. Plain prose, no markdown, no preamble.
            """;
        return require().complete(system, ctx.toString(), 400);
    }

    private LlmProvider require() {
        if (provider == null) throw new IllegalStateException("AI analyst not configured");
        return provider;
    }

    private static JsonObject tryParseJson(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("```")) {              // strip ```json ... ``` fences
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
}
