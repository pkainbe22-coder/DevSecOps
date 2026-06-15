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
| M6 | Pipeline data → portal (`/api/scan-results` + Sonar/Gitea clients + SCA/DAST parsers) | ◐ code complete, unit-tested |
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

## Run it — one command

```bash
./setup.sh
```

This checks Docker, creates `.env`, runs `docker compose up -d --build` (which builds the
portal WAR and brings up MySQL + Gitea + Jenkins + SonarQube + portal + staging), waits for
health, prints the Jenkins initial password, and lists the manual web-UI steps that remain.

Then open **`http://localhost:8081/`** and sign in. After you generate the Gitea + SonarQube
tokens (steps printed by the script), paste them into `.env` and run `docker compose up -d`
to recreate the portal with them.

### Services after `setup.sh`
| URL | What |
|-----|------|
| http://localhost:8081/ | Portal (served at ROOT) |
| http://localhost:3000/ | Gitea — run installer, create org/repo + token |
| http://localhost:9000/ | SonarQube — change pw, create project `myapp` + token |
| http://localhost:8080/ | Jenkins — install plugins, add tools/credentials |
| http://localhost:8082/ | Staging Tomcat (DAST target) |

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
