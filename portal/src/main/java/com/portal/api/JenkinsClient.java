package com.portal.api;

import com.portal.util.Env;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Triggers a Jenkins deploy job via its remote build API:
 *   POST /job/{job}/buildWithParameters?token={JENKINS_DEPLOY_TOKEN}&COMMIT={hash}
 * Auth: HTTP Basic with JENKINS_USER + JENKINS_API_TOKEN (env, never hardcoded).
 *
 * Returns true if Jenkins accepted the trigger (HTTP 2xx, typically 201). If Jenkins
 * isn't configured (no base URL), returns false so the caller can still record the
 * deployment without a remote build.
 */
public class JenkinsClient {

    private final String baseUrl = Env.get("JENKINS_BASE_URL", "");
    private final String user = Env.get("JENKINS_USER", "");
    private final String apiToken = Env.get("JENKINS_API_TOKEN", "");
    private final String job = Env.get("JENKINS_DEPLOY_JOB", "deploy");
    private final String buildToken = Env.get("JENKINS_DEPLOY_TOKEN", "");
    private final HttpClient http = HttpClient.newHttpClient();

    /** True if a Jenkins base URL is configured (i.e. triggering is possible). */
    public boolean isConfigured() {
        return !baseUrl.isBlank();
    }

    /** Trigger the deploy job for the given commit. Best-effort; logs on failure. */
    public boolean triggerDeploy(String commitHash, String environment) {
        if (!isConfigured()) return false;
        try {
            String url = baseUrl + "/job/" + job + "/buildWithParameters"
                       + "?token=" + enc(buildToken)
                       + "&COMMIT=" + enc(commitHash)
                       + "&ENVIRONMENT=" + enc(environment);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody());
            if (!user.isBlank() && !apiToken.isBlank()) {
                String basic = Base64.getEncoder()
                        .encodeToString((user + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
                b.header("Authorization", "Basic " + basic);
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() / 100 == 2;
            if (!ok) System.err.println("[JenkinsClient] trigger HTTP " + resp.statusCode());
            return ok;
        } catch (Exception e) {
            System.err.println("[JenkinsClient] triggerDeploy failed: " + e.getMessage());
            return false;
        }
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
