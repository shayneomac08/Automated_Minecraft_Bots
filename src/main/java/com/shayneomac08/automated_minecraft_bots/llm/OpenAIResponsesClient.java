package com.shayneomac08.automated_minecraft_bots.llm;

import com.shayneomac08.automated_minecraft_bots.agent.ActionPlan;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class OpenAIResponsesClient implements LlmClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final String apiKey;
    private final String model;

    public OpenAIResponsesClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ActionPlan plan(String prompt) throws Exception {
        String body = """
        {
          "model": "%s",
          "input": [
            {"role":"system","content":"You control a Minecraft bot. Output ONLY valid JSON: {\\"actions\\":[...]}."},
            {"role":"user","content":%s}
          ]
        }
        """.formatted(model, jsonString(prompt));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/responses"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("OpenAI error " + resp.statusCode() + ": " + resp.body());
        }

        String json = extractFirstJsonObject(resp.body());
        return SimpleJson.parseActionPlan(json);
    }

    public String chat(List<Map<String, String>> messages, int maxTokens) throws IOException, InterruptedException {
        // DEBUG: Log API configuration
        System.out.println("=== OPENAI REQUEST DEBUG ===");
        System.out.println("Model: " + model);
        System.out.println("API Key (first 20 chars): " + (apiKey != null ? apiKey.substring(0, Math.min(20, apiKey.length())) : "NULL"));
        System.out.println("API Key length: " + (apiKey != null ? apiKey.length() : 0));
        System.out.println("Max tokens: " + maxTokens);
        System.out.println("===========================");

        String requestBody = SimpleJson.toJson(Map.of(
            "model", model,
            "messages", messages,
            "max_completion_tokens", maxTokens,
            "temperature", 0.7
        ));

        System.out.println("=== REQUEST BODY ===");
        System.out.println(requestBody);
        System.out.println("====================");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        // Get response body ONCE
        String rawResponse = resp.body();

        // DEBUG: Log raw response
        System.out.println("=== RAW OPENAI RESPONSE ===");
        System.out.println("Status Code: " + resp.statusCode());
        System.out.println("Response Body: " + rawResponse);
        System.out.println("===========================");

        if (resp.statusCode() != 200) {
            throw new IOException("OpenAI API error: " + resp.statusCode() + " - " + rawResponse);
        }

        // Use the shared robust Gson-based extraction
        return LlmResponseParser.extractAssistantContent(rawResponse, "OpenAI");
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String extractFirstJsonObject(String s) {
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b < 0 || b <= a) throw new RuntimeException("No JSON object found");
        return s.substring(a, b + 1);
    }
}
