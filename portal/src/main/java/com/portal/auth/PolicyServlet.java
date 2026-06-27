package com.portal.auth;

import com.portal.dao.PolicyRuleDao;
import com.portal.model.PolicyRule;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Policy-as-Code config page. Behind /security/* so only SECURITY reaches it (RoleFilter).
 *
 *   GET  /security/policy  → render the editable ruleset.
 *   POST /security/policy  → save edits (one set of fields per existing rule id),
 *                            optionally add a new rule, or toggle/delete a rule.
 *
 * Changes take effect immediately: the PolicyEngine reloads active rules on every
 * commit evaluation, so new commits are gated against the updated thresholds.
 */
@WebServlet(name = "PolicyServlet", urlPatterns = {"/security/policy"})
public class PolicyServlet extends HttpServlet {

    private final PolicyRuleDao ruleDao = new PolicyRuleDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setAttribute("rules", ruleDao.findAllOrdered());
        req.setAttribute("saved", req.getParameter("saved") != null);
        req.getRequestDispatcher("/WEB-INF/views/policy.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String op = req.getParameter("op");

        if ("delete".equals(op)) {
            ruleDao.delete(intParam(req, "id", -1));
        } else if ("add".equals(op)) {
            PolicyRule r = new PolicyRule();
            r.ruleName       = trimOr(req.getParameter("rule_name"), "New rule");
            r.conditionField = oneOf(req.getParameter("condition_field"), FIELDS, "critical");
            r.operator       = oneOf(req.getParameter("operator"), OPERATORS, "gt");
            r.thresholdValue = Math.max(0, intParam(req, "threshold_value", 0));
            r.action         = oneOf(req.getParameter("action"), ACTIONS, "MANUAL_REVIEW");
            r.priority       = intParam(req, "priority", 100);
            r.active         = true;
            ruleDao.insert(r);
        } else {
            // Save: update every rule whose fields were submitted (id=<n> rows).
            List<PolicyRule> existing = ruleDao.findAllOrdered();
            for (PolicyRule r : existing) {
                String p = "r" + r.id + "_";
                if (req.getParameter(p + "threshold_value") == null) continue;  // not on this form
                r.ruleName       = trimOr(req.getParameter(p + "rule_name"), r.ruleName);
                r.conditionField = oneOf(req.getParameter(p + "condition_field"), FIELDS, r.conditionField);
                r.operator       = oneOf(req.getParameter(p + "operator"), OPERATORS, r.operator);
                r.thresholdValue = Math.max(0, intParam(req, p + "threshold_value", r.thresholdValue));
                r.action         = oneOf(req.getParameter(p + "action"), ACTIONS, r.action);
                r.priority       = intParam(req, p + "priority", r.priority);
                r.active         = req.getParameter(p + "active") != null;   // checkbox
                ruleDao.update(r);
            }
        }
        resp.sendRedirect(req.getContextPath() + "/security/policy?saved=1");
    }

    private static final List<String> FIELDS    = List.of("critical", "high", "medium", "low");
    private static final List<String> OPERATORS = List.of("eq", "gt", "lt", "gte", "lte");
    private static final List<String> ACTIONS   =
            List.of("AUTO_APPROVE", "AUTO_REJECT", "ESCALATE", "MANUAL_REVIEW");

    private static int intParam(HttpServletRequest req, String name, int def) {
        try { return Integer.parseInt(req.getParameter(name).trim()); }
        catch (Exception e) { return def; }
    }
    private static String trimOr(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }
    /** Whitelist guard so the stored value is always a known enum member. */
    private static String oneOf(String v, List<String> allowed, String def) {
        return (v != null && allowed.contains(v)) ? v : def;
    }
}
