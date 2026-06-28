package com.portal.auth;

import com.portal.api.AiAnalyst;
import com.portal.api.AiResult;
import com.portal.dao.FindingDao;
import com.portal.model.Finding;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * AI Security Analyst actions (SECURITY, behind /security/*).
 *
 *   POST /security/ai/explain  (findingId) → Claude explains the finding + suggests a fix,
 *                                            stored on the finding, then back to the dashboard.
 *   POST /security/ai/summary             → Claude writes an executive posture summary,
 *                                            stashed in the session for the dashboard.
 *
 * Gated on ANTHROPIC_API_KEY — when unset, returns a friendly notice instead of erroring.
 */
@WebServlet(name = "AiServlet", urlPatterns = {"/security/ai/explain", "/security/ai/summary"})
public class AiServlet extends HttpServlet {

    private final FindingDao findingDao = new FindingDao();
    private final AiAnalyst claude = new AiAnalyst();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!claude.isConfigured()) {
            redirectWith(req, resp, "ai=unconfigured");
            return;
        }
        if (req.getServletPath().endsWith("/summary")) {
            doSummary(req, resp);
            return;
        }
        int findingId;
        try {
            findingId = Integer.parseInt(req.getParameter("findingId"));
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "findingId required");
            return;
        }
        Finding f = findingDao.findById(findingId);
        if (f == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "finding not found");
            return;
        }
        try {
            AiResult r = claude.analyzeFinding(f);
            findingDao.saveAi(findingId, r.summary, r.fix);
            redirectWith(req, resp, "ai=ok#f" + findingId);
        } catch (Exception e) {
            System.err.println("[AiServlet] " + e.getMessage());
            redirectWith(req, resp, "ai=error");
        }
    }

    private void doSummary(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            List<Finding> top = findingDao.findTopByRisk(50);
            int[] c = findingDao.postureCounts();
            double topRisk = top.isEmpty() ? 0 : top.get(0).riskScore;
            int posture = (int) Math.max(0, Math.round(100 - Math.min(100, topRisk * 0.5 + c[1] * 10 + c[2] * 5)));
            String summary = claude.executiveSummary(top, posture, c[1], c[2]);
            req.getSession().setAttribute("aiExecSummary", summary);
            redirectWith(req, resp, "ai=summary");
        } catch (Exception e) {
            System.err.println("[AiServlet] summary " + e.getMessage());
            redirectWith(req, resp, "ai=error");
        }
    }

    private void redirectWith(HttpServletRequest req, HttpServletResponse resp, String q) throws IOException {
        resp.sendRedirect(req.getContextPath() + "/security/risk?" + q);
    }
}
