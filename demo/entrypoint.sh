#!/bin/sh
# Cloud hosts (Render/Koyeb) inject $PORT; default to 8080 (Fly/local).
PORT="${PORT:-8080}"
sed -i "s/port=\"8080\"/port=\"$PORT\"/" /usr/local/tomcat/conf/server.xml
# Fit the JVM inside small free-tier containers (Render free = 512MB). The default
# container heap can be too tight; cap it predictably and use a low-overhead GC.
export CATALINA_OPTS="-XX:MaxRAMPercentage=65.0 -XX:+UseSerialGC ${CATALINA_OPTS}"
echo "[demo] starting Tomcat on port $PORT (DEMO_MODE=$DEMO_MODE)"
exec catalina.sh run
