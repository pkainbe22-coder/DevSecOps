package com.portal.auth;

import com.portal.api.AiAnalyst;
import com.portal.dao.FindingDao;
import com.portal.model.Finding;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Risk Intelligence dashboard (SECURITY, behind /security/*). The org-wide "fix these
 * first" view: every finding ranked by contextual risk (CVSS × real-world exploitability
 * from EPSS + CISA KEV), plus a single security-posture score and threat KPIs.
 */
@WebServlet(name = "RiskServlet", urlPatterns = {"/security/risk"})
public class RiskServlet extends HttpServlet {

    private final FindingDao findingDao = new FindingDao();
    private final AiAnalyst claude = new AiAnalyst();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<Finding> top = findingDao.findTopByRisk(50);
        int[] counts = findingDao.postureCounts();   // [total, kev, exploitable]
        int total = counts[0], kev = counts[1], exploitable = counts[2];

        double topRisk = top.isEmpty() ? 0 : top.get(0).riskScore;
        // Posture: starts at 100, penalised by the worst finding + actively-exploited /
        // high-EPSS counts. KEV-listed vulns hurt the most.
        int posture = (int) Math.max(0, Math.round(100 - Math.min(100,
                topRisk * 0.5 + kev * 10 + exploitable * 5)));

        req.setAttribute("findings", top);
        req.setAttribute("total", total);
        req.setAttribute("kev", kev);
        req.setAttribute("exploitable", exploitable);
        req.setAttribute("posture", posture);
        req.setAttribute("postureBand", posture >= 75 ? "ok" : posture >= 45 ? "warn" : "crit");

        // AI Security Analyst state
        req.setAttribute("aiConfigured", claude.isConfigured());
        req.setAttribute("aiProvider", claude.providerName());
        req.setAttribute("aiStatus", req.getParameter("ai"));   // ok | error | unconfigured | null
        var session = req.getSession(false);
        if (session != null) req.setAttribute("aiExecSummary", session.getAttribute("aiExecSummary"));

        req.getRequestDispatcher("/WEB-INF/views/risk.jsp").forward(req, resp);
    }
}
