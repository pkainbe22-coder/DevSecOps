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
    SONAR_PROJECT_KEY = 'myapp'
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

    // M5a — SAST
    stage('SAST - SonarQube') {
      steps {
        withSonarQubeEnv('MySonarServer') {
          sh "mvn sonar:sonar -Dsonar.projectKey=${SONAR_PROJECT_KEY}"
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

    // M5c — deploy the built WAR to the staging Tomcat
    stage('Deploy to Staging') {
      steps { sh './deploy-staging.sh' }
    }

    // M5d — DAST against the running staging app. baseline first; only scan what you own.
    stage('DAST - ZAP') {
      steps {
        sh '''docker run --rm -v "$(pwd)":/zap/wrk:rw ghcr.io/zaproxy/zaproxy \
              zap-baseline.py -t ${STAGING_URL} -J zap-report.json || true'''
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
