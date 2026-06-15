package com.portal.auth;

import com.portal.dao.CommitDao;
import com.portal.model.Commit;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Role dashboards (M3 shell + M7 data + M8 pagination). Each loads only the data its
 * role may see, one page at a time. Paging trick: fetch PAGE_SIZE+1 rows to know if a
 * next page exists — no COUNT query needed.
 */
public class DashboardServlets {

    private static final CommitDao COMMITS = new CommitDao();
    static final int PAGE_SIZE = 20;

    /** Reads ?page=, runs the supplied fetch, and sets common paging attributes. */
    private static List<Commit> page(HttpServletRequest req, Fetch fetch) {
        int p = 1;
        try { p = Math.max(1, Integer.parseInt(req.getParameter("page"))); } catch (Exception ignored) {}
        int offset = (p - 1) * PAGE_SIZE;
        List<Commit> rows = fetch.get(PAGE_SIZE + 1, offset);   // +1 sentinel
        boolean hasNext = rows.size() > PAGE_SIZE;
        if (hasNext) rows = rows.subList(0, PAGE_SIZE);
        req.setAttribute("page", p);
        req.setAttribute("hasPrev", p > 1);
        req.setAttribute("hasNext", hasNext);
        return rows;
    }

    @FunctionalInterface
    private interface Fetch { List<Commit> get(int limit, int offset); }

    @WebServlet(name = "DeveloperDashboard", urlPatterns = {"/developer/dashboard"})
    public static class Developer extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            String username = (String) req.getSession().getAttribute("username");
            req.setAttribute("commits", page(req, (lim, off) -> COMMITS.findByAuthor(username, lim, off)));
            req.getRequestDispatcher("/WEB-INF/views/developer.jsp").forward(req, resp);
        }
    }

    @WebServlet(name = "SecurityDashboard", urlPatterns = {"/security/dashboard"})
    public static class Security extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            String decision = normalize(req.getParameter("decision"));   // null = all
            req.setAttribute("decision", decision == null ? "" : decision);
            req.setAttribute("commits", page(req, (lim, off) -> COMMITS.findAllForSecurity(decision, lim, off)));
            req.getRequestDispatcher("/WEB-INF/views/security.jsp").forward(req, resp);
        }

        private String normalize(String d) {
            if (d == null) return null;
            d = d.trim().toUpperCase();
            return switch (d) {
                case "PENDING", "APPROVED", "REJECTED" -> d;
                default -> null;
            };
        }
    }

    @WebServlet(name = "OpsDashboard", urlPatterns = {"/ops/dashboard"})
    public static class Ops extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            req.setAttribute("commits", page(req, COMMITS::findApprovedForOps));
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
