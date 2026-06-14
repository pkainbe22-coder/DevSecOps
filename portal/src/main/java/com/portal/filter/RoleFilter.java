package com.portal.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;

/**
 * Server-side access control. Maps URL prefixes to the single role allowed to use them.
 * The role is read from the session (set at login). Wrong role -> 403. No session -> /login.
 *
 *   /developer/* -> DEVELOPER
 *   /security/*  -> SECURITY
 *   /ops/*       -> OPERATIONS
 *
 * Public paths (/login, /logout, /assets, /api/*) are not gated here.
 * (/api/* is the Jenkins receiver — authenticated separately by a shared secret in M6/M8.)
 */
@WebFilter(urlPatterns = {"/developer/*", "/security/*", "/ops/*"})
public class RoleFilter implements Filter {

    private static final Map<String, String> PREFIX_ROLE = Map.of(
            "/developer", "DEVELOPER",
            "/security",  "SECURITY",
            "/ops",       "OPERATIONS");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI().substring(req.getContextPath().length());
        String requiredRole = requiredRoleFor(path);

        HttpSession session = req.getSession(false);
        String role = (session == null) ? null : (String) session.getAttribute("role");

        if (role == null) {
            // Not authenticated.
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        if (requiredRole != null && !requiredRole.equals(role)) {
            // Authenticated but wrong role.
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: this area is for " + requiredRole);
            return;
        }
        chain.doFilter(request, response);
    }

    private String requiredRoleFor(String path) {
        for (Map.Entry<String, String> e : PREFIX_ROLE.entrySet()) {
            if (path.equals(e.getKey()) || path.startsWith(e.getKey() + "/")) {
                return e.getValue();
            }
        }
        return null;
    }
}
