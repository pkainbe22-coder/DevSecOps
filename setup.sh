#!/usr/bin/env bash
# One-shot setup for the scriptable parts of the DevSecOps Portal.
# Brings up the whole stack (infra + portal) and tells you exactly what manual
# web-UI steps remain. It does NOT click through Gitea/Jenkins/SonarQube — it can't.
set -euo pipefail

cd "$(dirname "$0")"
say()  { printf '\n\033[1;36m== %s ==\033[0m\n' "$*"; }
ok()   { printf '\033[0;32m  ✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[0;33m  ! %s\033[0m\n' "$*"; }

# 1. Docker -------------------------------------------------------------------
say "Checking Docker"
if ! command -v docker >/dev/null 2>&1; then
  warn "docker not found. Install Docker Desktop:  brew install --cask docker  (then open it once)"
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  warn "Docker daemon not running. Start Docker Desktop and re-run."
  exit 1
fi
ok "Docker is up"

# 2. .env ---------------------------------------------------------------------
say "Secrets (.env)"
if [[ ! -f .env ]]; then
  cp .env.example .env
  ok "Created .env from template"
  warn "API tokens are blank — fill GITEA_API_TOKEN / SONAR_API_TOKEN after steps 3-4 and re-run."
else
  ok ".env already exists"
fi

# 3. Bring up the stack -------------------------------------------------------
say "Building + starting the stack (this builds the portal WAR; first run is slow)"
docker compose up -d --build
ok "Containers launched"

# 4. Wait for MySQL + portal --------------------------------------------------
say "Waiting for MySQL to be healthy"
for i in $(seq 1 30); do
  status=$(docker inspect -f '{{.State.Health.Status}}' portal-mysql 2>/dev/null || echo "starting")
  [[ "$status" == "healthy" ]] && { ok "MySQL healthy"; break; }
  sleep 3
  [[ $i -eq 30 ]] && warn "MySQL still not healthy — check: docker compose logs mysql"
done

say "Waiting for the portal to respond on :8081"
for i in $(seq 1 30); do
  if curl -sf -o /dev/null http://localhost:8081/login 2>/dev/null; then ok "Portal is serving"; break; fi
  sleep 3
  [[ $i -eq 30 ]] && warn "Portal not responding yet — check: docker compose logs portal"
done

# 5. Jenkins password ---------------------------------------------------------
say "Jenkins initial admin password"
docker exec portal-jenkins cat /var/jenkins_home/secrets/initialAdminPassword 2>/dev/null \
  | sed 's/^/  /' || warn "Jenkins still starting — re-run this later: docker exec portal-jenkins cat /var/jenkins_home/secrets/initialAdminPassword"

# 6. What's left (manual) -----------------------------------------------------
say "Done — open these and finish the manual web-UI steps"
cat <<'EOF'
  Portal      http://localhost:8081/      (login: dev/dev123  sec/sec123  ops/ops123)
  Gitea       http://localhost:3000/      run installer -> create org/repo + API token
  SonarQube   http://localhost:9000/      admin/admin -> change pw -> project 'myapp' + token
  Jenkins     http://localhost:8080/      unlock (password above) -> install plugins:
                                          Gitea, SonarQube Scanner, OWASP Dependency-Check, HTTP Request
                                          tools: Maven3, DepCheck ; server: MySonarServer
                                          credential (Secret text): PORTAL_API_TOKEN
  Staging     http://localhost:8082/      empty Tomcat (DAST target for your app)

  After generating the Gitea + Sonar tokens, paste them into .env and run:
     docker compose up -d   (recreates the portal with the new tokens)

  Then M4: copy jenkins/Jenkinsfile.minimal -> repo root as Jenkinsfile, create the
  Jenkins pipeline job, add the Gitea push webhook (http://jenkins:8080/gitea-webhook/post),
  and confirm a green build. Only then add scanners (M5) one stage at a time.
EOF
