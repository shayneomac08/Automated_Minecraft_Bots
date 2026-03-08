package com.shayneomac08.automated_minecraft_bots;

import net.neoforged.neoforge.common.ModConfigSpec;

public class BotConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> LLM_PROVIDER;

    // ── Grok ──────────────────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<String> GROK_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> GROK_API_URL;
    public static final ModConfigSpec.ConfigValue<String> GROK_MODEL;

    // ── Other providers ───────────────────────────────────────────────────────
    public static final ModConfigSpec.ConfigValue<String> OPENAI_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> GEMINI_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> CLAUDE_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_URL;

    static {
        BUILDER.comment("LLM provider settings for Automated Minecraft Bots").push("llm");

        LLM_PROVIDER = BUILDER
                .comment("Active LLM provider (grok, openai, gemini, claude, ollama)")
                .define("llm_provider", "grok");

        GROK_API_KEY = BUILDER
                .comment("API key for Grok (xAI) — get yours at https://console.x.ai/")
                .define("grok_api_key", "");

        GROK_API_URL = BUILDER
                .comment("API URL for Grok")
                .define("grok_api_url", "https://api.x.ai/v1/chat/completions");

        GROK_MODEL = BUILDER
                .comment("Model for Grok (e.g. grok-3-beta, grok-2-1212)")
                .define("grok_model", "grok-3-beta");

        OPENAI_API_KEY = BUILDER
                .comment("API key for OpenAI")
                .define("openai_api_key", "");

        GEMINI_API_KEY = BUILDER
                .comment("API key for Google Gemini")
                .define("gemini_api_key", "");

        CLAUDE_API_KEY = BUILDER
                .comment("API key for Anthropic Claude")
                .define("claude_api_key", "");

        OLLAMA_URL = BUILDER
                .comment("Ollama local server URL")
                .define("ollama_url", "http://localhost:11434");

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
