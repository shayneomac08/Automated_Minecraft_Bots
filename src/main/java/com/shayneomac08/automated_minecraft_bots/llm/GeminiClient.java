package com.shayneomac08.automated_minecraft_bots.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class GeminiClient {
    private final String apiKey;
    private final String model;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public String chat(List<Map<String, String>> messages, int maxTokens) throws IOException, InterruptedException {
        // Convert messages to Gemini format
        StringBuilder contentBuilder = new StringBuilder();
        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");
            if ("system".equals(role)) {
                contentBuilder.append("Instructions: ").append(content).append("\n\n");
            } else if ("user".equals(role)) {
                contentBuilder.append("User: ").append(content).append("\n");
            } else if ("assistant".equals(role)) {
                contentBuilder.append("Assistant: ").append(content).append("\n");
            }
        }

        // Build Gemini API request
        String requestBody = SimpleJson.toJson(Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of(
                    "text", contentBuilder.toString()
                ))
            )),
            "generationConfig", Map.of(
                "maxOutputTokens", maxTokens,
                "temperature", 0.7
            )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Gemini API error: " + response.statusCode() + " - " + response.body());
        }

        // Parse response
        Map<String, Object> json = SimpleJson.parseObject(response.body());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) json.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("No response from Gemini");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) candidates.getFirst().get("content");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IOException("No text in Gemini response");
        }

        return (String) parts.getFirst().get("text");
    }
}
