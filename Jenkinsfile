// Full pipeline (M5 scanners + M6 report-to-portal).
// Build this up ONE STAGE AT A TIME (M5a..M5d) — only add the next stage once the
// previous is green. Keep `|| true` on scan stages so findings are RECORDED, not fatal.
//
// Jenkins prerequisites (M1/M5):
//   - Maven tool named 'Maven3'
//   - SonarQube server config named 'MySonarServer' (Manage Jenkins -> System)
//   - Dependency-Check tool named 'DepCheck'
//   - Plugins: Gitea, SonarQube Scanner, OWASP Dependency-Check, HTTP Request
//   - Docker available on the agent (for the ZAP stage)
//   - Credentials (Jenkins Credentials, NOT hardcoded): PORTAL_API_TOKEN
pipeline {
  agent any
  tools { maven 'Maven3' }

  environment {
    SONAR_PROJECT_KEY = 'my-app'   // matches the Gitea repo praks/my-app
    // Compose service names + internal ports (Jenkins shares the compose network).
    PORTAL_URL        = 'http://portal:8080/api/scan-results'
    STAGING_URL       = 'http://staging:8080'
  }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Build') {
      steps { sh 'mvn -B clean package -DskipTests' }
    }

    // M5a — SAST. Token passed via a Jenkins credential (proven approach — no
    // global 'MySonarServer' config needed). Single-quoted sh keeps the token
    // out of the build log.
    stage('SAST - SonarQube') {
      steps {
        withCredentials([string(credentialsId: 'SONAR_TOKEN', variable: 'SONAR_TOKEN')]) {
          sh '''mvn -B sonar:sonar -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                -Dsonar.host.url=http://sonarqube:9000 -Dsonar.token=$SONAR_TOKEN'''
        }
      }
    }

    // M5b — SCA (first run is slow: downloads the CVE database)
    stage('SCA - Dependency-Check') {
      steps {
        dependencyCheck additionalArguments: '--scan ./ --format JSON --format HTML',
                        odcInstallation: 'DepCheck'
      }
    }

    // M5c — deploy the built WAR to the staging Tomcat container via the Docker CLI.
    // (docker cp streams the file through the API, so it works from inside Jenkins.)
    stage('Deploy to Staging') {
      steps {
        sh '''docker cp target/*.war portal-staging:/usr/local/tomcat/webapps/my-app.war
              sleep 15'''
      }
    }

    // M5d — DAST against the running staging app. ZAP needs /zap/wrk mounted+writable;
    // we use a named volume (chmod 777, ZAP runs as uid 1000), join the compose network
    // to reach 'staging', then copy the report into the workspace. baseline = only scan
    // what you own.
    stage('DAST - ZAP') {
      steps {
        sh '''
          docker volume create zapwrk >/dev/null 2>&1 || true
          docker run --rm -v zapwrk:/wrk alpine chmod 777 /wrk
          CID=$(docker run -d --network devsecops-portal_default -v zapwrk:/zap/wrk \
                ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t http://staging:8080/my-app/ -J zap-report.json)
          docker wait "$CID" || true
          docker rm "$CID" || true
          docker run --rm -v zapwrk:/wrk alpine cat /wrk/zap-report.json > zap-report.json || echo '{}' > zap-report.json
        '''
      }
    }

    // M6 — hand the commit + build context to the portal, which pulls the actual
    // severity counts from the SonarQube Web API and writes everything to MySQL.
    stage('Report to Portal') {
      steps {
        withCredentials([string(credentialsId: 'PORTAL_API_TOKEN', variable: 'PORTAL_TOKEN')]) {
          script {
            // Embed the SCA/DAST report JSON so the portal parses + stores them too.
            // (SAST counts the portal pulls itself from the SonarQube Web API.)
            def sca  = fileExists('dependency-check-report.json') ? readFile('dependency-check-report.json') : 'null'
            def dast = fileExists('zap-report.json') ? readFile('zap-report.json') : 'null'
            def core = groovy.json.JsonOutput.toJson([
              commitHash     : env.GIT_COMMIT,
              author         : env.GIT_AUTHOR_NAME,
              branch         : env.GIT_BRANCH,
              repo           : SONAR_PROJECT_KEY,
              buildNumber    : env.BUILD_NUMBER,
              sonarProjectKey: SONAR_PROJECT_KEY
            ])
            // Splice the raw report objects into a "reports" field without re-escaping them.
            def payload = core.substring(0, core.length()-1) +
                          ',"reports":{"sca":' + sca + ',"dast":' + dast + '}}'
            httpRequest httpMode: 'POST',
              url: env.PORTAL_URL,
              contentType: 'APPLICATION_JSON',
              customHeaders: [[name: 'X-Portal-Token', value: PORTAL_TOKEN]],
              requestBody: payload
          }
        }
      }
    }
  }

  post {
    always { archiveArtifacts artifacts: '**/dependency-check-report.*, zap-report.json', allowEmptyArchive: true }
  }
}
