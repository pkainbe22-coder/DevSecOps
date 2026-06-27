package com.portal.policy;

import com.portal.dao.PolicyRuleDao;
import com.portal.model.PolicyRule;
import com.portal.model.Severity;

import java.util.List;

/**
 * Policy-as-Code gate. Evaluates a commit's aggregate severity counts against the
 * active rules (lowest priority number first); the FIRST matching rule decides the
 * gate. If no rule matches, the commit falls through to normal manual review.
 *
 * Rules are loaded fresh from policy_rules on every evaluation, so threshold changes
 * made by Security on /security/policy take effect for the next commit immediately.
 *
 * The seeded defaults reproduce the standard governance policy:
 *   critical > 0   → AUTO_REJECT   (block any critical)
 *   high     > 3   → ESCALATE      (too many highs → security review)
 *   high     > 0   → MANUAL_REVIEW (a few highs → manual review)
 *   critical <= 0  → AUTO_APPROVE  (reached only when clean: 0 critical, 0 high)
 */
public final class PolicyEngine {

    private final PolicyRuleDao ruleDao;

    public PolicyEngine() { this(new PolicyRuleDao()); }
    public PolicyEngine(PolicyRuleDao ruleDao) { this.ruleDao = ruleDao; }

    public PolicyDecision evaluate(Severity sev) {
        return evaluate(sev, ruleDao.findActiveOrdered());
    }

    /** Pure evaluation against a supplied ruleset — unit-testable, no DB. */
    public PolicyDecision evaluate(Severity sev, List<PolicyRule> rules) {
        for (PolicyRule r : rules) {
            if (matches(sev.get(r.conditionField), r.operator, r.thresholdValue)) {
                return decisionFor(r.action, sev);
            }
        }
        return PolicyDecision.manual();   // nothing matched → manual review
    }

    private static boolean matches(int value, String op, int threshold) {
        return switch (op == null ? "" : op) {
            case "eq"  -> value == threshold;
            case "gt"  -> value >  threshold;
            case "lt"  -> value <  threshold;
            case "gte" -> value >= threshold;
            case "lte" -> value <= threshold;
            default    -> false;
        };
    }

    private static PolicyDecision decisionFor(String action, Severity sev) {
        return switch (action == null ? "" : action) {
            case "AUTO_APPROVE" -> PolicyDecision.autoApprove();
            case "AUTO_REJECT"  -> PolicyDecision.autoReject(sev.critical);
            case "ESCALATE"     -> PolicyDecision.escalate(sev.high);
            default             -> PolicyDecision.manual();   // MANUAL_REVIEW / unknown
        };
    }
}
