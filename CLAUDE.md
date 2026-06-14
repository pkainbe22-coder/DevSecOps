# DevSecOps Code Governance Portal

A web portal sitting on top of a DevSecOps pipeline. On every push: build → SAST/SCA/DAST
scans → record results → role-based **approval gate** between Security and Operations.

This file is the persistent context for Claude Code. Keep it current when decisions change.

## Locked decisions (M0)

- **Portal framework:** plain **Servlet + JSP + JDBC** (no Spring). Least friction.
- **Runtime:** **Apache Tomcat 10** → **Jakarta** namespace (`jakarta.servlet.*`, NOT `javax.servlet.*`).
- **JDK:** Java 17.
- **DB:** MySQL 8. Single source of truth.
- **Auth:** session-based, **BCrypt** (jbcrypt) password hashing, server-side role `Filter`.
- **Secrets:** env vars / Jenkins credentials ONLY. Never hardcoded, never committed. `.env` is gitignored.

## Roles & access

| Role | Sees | Does | Cannot |
|------|------|------|--------|
| DEVELOPER | own commits only | view commit history (read-only) | vuln data, approve, deploy |
| SECURITY | all commits + scan results | Approve / Reject (with comment) | trigger deployments |
| OPERATIONS | APPROVED commits only | Deploy + record status | approve; see rejected/pending |

Enforced by a servlet `Filter` mapping URL prefixes to roles: `/developer/*`, `/security/*`, `/ops/*`.
Role lives in the session at login. Wrong-role access → 403 / redirect.

## Security tools (NOT interchangeable)

| Need | Type | Tool | Runs when |
|------|------|------|-----------|
| code analysis | SAST | **SonarQube Community Build** | after build, on source |
| dependency CVEs | SCA | **OWASP Dependency-Check** | after build |
| running-app scan | DAST | **OWASP ZAP** (`zap-baseline.py`) | after deploy to staging |
| classification | — | **OWASP Top 10** | how findings are labelled |

SonarQube Community Build has no branch/PR analysis → post-merge reporting tool (fits this design).

## Architecture

```
Developer push → Gitea --webhook--> Jenkins pipeline
   checkout → build(Maven) → SAST(Sonar) → SCA(DepCheck) → deploy staging → DAST(ZAP)
   → POST results to Portal /api/scan-results → MySQL
   → DEVELOPER / SECURITY / OPERATIONS dashboards
```

## Ports

| Service | Port |
|---------|------|
| Gitea | 3000 |
| Jenkins | 8080 |
| SonarQube | 9000 |
| Portal (Tomcat) | 8081 |
| Staging app (Tomcat) | 8082 |
| MySQL | 3306 |

## Database (5 tables — see `db/schema.sql`)

`users` · `commits` · `scan_results` · `deployment_approvals` · `deployments`.
The approval gate: `deployment_approvals.decision ∈ {PENDING, APPROVED, REJECTED}`.
Only APPROVED commits surface to Operations.

## Integration contracts (concentrate care here — M6)

- **Jenkins → Portal** `POST /api/scan-results` JSON:
  `{ commitHash, author, branch, repo, buildNumber, sonarProjectKey }`
- **SonarQube Web API:** `GET /api/issues/search?componentKeys=<key>&types=VULNERABILITY` → aggregate by severity.
- **Gitea API:** base URL + token from env var → enrich commit (message, url, committed_at).
- Receiver must be **idempotent on `commit_hash`** (re-push must not duplicate rows).

## Build conventions

- One milestone per change; commit after each. Don't "build everything" at once.
- Tomcat 10 → use `jakarta.*` imports everywhere.
- All config (DB URL, tokens, Sonar/Gitea base) read from env vars via `Env` util.
- DAO layer uses plain JDBC with `PreparedStatement`; no ORM.

## Milestones

M0 bootstrap ✅ · M1 infra (you) · M2 schema ✅ · M3 portal shell ✅ · M4 webhook+pipeline ·
M5 scanners · M6 data into portal · M7 workflow gate · M8 hardening.

## Default seed users (dev only — change in prod)

`dev / dev123` (DEVELOPER) · `sec / sec123` (SECURITY) · `ops / ops123` (OPERATIONS).
Seeded idempotently on first boot by `SeedListener`.
