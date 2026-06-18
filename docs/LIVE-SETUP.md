# Live Setup Runbook

How this stack was actually brought up end-to-end, including the gotchas that the
original plan doesn't mention. Pairs with the top-level `README.md` and `CLAUDE.md`.

> **No secrets in this file.** All tokens live in `.env` (gitignored) or Jenkins
> credentials. Placeholders below show where each value goes.

## 0. Prereqs
- Docker Desktop running, **6 GB memory** (Settings → Resources). SonarQube alone wants ~2 GB.
- **~15 GB free host disk** for the full scanner stack (SonarQube + ZAP image 3.5 GB +
  Dependency-Check image ~1 GB + NVD database ~1–2 GB). See "Disk notes" below.
- JDK 17 + Maven (for building the portal WAR locally if you don't use the portal image).

## 1. Bring up the stack
```bash
cp .env.example .env          # fill secrets as you create them below
docker compose up -d --build mysql gitea jenkins staging portal
docker compose up -d sonarqube   # once you have disk + 6 GB RAM
```

### Gotcha: Gitea port conflict
Host port 3000 is commonly taken (Node dev servers). Compose maps Gitea to **host 3001**
(`3001:3000`). Browser → `http://localhost:3001`; inside the compose network it's still
`http://gitea:3000`.

## 2. Gitea (`http://localhost:3001`)
- It auto-runs its installer (DB is pre-configured via env), so you land on the home page —
  just **Register** the first user (becomes admin).
- Create a repo (here: `praks/my-app`, public).
- **Settings → Applications → tokens:**
  - Portal read token → `.env` `GITEA_API_TOKEN` (+ `GITEA_OWNER`). Scope: `repository:Read`, `user:Read`.
  - A separate write token if you need to push via API.

## 3. SonarQube (`http://localhost:9000`)
- Default `admin/admin` → forced password change. **Policy: needs upper+lower+digit+special**
  (e.g. a 14+ char passphrase with a symbol). A simple all-lowercase password is rejected (HTTP 400).
- Create project `my-app`.
- Generate a **GLOBAL_ANALYSIS_TOKEN** → Jenkins credential `SONAR_TOKEN` (for scanning).
- Generate a **USER_TOKEN** → `.env` `SONAR_API_TOKEN` (portal reads issues with it).

## 4. Jenkins (`http://localhost:8080`)
### Gotcha: the unlock wizard + Safari autofill
Safari's saved-password autofill on `localhost` overwrites the unlock field, so the correct
`initialAdminPassword` is rejected. If the wizard won't accept it, **bypass it server-side**:
```bash
# install required plugins into the volume
docker exec portal-jenkins jenkins-plugin-cli --plugin-download-directory /var/jenkins_home/plugins \
  --plugins workflow-aggregator git gitea sonar dependency-check-jenkins-plugin http_request credentials-binding pipeline-stage-view ws-cleanup
# skip the setup + upgrade wizards
docker exec portal-jenkins sh -c 'V=$(curl -sI http://localhost:8080/ | awk -F": " "/X-Jenkins:/{print \$2}" | tr -d "\r"); \
  printf "%s" "$V" > /var/jenkins_home/jenkins.install.UpgradeWizard.state; \
  printf "%s" "$V" > /var/jenkins_home/jenkins.install.InstallUtil.lastExecVersion'
docker restart portal-jenkins
```
Then log in with `admin` + the `initialAdminPassword`
(`docker exec portal-jenkins cat /var/jenkins_home/secrets/initialAdminPassword`).

### Config (can be done via the script console / REST, not just the UI)
- Maven tool named **`Maven3`** (auto-install 3.9.9).
- Credentials (Secret text): `SONAR_TOKEN`, `PORTAL_API_TOKEN` (matches `.env`), `NVD_API_KEY`.
- Pipeline job from SCM → `http://gitea:3000/praks/my-app.git`, branch `main`, `Jenkinsfile`.
- Gitea webhook → `http://jenkins:8080/gitea-webhook/post` (push events). A 2-min SCM poll is a good fallback.

### Gotcha: Jenkins needs the Docker CLI (for ZAP/DAST)
The base image has the socket mounted but no `docker` binary. `jenkins/Dockerfile` adds the
static Docker CLI; compose builds Jenkins from it.

## 5. The pipeline (`my-app/Jenkinsfile`)
`build → SAST → SCA → deploy-staging → DAST → report`. Docker-in-docker notes:
- **Staging deploy:** `docker cp target/*.war portal-staging:/usr/local/tomcat/webapps/my-app.war`
  (`docker cp` streams via the API, so the source path is read inside Jenkins — works).
- **DAST (ZAP):** ZAP refuses to write a report unless `/zap/wrk` is **mounted and writable**.
  Use a named volume `zapwrk` (`chmod 777`, ZAP runs as uid 1000), join `--network
  devsecops-portal_default` to reach `staging:8080`, then copy the report out via
  `docker run -v zapwrk:/wrk alpine cat`. The naive `-v $(pwd):/zap/wrk` fails inside Jenkins
  (the path is on the daemon, not the Jenkins container).
- **SCA (Dependency-Check):** needs a free **NVD API key** (`nvd.nist.gov/developers/request-an-api-key`).
  First run downloads the NVD database (15–40 min) into the persistent `depcheck-data` volume;
  later runs are fast. Run with `--volumes-from portal-jenkins` so it can read the workspace.
- **Report:** POSTs `{commitHash, author, branch, repo, buildNumber, sonarProjectKey,
  reports:{sca, dast}}` to `http://portal:8080/api/scan-results` with `X-Portal-Token`. The
  portal pulls SAST counts itself from the SonarQube Web API.

## 6. Portal model gotcha
JSP/JSTL EL (`${c.author}`) needs **JavaBean getters**, not public fields — the `Commit`
model must expose `getAuthor()` etc. or the dashboards 500.

## Disk notes (Docker Desktop on macOS)
- `docker system prune` frees space **inside** the Docker VM but does **not** return it to
  macOS — `Docker.raw` is a sparse file that doesn't shrink. Host free space is the real ceiling.
- If the disk fills mid-pull, the daemon can wedge (`context deadline exceeded`). Recover with
  `pkill -9 -f com.docker.backend && open -a Docker` (containers/volumes persist).
- Safe reclaim that doesn't touch personal files: `~/Library/Caches`, `~/.cache` (minus ML
  model caches like HuggingFace), `~/.npm`, `~/.gradle/caches`, browser caches, `brew cleanup -s`.
