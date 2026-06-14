package com.portal.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.model.Commit;
import com.portal.util.Env;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;

/**
 * Enriches a commit with details from the Gitea API:
 *   GET /api/v1/repos/{owner}/{repo}/git/commits/{sha}
 * Auth: token from env (GITEA_API_TOKEN). Best-effort — failures degrade gracefully.
 */
public class GiteaClient {

    private final String baseUrl = Env.get("GITEA_BASE_URL", "http://localhost:3000");
    private final String token = Env.get("GITEA_API_TOKEN", "");
    private final String owner = Env.get("GITEA_OWNER", "");
    private final HttpClient http = HttpClient.newHttpClient();

    /** Fills message, giteaUrl and committedAt on the given commit when possible. */
    public void enrich(Commit c) {
        if (owner.isBlank() || c.repo == null || c.commitHash == null) return;
        try {
            String url = baseUrl + "/api/v1/repos/" + owner + "/" + c.repo
                       + "/git/commits/" + c.commitHash;
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
            if (!token.isBlank()) b.header("Authorization", "token " + token);
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return;

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (root.has("html_url")) c.giteaUrl = root.get("html_url").getAsString();
            if (root.has("commit")) {
                JsonObject commit = root.getAsJsonObject("commit");
                if (commit.has("message") && (c.message == null || c.message.isBlank()))
                    c.message = commit.get("message").getAsString();
                if (commit.has("author")) {
                    JsonObject author = commit.getAsJsonObject("author");
                    if (author.has("date"))
                        c.committedAt = OffsetDateTime.parse(author.get("date").getAsString())
                                .toLocalDateTime();
                    if ((c.author == null || c.author.isBlank()) && author.has("name"))
                        c.author = author.get("name").getAsString();
                }
            }
        } catch (Exception e) {
            System.err.println("[GiteaClient] enrich failed: " + e.getMessage());
        }
    }
}
