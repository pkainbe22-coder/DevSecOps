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
 * Groq provider — a free, fast LLM backend (OpenAI-compatible chat completions API).
 * Lets the AI Security Analyst run at no cost when no Claude key is present.
 *
 * Wire format: POST https://api.groq.com/openai/v1/chat/completions
 *   headers: Authorization: Bearer <key>, content-type: application/json
 *   body:    { model, max_tokens, temperature, messages:[{role:"system"},{role:"user"}] }
 *
 * Gated on GROQ_API_KEY; model defaults to llama-3.3-70b-versatile (override GROQ_MODEL).
 */
public class GroqClient implements LlmProvider {

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final String apiKey = Env.get("GROQ_API_KEY", "");
    private final String model  = Env.get("GROQ_MODEL", "llama-3.3-70b-versatile");
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Override public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
    @Override public String name() { return "Groq (" + model + ")"; }

    @Override
    public String complete(String system, String user, int maxTokens) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", maxTokens);
            body.addProperty("temperature", 0.3);
            JsonArray messages = new JsonArray();
            messages.add(message("system", system));
            messages.add(message("user", user));
            body.add("messages", messages);

            HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("content-type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401) throw new RuntimeException("Groq API rejected the key (401)");
            if (resp.statusCode() / 100 != 2)
                throw new RuntimeException("Groq API HTTP " + resp.statusCode());

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) return "";
            JsonObject m = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            return m != null && m.has("content") ? m.get("content").getAsString().trim() : "";
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Groq API call failed: " + e.getMessage(), e);
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
