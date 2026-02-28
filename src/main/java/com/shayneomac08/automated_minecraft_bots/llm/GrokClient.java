package com.shayneomac08.automated_minecraft_bots.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class GrokClient {
    private final String apiKey;
    private final String model;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public GrokClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public String chat(List<Map<String, String>> messages, int maxTokens) throws IOException, InterruptedException {
        // Grok uses OpenAI-compatible API format
        String requestBody = SimpleJson.toJson(Map.of(
            "model", model,
            "messages", messages,
            "max_completion_tokens", maxTokens,
            "temperature", 0.7
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.x.ai/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        String rawResponse = response.body();

        if (response.statusCode() != 200) {
            throw new IOException("Grok API error: " + response.statusCode() + " - " + rawResponse);
        }

        // Use shared robust Gson-based extraction (OpenAI-compatible format)
        return LlmResponseParser.extractAssistantContent(rawResponse, "Grok");
    }
}
