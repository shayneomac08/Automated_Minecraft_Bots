package com.shayneomac08.automated_minecraft_bots.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shayneomac08.automated_minecraft_bots.BotConfig;
import com.shayneomac08.automated_minecraft_bots.agent.ActionPlan;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// Package-private interface kept here so OpenAIResponsesClient can implement it.
// LlmClient.java and LLMClient.java are the same path on Windows (case-insensitive FS).
interface LlmClient {
    ActionPlan plan(String prompt) throws Exception;
}

/**
 * Unified LLM query client.
 * Routes to Grok, OpenAI, Gemini, Claude, or Ollama.
 */
public class LLMClient {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private LLMClient() {}

    /** Query using the global BotConfig provider with a 20-token limit. */
    public static String query(String prompt) throws IOException, InterruptedException {
        return query(prompt, BotConfig.LLM_PROVIDER.get(), 20);
    }

    /** Query using an explicit provider string and token limit. */
    public static String query(String prompt, String provider, int maxTokens)
            throws IOException, InterruptedException {
        return switch (provider.toLowerCase().trim()) {
            case "openai"  -> queryOpenAI(prompt, maxTokens);
            case "gemini"  -> queryGemini(prompt, maxTokens);
            case "claude"  -> queryClaude(prompt, maxTokens);
            case "ollama"  -> queryOllama(prompt, maxTokens);
            default        -> queryGrok(prompt, maxTokens);   // "grok" + unrecognised
        };
    }

    // ── Grok ──────────────────────────────────────────────────────────────────

    private static String queryGrok(String prompt, int maxTokens) throws IOException, InterruptedException {
        String body = buildOpenAICompatibleBody(BotConfig.GROK_MODEL.get(), prompt, maxTokens);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BotConfig.GROK_API_URL.get()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + BotConfig.GROK_API_KEY.get())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp, "Grok");
        return LlmResponseParser.extractAssistantContent(resp.body(), "Grok");
    }

    // ── OpenAI ────────────────────────────────────────────────────────────────

    private static String queryOpenAI(String prompt, int maxTokens) throws IOException, InterruptedException {
        String body = buildOpenAICompatibleBody("gpt-4o-mini", prompt, maxTokens);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + BotConfig.OPENAI_API_KEY.get())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp, "OpenAI");
        return LlmResponseParser.extractAssistantContent(resp.body(), "OpenAI");
    }

    // ── Gemini ────────────────────────────────────────────────────────────────

    private static String queryGemini(String prompt, int maxTokens) throws IOException, InterruptedException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + "gemini-2.0-flash:generateContent?key=" + BotConfig.GEMINI_API_KEY.get();

        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject contentObj = new JsonObject();
        contentObj.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(contentObj);
        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("maxOutputTokens", maxTokens);
        JsonObject root = new JsonObject();
        root.add("contents", contents);
        root.add("generationConfig", genConfig);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp, "Gemini");

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray candidates = json.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty())
            throw new IOException("No candidates in Gemini response");
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        JsonArray partsArr = content.getAsJsonArray("parts");
        if (partsArr == null || partsArr.isEmpty())
            throw new IOException("No parts in Gemini response");
        return partsArr.get(0).getAsJsonObject().get("text").getAsString();
    }

    // ── Claude ────────────────────────────────────────────────────────────────

    private static String queryClaude(String prompt, int maxTokens) throws IOException, InterruptedException {
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(userMsg);
        JsonObject root = new JsonObject();
        root.addProperty("model", "claude-sonnet-4-20250514");
        root.add("messages", messages);
        root.addProperty("max_tokens", maxTokens);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", BotConfig.CLAUDE_API_KEY.get())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp, "Claude");

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray content = json.getAsJsonArray("content");
        if (content == null || content.isEmpty())
            throw new IOException("No content in Claude response");
        return content.get(0).getAsJsonObject().get("text").getAsString();
    }

    // ── Ollama ────────────────────────────────────────────────────────────────

    private static String queryOllama(String prompt, int maxTokens) throws IOException, InterruptedException {
        String body = buildOpenAICompatibleBody("llama3", prompt, maxTokens);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BotConfig.OLLAMA_URL.get() + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        checkStatus(resp, "Ollama");

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject messageObj = json.getAsJsonObject("message");
        if (messageObj == null)
            throw new IOException("No message object in Ollama response");
        return messageObj.get("content").getAsString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String buildOpenAICompatibleBody(String model, String prompt, int maxTokens) {
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(userMsg);
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.add("messages", messages);
        root.addProperty("max_tokens", maxTokens);
        return root.toString();
    }

    private static void checkStatus(HttpResponse<String> resp, String provider) throws IOException {
        if (resp.statusCode() != 200) {
            throw new IOException(provider + " API error " + resp.statusCode() + ": " + resp.body());
        }
    }
}
