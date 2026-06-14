package com.portal.auth;

import com.portal.dao.ApprovalDao;
import com.portal.dao.DeploymentDao;
import com.portal.util.Env;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * M7 — Operations deploy endpoint. Behind /ops/* (RoleFilter → OPERATIONS only).
 * Re-checks the gate server-side: a commit must be APPROVED before it can deploy,
 * even if the UI is bypassed. Optionally triggers a Jenkins deploy job (M7 stretch).
 */
@WebServlet(name = "OpsActionServlet", urlPatterns = {"/ops/deploy"})
public class OpsActionServlet extends HttpServlet {

    private final ApprovalDao approvalDao = new ApprovalDao();
    private final DeploymentDao deploymentDao = new DeploymentDao();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        Integer userId = session == null ? null : (Integer) session.getAttribute("userId");

        int commitId;
        try {
            commitId = Integer.parseInt(req.getParameter("commitId"));
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "commitId required");
            return;
        }

        // Server-side gate enforcement — never trust the queue alone.
        if (!approvalDao.isApproved(commitId)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "commit is not approved");
            return;
        }

        String env = Env.get("DEPLOY_ENVIRONMENT", "production");
        // (Optional) trigger Jenkins remote build job here via JENKINS_* env vars.
        // Kept as a status record for now; wire the httpRequest in M7 stretch / M8.
        deploymentDao.record(commitId, "DEPLOYED", env, userId == null ? 0 : userId);

        resp.sendRedirect(req.getContextPath() + "/ops/dashboard");
    }
}
