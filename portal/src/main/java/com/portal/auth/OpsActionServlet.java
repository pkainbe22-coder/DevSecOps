package com.portal.auth;

import com.portal.api.JenkinsClient;
import com.portal.dao.ApprovalDao;
import com.portal.dao.CommitDao;
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
    private final CommitDao commitDao = new CommitDao();
    private final JenkinsClient jenkins = new JenkinsClient();

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

        // Trigger the Jenkins deploy job when configured. If Jenkins isn't wired up,
        // record a manual DEPLOYED entry. If the trigger was attempted but rejected,
        // record FAILED so Ops sees it didn't go out.
        String status;
        if (jenkins.isConfigured()) {
            String hash = commitDao.findHashById(commitId);
            status = jenkins.triggerDeploy(hash, env) ? "DEPLOYED" : "FAILED";
        } else {
            status = "DEPLOYED";   // no remote job configured — manual record
        }
        deploymentDao.record(commitId, status, env, userId == null ? 0 : userId);

        resp.sendRedirect(req.getContextPath() + "/ops/dashboard");
    }
}
