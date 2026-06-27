package com.portal.policy;

/**
 * The outcome of evaluating the policy ruleset against a commit's severity totals:
 * the gate decision (PENDING/APPROVED/REJECTED), how it was reached (decision_source),
 * an explanatory comment, and whether decided_at should be stamped now.
 *
 * AUTO_* decisions are final (decided now, no human needed). MANUAL means the commit
 * lands in the manual review queue (PENDING) — possibly flagged as "escalated".
 */
public final class PolicyDecision {
    public final String decision;        // PENDING | APPROVED | REJECTED
    public final String source;          // AUTO_APPROVED | AUTO_REJECTED | MANUAL
    public final String comment;
    public final boolean decidedNow;     // stamp decided_at = NOW() ?

    private PolicyDecision(String decision, String source, String comment, boolean decidedNow) {
        this.decision = decision;
        this.source = source;
        this.comment = comment;
        this.decidedNow = decidedNow;
    }

    public static PolicyDecision autoApprove() {
        return new PolicyDecision("APPROVED", "AUTO_APPROVED",
                "Auto-approved: no critical or high severity findings", true);
    }

    public static PolicyDecision autoReject(int critical) {
        return new PolicyDecision("REJECTED", "AUTO_REJECTED",
                "Auto-rejected: " + critical + " critical severity finding(s) require remediation before deployment",
                true);
    }

    public static PolicyDecision escalate(int high) {
        return new PolicyDecision("PENDING", "MANUAL",
                "Escalated: " + high + " high severity findings require security review", false);
    }

    /** Normal manual-review flow (no rule matched, or an explicit MANUAL_REVIEW rule). */
    public static PolicyDecision manual() {
        return new PolicyDecision("PENDING", "MANUAL", null, false);
    }
}
