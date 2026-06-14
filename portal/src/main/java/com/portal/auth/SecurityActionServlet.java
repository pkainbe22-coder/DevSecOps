package com.portal.auth;

import com.portal.dao.ApprovalDao;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * M7 — Security decision endpoint. Behind /security/* so only SECURITY reaches it
 * (RoleFilter). Records APPROVED/REJECTED with the security user's id + comment.
 */
@WebServlet(name = "SecurityActionServlet", urlPatterns = {"/security/decide"})
public class SecurityActionServlet extends HttpServlet {

    private final ApprovalDao approvalDao = new ApprovalDao();

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

        String action = req.getParameter("action");        // "approve" | "reject"
        String comment = req.getParameter("comment");
        String decision = "approve".equalsIgnoreCase(action) ? "APPROVED"
                         : "reject".equalsIgnoreCase(action) ? "REJECTED" : null;
        if (decision == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "action must be approve|reject");
            return;
        }

        approvalDao.decide(commitId, decision, userId == null ? 0 : userId, comment);
        resp.sendRedirect(req.getContextPath() + "/security/dashboard");
    }
}
