# Demo & Status

What's proven working, and how to demonstrate the system end-to-end.

## Status (proven live)

| Milestone | What | State |
|-----------|------|-------|
| M0–M3 | Portal: auth + role filter + dashboards | ✅ live |
| M4 | Gitea → webhook → Jenkins build | ✅ live |
| M5a | SAST (SonarQube) | ✅ live |
| M5b | SCA (OWASP Dependency-Check, NVD) | ⏳ first NVD sync running |
| M5c | Deploy to staging Tomcat | ✅ live |
| M5d | DAST (OWASP ZAP) | ✅ live |
| M6 | Scan results → portal DB → Security dashboard | ✅ live |
| M7 | Approval gate: Security approve → Ops deploy | ✅ live |
| M8 | Idempotency, env secrets, pagination, getters, runbook | ✅ |

Automated tests: `cd portal && mvn test` — 15 JUnit tests (scan-report parsers +
the approval-gate logic on in-memory H2).

## The end-to-end flow

```
developer push → Gitea → webhook → Jenkins pipeline
   build (Maven)
   → SAST  (SonarQube)            -> vulnerabilities
   → SCA   (Dependency-Check/NVD) -> Log4Shell CVE-2021-44228 (critical)
   → deploy to staging Tomcat
   → DAST  (ZAP baseline)         -> missing security headers, etc.
   → POST results to portal /api/scan-results
        → portal writes commits + scan_results + PENDING approval to MySQL
DEVELOPER sees own commits · SECURITY sees findings + Approve/Reject · OPERATIONS deploys approved
```

## How to demo (≈5 min)

1. **Show the running stack** — `docker compose ps` (mysql, gitea, jenkins, sonarqube,
   portal, staging all up).
2. **Trigger the pipeline** — push any change to `praks/my-app` (or click *Build Now* in
   Jenkins). Watch the stages go green in Jenkins: Build → SAST → SCA → Deploy → DAST → Report.
3. **Developer view** — log into the portal as `dev/dev123`: sees its own commits, no vuln data.
4. **Security view** — log in as `sec/sec123`: the commit appears with combined
   **critical/high/medium/low** counts (SAST + SCA + DAST). Click **Approve** (or Reject) with a comment.
5. **Operations view** — log in as `ops/ops123`: only the **approved** commit appears. Click
   **Deploy** → status recorded. (A rejected commit never shows here — the gate holds.)
6. **Cross-role block** — as `dev`, hit `/security/dashboard` → **403** (server-side role filter).

## Service URLs

| Service | URL |
|---------|-----|
| Portal | http://localhost:8081/ |
| Gitea | http://localhost:3001/ |
| Jenkins | http://localhost:8080/ |
| SonarQube | http://localhost:9000/ |
| Staging app | http://localhost:8082/my-app/ |

Seed portal logins: `dev/dev123` · `sec/sec123` · `ops/ops123`.

## Talking points

- The **approval gate** is the core value: Operations can only deploy what Security approved,
  enforced **server-side** (re-checked in the deploy servlet, not just hidden in the UI).
- Three **independent** scanner types — SAST (static), SCA (dependencies/CVEs), DAST (running
  app) — normalized into one severity model and one dashboard.
- **Idempotent**: re-pushing the same commit hash doesn't duplicate rows.
- **Secrets** are all in env vars / Jenkins credentials — nothing committed.
