package com.portal.model;

/**
 * One row of the Policy-as-Code ruleset (policy_rules table). Each rule tests a single
 * severity field against a threshold and, on first match (lowest priority first), drives
 * the automatic gate decision. Editable by the SECURITY role at /security/policy.
 *
 *   condition_field ∈ critical | high | medium | low
 *   operator        ∈ eq | gt | lt | gte | lte
 *   action          ∈ AUTO_APPROVE | AUTO_REJECT | ESCALATE | MANUAL_REVIEW
 */
public class PolicyRule {
    public int id;
    public String ruleName;
    public String conditionField;
    public String operator;
    public int thresholdValue;
    public String action;
    public int priority;
    public boolean active;

    // Getters — JSTL EL needs JavaBean accessors, not public fields.
    public int getId() { return id; }
    public String getRuleName() { return ruleName; }
    public String getConditionField() { return conditionField; }
    public String getOperator() { return operator; }
    public int getThresholdValue() { return thresholdValue; }
    public String getAction() { return action; }
    public int getPriority() { return priority; }
    public boolean isActive() { return active; }

    /** Human-readable operator for the config table, e.g. "&gt;". */
    public String operatorSymbol() {
        return switch (operator == null ? "" : operator) {
            case "eq"  -> "=";
            case "gt"  -> ">";
            case "lt"  -> "<";
            case "gte" -> ">=";
            case "lte" -> "<=";
            default    -> operator;
        };
    }
}
