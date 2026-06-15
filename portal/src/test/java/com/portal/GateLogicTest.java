package com.portal;

import com.portal.dao.ApprovalDao;
import com.portal.dao.CommitDao;
import com.portal.dao.ScanResultDao;
import com.portal.model.Commit;
import com.portal.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The heart of the system: the approval gate. Proves approved commits reach Ops,
 * rejected/pending ones don't, plus idempotency and the Security filter/pagination.
 */
class GateLogicTest {

    private final CommitDao commits = new CommitDao();
    private final ApprovalDao approvals = new ApprovalDao();
    private final ScanResultDao scans = new ScanResultDao();

    @BeforeEach
    void setUp() { TestDb.reset(); }

    private int newCommit(String hash, String author) {
        Commit c = new Commit();
        c.commitHash = hash; c.author = author; c.repo = "myapp"; c.branch = "main";
        c.message = "msg " + hash;
        int id = commits.upsert(c);
        approvals.ensurePending(id);   // mirrors what the receiver does
        return id;
    }

    // ---- the gate ----------------------------------------------------------

    @Test
    void approvedCommitReachesOps() {
        int id = newCommit("aaa1", "dev");
        approvals.decide(id, "APPROVED", 1, "looks good");

        assertTrue(approvals.isApproved(id));
        List<Commit> ops = commits.findApprovedForOps(20, 0);
        assertEquals(1, ops.size());
        assertEquals("aaa1", ops.get(0).commitHash);
    }

    @Test
    void rejectedCommitNeverReachesOps() {
        int id = newCommit("bbb2", "dev");
        approvals.decide(id, "REJECTED", 1, "vuln");

        assertFalse(approvals.isApproved(id));
        assertTrue(commits.findApprovedForOps(20, 0).isEmpty(), "rejected must not reach ops");
    }

    @Test
    void pendingCommitNeverReachesOps() {
        newCommit("ccc3", "dev");   // left PENDING
        assertTrue(commits.findApprovedForOps(20, 0).isEmpty(), "pending must not reach ops");
    }

    // ---- idempotency -------------------------------------------------------

    @Test
    void commitUpsertIsIdempotentOnHash() {
        int id1 = newCommit("dup1", "dev");
        Commit again = new Commit();
        again.commitHash = "dup1"; again.author = "dev"; again.repo = "myapp";
        int id2 = commits.upsert(again);
        assertEquals(id1, id2, "same hash must not create a second commit row");
    }

    @Test
    void ensurePendingIsIdempotent() {
        int id = newCommit("pen1", "dev");
        approvals.ensurePending(id);   // second call
        approvals.ensurePending(id);   // third call
        // Still exactly one row, still actionable as PENDING (decide once works).
        approvals.decide(id, "APPROVED", 1, null);
        assertTrue(approvals.isApproved(id));
    }

    @Test
    void scanUpsertReplacesNotDuplicates() {
        int id = newCommit("scan1", "dev");
        scans.upsert(id, "SAST", "SonarQube", new Severity(1, 2, 3, 4), null);
        scans.upsert(id, "SAST", "SonarQube", new Severity(5, 0, 0, 0), null); // re-scan

        Commit c = commits.findAllForSecurity(null, 20, 0).get(0);
        assertEquals(5, c.critical, "re-scan should overwrite, not add");
        assertEquals(0, c.high);
    }

    @Test
    void severityCountsSumAcrossScanTypes() {
        int id = newCommit("scan2", "dev");
        scans.upsert(id, "SAST", "SonarQube", new Severity(1, 0, 0, 0), null);
        scans.upsert(id, "SCA", "DependencyCheck", new Severity(0, 2, 0, 0), null);
        scans.upsert(id, "DAST", "ZAP", new Severity(0, 0, 3, 0), null);

        Commit c = commits.findAllForSecurity(null, 20, 0).get(0);
        assertEquals(1, c.critical);
        assertEquals(2, c.high);
        assertEquals(3, c.medium);
    }

    // ---- security filter ---------------------------------------------------

    @Test
    void securityFilterPartitionsByDecision() {
        approvals.decide(newCommit("f-app", "dev"), "APPROVED", 1, null);
        approvals.decide(newCommit("f-rej", "dev"), "REJECTED", 1, null);
        newCommit("f-pen", "dev"); // pending

        assertEquals(3, commits.findAllForSecurity(null, 50, 0).size());
        assertEquals(1, commits.findAllForSecurity("APPROVED", 50, 0).size());
        assertEquals(1, commits.findAllForSecurity("REJECTED", 50, 0).size());
        assertEquals(1, commits.findAllForSecurity("PENDING", 50, 0).size());
    }

    // ---- pagination --------------------------------------------------------

    @Test
    void developerPaginationRespectsLimitAndOffset() {
        for (int i = 0; i < 25; i++) newCommit("p" + i, "dev");
        // Fetch PAGE_SIZE+1 to detect a next page, as the servlet does.
        assertEquals(21, commits.findByAuthor("dev", 21, 0).size());  // 20 shown + 1 sentinel
        assertEquals(5, commits.findByAuthor("dev", 21, 20).size());  // remainder on page 2
    }

    @Test
    void developerSeesOnlyOwnCommits() {
        newCommit("mine", "dev");
        newCommit("theirs", "someoneelse");
        List<Commit> mine = commits.findByAuthor("dev", 20, 0);
        assertEquals(1, mine.size());
        assertEquals("mine", mine.get(0).commitHash);
    }
}
