package com.portal.auth;

import com.portal.dao.CommitDao;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Role dashboards (M3 shell + M7 data). Each loads only the data its role may see
 * and forwards to its JSP. The RoleFilter has already guaranteed the right role.
 */
public class DashboardServlets {

    private static final CommitDao COMMITS = new CommitDao();

    @WebServlet(name = "DeveloperDashboard", urlPatterns = {"/developer/dashboard"})
    public static class Developer extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            String username = (String) req.getSession().getAttribute("username");
            req.setAttribute("commits", COMMITS.findByAuthor(username));   // own commits only
            req.getRequestDispatcher("/WEB-INF/views/developer.jsp").forward(req, resp);
        }
    }

    @WebServlet(name = "SecurityDashboard", urlPatterns = {"/security/dashboard"})
    public static class Security extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            req.setAttribute("commits", COMMITS.findAllForSecurity());     // all + findings
            req.getRequestDispatcher("/WEB-INF/views/security.jsp").forward(req, resp);
        }
    }

    @WebServlet(name = "OpsDashboard", urlPatterns = {"/ops/dashboard"})
    public static class Ops extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            req.setAttribute("commits", COMMITS.findApprovedForOps());     // approved only
            req.getRequestDispatcher("/WEB-INF/views/ops.jsp").forward(req, resp);
        }
    }

    /** Root "/" -> login (or the user's dashboard if already authenticated). */
    @WebServlet(name = "RootServlet", urlPatterns = {"/"})
    public static class Root extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            var session = req.getSession(false);
            Object role = session == null ? null : session.getAttribute("role");
            if (role != null) {
                resp.sendRedirect(req.getContextPath() + LoginServlet.homeFor((String) role));
            } else {
                resp.sendRedirect(req.getContextPath() + "/login");
            }
        }
    }
}
