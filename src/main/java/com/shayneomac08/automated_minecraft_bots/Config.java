package com.shayneomac08.automated_minecraft_bots;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    // LLM API Keys and Models
    public static final ModConfigSpec.ConfigValue<String> OPENAI_KEY;
    public static final ModConfigSpec.ConfigValue<String> OPENAI_MODEL;
    public static final ModConfigSpec.ConfigValue<String> GEMINI_KEY;
    public static final ModConfigSpec.ConfigValue<String> GEMINI_MODEL;
    public static final ModConfigSpec.ConfigValue<String> GROK_KEY;
    public static final ModConfigSpec.ConfigValue<String> GROK_MODEL;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_LLM;

    static {
        BUILDER.comment("=== LLM Configuration ===",
                        "Configure your LLM API keys in-game via Mod Menu > Automated Minecraft Bots",
                        "Or edit this file directly: config/automated_minecraft_bots-common.toml").push("llm");

        OPENAI_KEY = BUILDER.comment("OpenAI API Key (supports up to 512 characters)",
                                     "Get your key at: https://platform.openai.com/api-keys")
                .define("openai_key", "", obj -> obj instanceof String s && s.length() <= 512);
        OPENAI_MODEL = BUILDER.comment("OpenAI Model (eg. gpt-4o, gpt-4o-mini)")
                .define("openai_model", "gpt-4o");

        GEMINI_KEY = BUILDER.comment("Google Gemini API Key (supports up to 512 characters)",
                                     "Get your key at: https://aistudio.google.com/app/apikey")
                .define("gemini_key", "", obj -> obj instanceof String s && s.length() <= 512);
        GEMINI_MODEL = BUILDER.comment("Gemini Model (eg. gemini-2.0-flash-exp, gemini-1.5-pro)")
                .define("gemini_model", "gemini-2.0-flash-exp");

        GROK_KEY = BUILDER.comment("xAI Grok API Key (supports up to 512 characters)",
                                   "Get your key at: https://console.x.ai/")
                .define("grok_key", "", obj -> obj instanceof String s && s.length() <= 512);
        GROK_MODEL = BUILDER.comment("Grok Model (eg. grok-beta, grok-2)")
                .define("grok_model", "grok-beta");

        DEFAULT_LLM = BUILDER.comment("Default LLM provider (openai, gemini, or grok)")
                .define("default_llm", "openai", obj -> obj instanceof String s &&
                        (s.equals("openai") || s.equals("gemini") || s.equals("grok")));

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();


    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemName));
    }
}
