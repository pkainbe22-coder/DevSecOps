#!/bin/sh
# Cloud hosts (Render/Koyeb) inject $PORT; default to 8080 (Fly/local).
PORT="${PORT:-8080}"
sed -i "s/port=\"8080\"/port=\"$PORT\"/" /usr/local/tomcat/conf/server.xml
echo "[demo] starting Tomcat on port $PORT (DEMO_MODE=$DEMO_MODE)"
exec catalina.sh run
