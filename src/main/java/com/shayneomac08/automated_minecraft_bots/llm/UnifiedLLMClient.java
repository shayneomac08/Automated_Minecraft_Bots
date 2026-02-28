package com.shayneomac08.automated_minecraft_bots.llm;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Unified client that can use OpenAI, Gemini, or Grok based on configuration
 */
public class UnifiedLLMClient {
    private final LLMProvider provider;
    private final OpenAIResponsesClient openaiClient;
    private final GeminiClient geminiClient;
    private final GrokClient grokClient;

    public UnifiedLLMClient(LLMProvider provider, String apiKey, String model) {
        this.provider = provider;

        switch (provider) {
            case OPENAI -> {
                this.openaiClient = new OpenAIResponsesClient(apiKey, model);
                this.geminiClient = null;
                this.grokClient = null;
            }
            case GEMINI -> {
                this.openaiClient = null;
                this.geminiClient = new GeminiClient(apiKey, model);
                this.grokClient = null;
            }
            case GROK -> {
                this.openaiClient = null;
                this.geminiClient = null;
                this.grokClient = new GrokClient(apiKey, model);
            }
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        }
    }

    public String chat(List<Map<String, String>> messages, int maxTokens) throws IOException, InterruptedException {
        return switch (provider) {
            case OPENAI -> openaiClient.chat(messages, maxTokens);
            case GEMINI -> geminiClient.chat(messages, maxTokens);
            case GROK -> grokClient.chat(messages, maxTokens);
        };
    }

    public LLMProvider getProvider() {
        return provider;
    }
}
