package com.portal.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.dao.ApprovalDao;
import com.portal.dao.CommitDao;
import com.portal.dao.ScanResultDao;
import com.portal.model.Commit;
import com.portal.model.Severity;
import com.portal.policy.PolicyDecision;
import com.portal.policy.PolicyEngine;
import com.portal.util.Env;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * M6 — the core integration. Jenkins POSTs a build summary here; this servlet:
 *   1. Upserts the commit (idempotent on hash; enriched via Gitea API).
 *   2. Pulls vulnerability counts via the SonarQube Web API.
 *   3. Upserts scan_results (SAST today; SCA/DAST when those parsers are wired).
 *   4. Ensures a PENDING approval row exists.
 *
 * Expected JSON: { commitHash, author, branch, repo, buildNumber, sonarProjectKey }
 * Auth: shared secret in the X-Portal-Token header (compared to PORTAL_API_TOKEN env).
 */
@WebServlet(name = "ScanResultsServlet", urlPatterns = {"/api/scan-results"})
public class ScanResultsServlet extends HttpServlet {

    private final CommitDao commitDao = new CommitDao();
    private final ScanResultDao scanDao = new ScanResultDao();
    private final ApprovalDao approvalDao = new ApprovalDao();
    private final PolicyEngine policy = new PolicyEngine();
    private final SonarClient sonar = new SonarClient();
    private final GiteaClient gitea = new GiteaClient();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // --- auth: shared secret (skipped only if PORTAL_API_TOKEN is unset, e.g. local dev) ---
        String expected = Env.get("PORTAL_API_TOKEN", "");
        if (!expected.isBlank() && !expected.equals(req.getHeader("X-Portal-Token"))) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "bad token");
            return;
        }

        JsonObject body;
        try {
            String raw = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            body = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid JSON");
            return;
        }

        String commitHash = str(body, "commitHash");
        if (commitHash == null || commitHash.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "commitHash required");
            return;
        }

        try {
            // 1. commit (idempotent) + Gitea enrichment
            Commit c = new Commit();
            c.commitHash = commitHash;
            c.author = str(body, "author");
            c.branch = str(body, "branch");
            c.repo = str(body, "repo");
            gitea.enrich(c);                       // best-effort
            int commitId = commitDao.upsert(c);

            // 2 + 3. SAST counts from SonarQube -> scan_results (idempotent per type)
            String projectKey = str(body, "sonarProjectKey");
            if (projectKey != null && !projectKey.isBlank()) {
                Severity sast = sonar.vulnerabilityCounts(projectKey);
                scanDao.upsert(commitId, "SAST", "SonarQube", sast, sonar.reportUrl(projectKey));
            }
            // SCA + DAST: Jenkins may embed the raw report JSON under "reports".
            // Parsed here (in Java, testable) and stored as their own scan_results rows.
            if (body.has("reports") && body.get("reports").isJsonObject()) {
                JsonObject reports = body.getAsJsonObject("reports");
                if (reports.has("sca") && reports.get("sca").isJsonObject()) {
                    Severity sca = DependencyCheckParser.parse(reports.getAsJsonObject("sca"));
                    scanDao.upsert(commitId, "SCA", "DependencyCheck", sca, null);
                }
                if (reports.has("dast") && reports.get("dast").isJsonObject()) {
                    Severity dast = ZapParser.parse(reports.getAsJsonObject("dast"));
                    scanDao.upsert(commitId, "DAST", "ZAP", dast, null);
                }
            }

            // 4. Policy-as-Code gate: evaluate the commit's aggregate severities against
            //    the active ruleset and record the resulting decision (auto-approve /
            //    auto-reject / escalate / manual). Idempotent — never overturns an
            //    existing decision on re-push.
            Severity totals = scanDao.totalsForCommit(commitId);
            PolicyDecision decision = policy.evaluate(totals);
            approvalDao.ensureDecision(commitId, decision);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"status\":\"ok\",\"commitId\":" + commitId
                    + ",\"decision\":\"" + decision.decision + "\""
                    + ",\"source\":\"" + decision.source + "\"}");
        } catch (Exception e) {
            System.err.println("[ScanResultsServlet] " + e.getMessage());
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "processing failed");
        }
    }

    private static String str(JsonObject o, String k) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
