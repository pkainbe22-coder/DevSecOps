package com.portal.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portal.util.Env;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Claude Messages API provider. Consistent with the other API clients here:
 * java.net.http + Gson, no SDK dependency.
 *
 * Wire format: POST https://api.anthropic.com/v1/messages
 *   headers: x-api-key, anthropic-version: 2023-06-01, content-type: application/json
 *   body:    { model, max_tokens, system, messages:[{role:"user", content}] }
 *
 * Gated on ANTHROPIC_API_KEY; model defaults to claude-opus-4-8 (override CLAUDE_MODEL).
 */
public class AnthropicClient implements LlmProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey = Env.get("ANTHROPIC_API_KEY", Env.get("CLAUDE_API_KEY", ""));
    private final String model  = Env.get("CLAUDE_MODEL", "claude-opus-4-8");
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
    @Override public String name() { return "Claude (" + model + ")"; }

    @Override
    public String complete(String system, String user, int maxTokens) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", maxTokens);
            body.addProperty("system", system);
            JsonArray messages = new JsonArray();
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", user);
            messages.add(msg);
            body.add("messages", messages);

            HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401) throw new RuntimeException("Claude API rejected the key (401)");
            if (resp.statusCode() / 100 != 2)
                throw new RuntimeException("Claude API HTTP " + resp.statusCode());

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray content = root.getAsJsonArray("content");
            StringBuilder out = new StringBuilder();
            for (var block : content) {
                JsonObject b = block.getAsJsonObject();
                if (b.has("text")) out.append(b.get("text").getAsString());
            }
            return out.toString().trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Claude API call failed: " + e.getMessage(), e);
        }
    }
}
