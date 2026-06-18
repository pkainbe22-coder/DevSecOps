package com.portal.model;

import java.time.LocalDateTime;

/** A commit reported by the pipeline, optionally enriched via the Gitea API. */
public class Commit {
    public int id;
    public String commitHash;
    public String author;
    public String message;
    public String branch;
    public String repo;
    public String giteaUrl;
    public LocalDateTime committedAt;

    // Joined/derived fields used by the dashboards:
    public int critical, high, medium, low;     // summed across scan_results
    public String decision;                      // PENDING | APPROVED | REJECTED
    public String approvalComment;
    public String deployStatus;                  // NOT_DEPLOYED | DEPLOYED | ...

    public String shortHash() {
        return commitHash == null ? "" : commitHash.substring(0, Math.min(8, commitHash.length()));
    }

    /** Pre-formatted timestamp for JSPs (JSTL fmt can't handle LocalDateTime). */
    public String committedDisplay() {
        return committedAt == null ? "—"
                : committedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public int totalFindings() { return critical + high + medium + low; }

    // Getters — JSTL EL (${c.author}) needs JavaBean accessors, not public fields.
    public int getId() { return id; }
    public String getCommitHash() { return commitHash; }
    public String getAuthor() { return author; }
    public String getMessage() { return message; }
    public String getBranch() { return branch; }
    public String getRepo() { return repo; }
    public String getGiteaUrl() { return giteaUrl; }
    public LocalDateTime getCommittedAt() { return committedAt; }
    public int getCritical() { return critical; }
    public int getHigh() { return high; }
    public int getMedium() { return medium; }
    public int getLow() { return low; }
    public String getDecision() { return decision; }
    public String getApprovalComment() { return approvalComment; }
    public String getDeployStatus() { return deployStatus; }
}
