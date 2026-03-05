package com.shayneomac08.automated_minecraft_bots.client;

import com.shayneomac08.automated_minecraft_bots.BotConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
    private final Screen parent;

    // Six input fields: provider selector + one per API provider
    private EditBox providerField;
    private EditBox grokKeyField;
    private EditBox openaiKeyField;
    private EditBox geminiKeyField;
    private EditBox claudeKeyField;
    private EditBox ollamaUrlField;

    // Layout constants
    private static final int LABEL_W  = 70;
    private static final int GAP      = 4;
    private static final int FIELD_H  = 20;
    private static final int SPACING  = 32;
    private static final int START_Y  = 50;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Automated Minecraft Bots - Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int totalW  = Math.min(500, this.width - 20);
        int leftX   = (this.width - totalW) / 2;
        int fieldX  = leftX + LABEL_W + GAP;
        int fieldW  = totalW - LABEL_W - GAP;

        // ── Label buttons (left side) ─────────────────────────────────────────
        String[] labels = {"Provider", "Grok", "OpenAI", "Gemini", "Claude", "Ollama"};
        for (int i = 0; i < labels.length; i++) {
            final String lbl = labels[i];
            this.addRenderableWidget(
                Button.builder(Component.literal(lbl), btn -> {})
                      .bounds(leftX, START_Y + SPACING * i, LABEL_W, FIELD_H)
                      .build()
            );
        }

        // ── Input fields (right side) ──────────────────────────────────────────
        // Provider selector (grok / openai / gemini / claude / ollama)
        this.providerField = new EditBox(this.font, fieldX, START_Y, fieldW, FIELD_H,
                Component.literal("Active LLM Provider"));
        this.providerField.setMaxLength(32);
        this.providerField.setValue(BotConfig.LLM_PROVIDER.get());
        this.providerField.setHint(Component.literal("grok / openai / gemini / claude / ollama"));
        this.addRenderableWidget(this.providerField);

        // Grok
        this.grokKeyField = new EditBox(this.font, fieldX, START_Y + SPACING, fieldW, FIELD_H,
                Component.literal("Grok API Key"));
        this.grokKeyField.setMaxLength(512);
        this.grokKeyField.setValue(BotConfig.GROK_API_KEY.get());
        this.grokKeyField.setHint(Component.literal("xai-..."));
        this.addRenderableWidget(this.grokKeyField);

        // OpenAI
        this.openaiKeyField = new EditBox(this.font, fieldX, START_Y + SPACING * 2, fieldW, FIELD_H,
                Component.literal("OpenAI API Key"));
        this.openaiKeyField.setMaxLength(512);
        this.openaiKeyField.setValue(BotConfig.OPENAI_API_KEY.get());
        this.openaiKeyField.setHint(Component.literal("sk-proj-..."));
        this.addRenderableWidget(this.openaiKeyField);

        // Gemini
        this.geminiKeyField = new EditBox(this.font, fieldX, START_Y + SPACING * 3, fieldW, FIELD_H,
                Component.literal("Gemini API Key"));
        this.geminiKeyField.setMaxLength(512);
        this.geminiKeyField.setValue(BotConfig.GEMINI_API_KEY.get());
        this.geminiKeyField.setHint(Component.literal("AIza..."));
        this.addRenderableWidget(this.geminiKeyField);

        // Claude
        this.claudeKeyField = new EditBox(this.font, fieldX, START_Y + SPACING * 4, fieldW, FIELD_H,
                Component.literal("Claude API Key"));
        this.claudeKeyField.setMaxLength(512);
        this.claudeKeyField.setValue(BotConfig.CLAUDE_API_KEY.get());
        this.claudeKeyField.setHint(Component.literal("sk-ant-..."));
        this.addRenderableWidget(this.claudeKeyField);

        // Ollama
        this.ollamaUrlField = new EditBox(this.font, fieldX, START_Y + SPACING * 5, fieldW, FIELD_H,
                Component.literal("Ollama URL"));
        this.ollamaUrlField.setMaxLength(256);
        this.ollamaUrlField.setValue(BotConfig.OLLAMA_URL.get());
        this.ollamaUrlField.setHint(Component.literal("http://localhost:11434"));
        this.addRenderableWidget(this.ollamaUrlField);

        // ── Save / Cancel ──────────────────────────────────────────────────────
        int btnY = START_Y + SPACING * 6 + 10;
        this.addRenderableWidget(
            Button.builder(Component.literal("Save"), btn -> this.saveAndClose())
                  .bounds(this.width / 2 - 155, btnY, 150, 20).build()
        );
        this.addRenderableWidget(
            Button.builder(Component.literal("Cancel"), btn -> this.onClose())
                  .bounds(this.width / 2 + 5, btnY, 150, 20).build()
        );
    }

    private void saveAndClose() {
        saveConfig();
        this.minecraft.setScreen(this.parent);
    }

    private void saveConfig() {
        BotConfig.LLM_PROVIDER.set(this.providerField.getValue().toLowerCase().trim());
        BotConfig.GROK_API_KEY.set(this.grokKeyField.getValue());
        BotConfig.OPENAI_API_KEY.set(this.openaiKeyField.getValue());
        BotConfig.GEMINI_API_KEY.set(this.geminiKeyField.getValue());
        BotConfig.CLAUDE_API_KEY.set(this.claudeKeyField.getValue());
        BotConfig.OLLAMA_URL.set(this.ollamaUrlField.getValue());
        BotConfig.SPEC.save();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
            "API keys are saved to the config file. Changes take effect immediately.",
            this.width / 2, this.height - 25, 0x808080);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
