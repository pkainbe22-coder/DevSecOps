# Self-contained DEMO image: portal + embedded H2 (no MySQL/Gitea/Jenkins/SonarQube).
# Seeds demo users + commits/scans/approvals so all dashboards are populated.
# Suitable for free, permanent hosting (Render / Fly / Koyeb).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY portal/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY portal/src ./src
RUN mvn -q -B clean package -DskipTests

FROM tomcat:10-jdk17
ENV DEMO_MODE=true \
    PORTAL_DB_URL="jdbc:h2:mem:portal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" \
    PORTAL_DB_USER=sa \
    PORTAL_DB_PASSWORD=""
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /src/target/portal.war /usr/local/tomcat/webapps/ROOT.war
COPY demo/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]
