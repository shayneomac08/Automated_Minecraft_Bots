package com.shayneomac08.automated_minecraft_bots.llm;

public enum LLMProvider {
    OPENAI("openai"),
    GEMINI("gemini"),
    GROK("grok");

    private final String id;

    LLMProvider(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static LLMProvider fromString(String str) {
        if (str == null) return OPENAI;
        return switch (str.toLowerCase()) {
            case "gemini" -> GEMINI;
            case "grok" -> GROK;
            default -> OPENAI;
        };
    }

    @Override
    public String toString() {
        return id;
    }
}
