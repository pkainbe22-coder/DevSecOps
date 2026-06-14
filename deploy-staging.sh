#!/usr/bin/env bash
# M5c — deploy the freshly built WAR to the staging Tomcat (port 8082) so the
# DAST (ZAP) stage has a running target. Adjust paths/host to your setup.
set -euo pipefail

WAR=$(ls target/*.war 2>/dev/null | head -1)
if [[ -z "${WAR}" ]]; then
  echo "No WAR found in target/ — did the build run?" >&2
  exit 1
fi

# Where the staging Tomcat picks up webapps. Override via env in the Jenkins job.
STAGING_WEBAPPS="${STAGING_WEBAPPS:-/opt/staging-tomcat/webapps}"
APP_NAME="${APP_NAME:-app}"

echo "Deploying ${WAR} -> ${STAGING_WEBAPPS}/${APP_NAME}.war"
cp "${WAR}" "${STAGING_WEBAPPS}/${APP_NAME}.war"

# Give Tomcat a moment to hot-deploy before ZAP hits it.
sleep 15
echo "Staging deploy done."
