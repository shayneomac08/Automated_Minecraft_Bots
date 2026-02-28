package com.shayneomac08.automated_minecraft_bots.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Shared utility for parsing OpenAI-compatible API responses.
 * Used by OpenAI, Grok, and other compatible LLM clients.
 */
public final class LlmResponseParser {

    private LlmResponseParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Robust extraction of assistant content from OpenAI-compatible response using Gson.
     *
     * @param rawJson The raw JSON response from the API
     * @param providerName The name of the provider (for logging purposes)
     * @return The extracted content string
     * @throws RuntimeException if parsing fails or response is invalid
     */
    public static String extractAssistantContent(String rawJson, String providerName) {
        try {
            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();

            // Safety check for error responses
            if (root.has("error")) {
                JsonElement errorElem = root.get("error");
                throw new RuntimeException(providerName + " API error: " + errorElem.toString());
            }

            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("No choices in response");
            }

            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject messageObj = firstChoice.getAsJsonObject("message");

            if (messageObj == null) {
                // Debug dump so we can see exactly what arrived
                System.out.println("[AMB CRITICAL] Full firstChoice object: " + firstChoice);
                throw new RuntimeException("Message object is null in first choice. Raw: " + rawJson);
            }

            JsonElement contentElem = messageObj.get("content");
            if (contentElem == null || contentElem.isJsonNull()) {
                throw new RuntimeException("Content field missing or null");
            }

            String content = contentElem.getAsString();
            System.out.println("[AMB] ✅ SUCCESSFULLY EXTRACTED CONTENT (" + providerName + "): " + content);
            return content;

        } catch (Exception e) {
            System.err.println("[AMB] " + providerName + " JSON parse failed: " + e.getMessage());
            throw new RuntimeException("Failed to parse " + providerName + " response", e);
        }
    }
}
