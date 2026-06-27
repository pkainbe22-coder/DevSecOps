package com.portal.model;

/** Severity counts for one scan (SAST/SCA/DAST). */
public class Severity {
    public int critical, high, medium, low;

    public Severity() {}
    public Severity(int critical, int high, int medium, int low) {
        this.critical = critical; this.high = high; this.medium = medium; this.low = low;
    }
    public int total() { return critical + high + medium + low; }

    /** Look up a count by policy condition_field name (critical/high/medium/low). */
    public int get(String field) {
        return switch (field == null ? "" : field.toLowerCase()) {
            case "critical" -> critical;
            case "high"     -> high;
            case "medium"   -> medium;
            case "low"      -> low;
            default         -> 0;
        };
    }
}
