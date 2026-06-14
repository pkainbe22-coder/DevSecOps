# DevSecOps Code Governance Portal

A web portal on top of a DevSecOps pipeline. On every push: build → SAST/SCA/DAST scans →
record results → role-based **approval gate** between Security and Operations.

> Full design & milestone plan: see `CLAUDE.md` and the execution document.

## Status

| Milestone | What | State |
|-----------|------|-------|
| M0 | Bootstrap: `CLAUDE.md`, `docker-compose.yml`, `.env` | ✅ |
| M1 | Infra: MySQL, Gitea, Jenkins, SonarQube | ⬜ you provision (needs Docker + RAM) |
| M2 | DB schema + seed users | ✅ |
| M3 | Portal shell: auth + role filter + dashboards | ✅ built & compiles |
| M4 | Webhook + minimal pipeline (`jenkins/Jenkinsfile.minimal`) | ◐ code written; job/webhook = you |
| M5 | Scanners (`Jenkinsfile`, `deploy-staging.sh`) | ◐ code written; needs running infra |
| M6 | Pipeline data → portal (`/api/scan-results` + Sonar/Gitea clients) | ◐ code written & compiles |
| M7 | Workflow gate (approve/reject/deploy) | ◐ code written & compiles |
| M8 | Hardening (idempotency, env secrets, server-side gate, README) | ◐ partial |

**Legend:** ✅ done · ◐ code complete & compiles, needs running infra to verify · ⬜ yours.

> The entire **code track compiles into `portal.war`**. What remains is the **infra track**
> (M1) and the inherently-manual web-UI config (Gitea webhook, Jenkins job + plugins,
> SonarQube token) — none of which can run without Docker and a host with ~2GB+ free RAM.

## Layout

```
db/                      schema.sql + DB init (5 tables)
docker-compose.yml       MySQL + Gitea + Jenkins + SonarQube (M1)
portal/                  Maven web app (Servlet + JSP + JDBC, Tomcat 10 / Jakarta)
  src/main/java/com/portal/
    auth/     login, logout, dashboards, seed listener
    filter/   RoleFilter (URL-prefix → role)
    dao/      UserDao (plain JDBC)
    util/     Env, Db, Passwords (BCrypt)
  src/main/webapp/       JSP views + assets
.env.example             copy to .env, fill secrets (gitignored)
```

## Prerequisites

- JDK 17, Maven, Docker (for M1 infra).

## Run it (M1 + M3)

```bash
# 1. Secrets
cp .env.example .env          # edit values

# 2. Infra (M1). Loads db/schema.sql into the 'portal' DB automatically.
docker compose up -d mysql    # add gitea/jenkins/sonarqube when you reach M4/M5

# 3. Build the portal WAR
cd portal && mvn clean package # -> portal/target/portal.war

# 4. Deploy to Tomcat 10 on :8081 (copy WAR to $CATALINA_BASE/webapps/),
#    passing the env vars from .env to the Tomcat process.
```

Then open `http://localhost:8081/portal/` and sign in.

## Seed accounts (dev only — change in prod)

| User | Pass | Role |
|------|------|------|
| `dev` | `dev123` | DEVELOPER |
| `sec` | `sec123` | SECURITY |
| `ops` | `ops123` | OPERATIONS |

Seeded idempotently on first boot by `SeedListener`. Override via `SEED_*` env vars.

## Security notes

- Passwords stored as **BCrypt** hashes only.
- Access enforced **server-side** by `RoleFilter` (`/developer/*`, `/security/*`, `/ops/*`).
- All secrets/tokens come from **env vars** — never committed. `.env` is gitignored.
