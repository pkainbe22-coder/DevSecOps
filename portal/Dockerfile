# Multi-stage: build the WAR with Maven, then run it on Tomcat 10 at the ROOT context
# (so the app is http://localhost:8081/ and the receiver is /api/scan-results).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM tomcat:10-jdk17
RUN rm -rf /usr/local/tomcat/webapps/*
# Deploy as ROOT.war -> served at "/".
COPY --from=build /src/target/portal.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
