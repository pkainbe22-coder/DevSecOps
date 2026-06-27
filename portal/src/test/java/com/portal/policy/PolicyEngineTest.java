package com.portal.policy;

import com.portal.model.PolicyRule;
import com.portal.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the default governance ruleset produces the documented cascade:
 *   critical>0 → AUTO_REJECT, high>3 → ESCALATE, high>0 → MANUAL, else clean → AUTO_APPROVE.
 */
class PolicyEngineTest {

    private final PolicyEngine engine = new PolicyEngine();
    private final List<PolicyRule> defaults = List.of(
            rule("critical", "gt", 0, "AUTO_REJECT", 10),
            rule("high", "gt", 3, "ESCALATE", 20),
            rule("high", "gt", 0, "MANUAL_REVIEW", 30),
            rule("critical", "lte", 0, "AUTO_APPROVE", 40));

    @Test
    void cleanBuildIsAutoApproved() {
        PolicyDecision d = engine.evaluate(new Severity(0, 0, 5, 9), defaults);
        assertEquals("APPROVED", d.decision);
        assertEquals("AUTO_APPROVED", d.source);
        assertEquals(true, d.decidedNow);
    }

    @Test
    void anyCriticalIsAutoRejected() {
        PolicyDecision d = engine.evaluate(new Severity(2, 0, 0, 0), defaults);
        assertEquals("REJECTED", d.decision);
        assertEquals("AUTO_REJECTED", d.source);
        assertEquals("Auto-rejected: 2 critical severity finding(s) require remediation before deployment", d.comment);
    }

    @Test
    void manyHighFindingsAreEscalatedToManualReview() {
        PolicyDecision d = engine.evaluate(new Severity(0, 5, 0, 0), defaults);
        assertEquals("PENDING", d.decision);
        assertEquals("MANUAL", d.source);
        assertEquals("Escalated: 5 high severity findings require security review", d.comment);
    }

    @Test
    void aFewHighFindingsGoToManualReview() {
        PolicyDecision d = engine.evaluate(new Severity(0, 2, 1, 0), defaults);
        assertEquals("PENDING", d.decision);
        assertEquals("MANUAL", d.source);
    }

    @Test
    void criticalTakesPrecedenceOverHigh() {
        // Both critical>0 and high>3 match; lowest priority (critical) wins.
        PolicyDecision d = engine.evaluate(new Severity(1, 9, 0, 0), defaults);
        assertEquals("REJECTED", d.decision);
    }

    @Test
    void noRulesFallsBackToManual() {
        PolicyDecision d = engine.evaluate(new Severity(3, 3, 3, 3), List.of());
        assertEquals("PENDING", d.decision);
        assertEquals("MANUAL", d.source);
    }

    private static PolicyRule rule(String field, String op, int threshold, String action, int priority) {
        PolicyRule r = new PolicyRule();
        r.conditionField = field; r.operator = op; r.thresholdValue = threshold;
        r.action = action; r.priority = priority; r.active = true;
        return r;
    }
}
