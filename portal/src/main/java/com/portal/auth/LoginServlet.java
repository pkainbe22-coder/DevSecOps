package com.portal.auth;

import com.portal.dao.UserDao;
import com.portal.model.User;
import com.portal.util.Passwords;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Verifies username/password against the BCrypt hash, then stores the user's
 * id/username/role in the session. The RoleFilter reads that session attribute.
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {

    private final UserDao userDao = new UserDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // Already logged in? Send to their dashboard.
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("role") != null) {
            resp.sendRedirect(req.getContextPath() + homeFor((String) session.getAttribute("role")));
            return;
        }
        req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        User user = (username == null) ? null : userDao.findByUsername(username.trim());
        if (user == null || !Passwords.verify(password == null ? "" : password, user.getPasswordHash())) {
            req.setAttribute("error", "Invalid username or password.");
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, resp);
            return;
        }

        // Prevent session fixation: new session on successful auth.
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = req.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("role", user.getRole());

        resp.sendRedirect(req.getContextPath() + homeFor(user.getRole()));
    }

    static String homeFor(String role) {
        return switch (role) {
            case "DEVELOPER"  -> "/developer/dashboard";
            case "SECURITY"   -> "/security/dashboard";
            case "OPERATIONS" -> "/ops/dashboard";
            default           -> "/login";
        };
    }
}
