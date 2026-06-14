package com.portal.model;

/** Severity counts for one scan (SAST/SCA/DAST). */
public class Severity {
    public int critical, high, medium, low;

    public Severity() {}
    public Severity(int critical, int high, int medium, int low) {
        this.critical = critical; this.high = high; this.medium = medium; this.low = low;
    }
    public int total() { return critical + high + medium + low; }
}
