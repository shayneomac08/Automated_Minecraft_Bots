package com.shayneomac08.automated_minecraft_bots.client;

import com.shayneomac08.automated_minecraft_bots.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
    private final Screen parent;

    // API Key text fields (wider for full keys)
    private EditBox openaiKeyField;
    private EditBox openaiModelField;
    private EditBox geminiKeyField;
    private EditBox geminiModelField;
    private EditBox grokKeyField;
    private EditBox grokModelField;
    private EditBox defaultLlmField;

    // Scroll offset for when content exceeds screen height (unused - kept for future scrolling feature)
    // private final int scrollOffset = 0;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Automated Minecraft Bots - Configuration"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = Math.min(400, this.width - 40); // Wide fields for API keys
        int fieldHeight = 20;
        int leftMargin = (this.width - fieldWidth) / 2;
        int startY = 40;
        int spacing = 30;

        // OpenAI API Key
        this.openaiKeyField = new EditBox(this.font, leftMargin, startY, fieldWidth, fieldHeight,
            Component.literal("OpenAI API Key"));
        this.openaiKeyField.setMaxLength(512);
        this.openaiKeyField.setValue(Config.OPENAI_KEY.get());
        this.openaiKeyField.setHint(Component.literal("sk-proj-..."));
        this.addRenderableWidget(this.openaiKeyField);

        // OpenAI Model
        this.openaiModelField = new EditBox(this.font, leftMargin, startY + spacing, fieldWidth, fieldHeight,
            Component.literal("OpenAI Model"));
        this.openaiModelField.setMaxLength(100);
        this.openaiModelField.setValue(Config.OPENAI_MODEL.get());
        this.openaiModelField.setHint(Component.literal("gpt-4o"));
        this.addRenderableWidget(this.openaiModelField);

        // Gemini API Key
        this.geminiKeyField = new EditBox(this.font, leftMargin, startY + spacing * 2, fieldWidth, fieldHeight,
            Component.literal("Gemini API Key"));
        this.geminiKeyField.setMaxLength(512);
        this.geminiKeyField.setValue(Config.GEMINI_KEY.get());
        this.geminiKeyField.setHint(Component.literal("AIza..."));
        this.addRenderableWidget(this.geminiKeyField);

        // Gemini Model
        this.geminiModelField = new EditBox(this.font, leftMargin, startY + spacing * 3, fieldWidth, fieldHeight,
            Component.literal("Gemini Model"));
        this.geminiModelField.setMaxLength(100);
        this.geminiModelField.setValue(Config.GEMINI_MODEL.get());
        this.geminiModelField.setHint(Component.literal("gemini-2.0-flash-exp"));
        this.addRenderableWidget(this.geminiModelField);

        // Grok API Key
        this.grokKeyField = new EditBox(this.font, leftMargin, startY + spacing * 4, fieldWidth, fieldHeight,
            Component.literal("Grok API Key"));
        this.grokKeyField.setMaxLength(512);
        this.grokKeyField.setValue(Config.GROK_KEY.get());
        this.grokKeyField.setHint(Component.literal("xai-..."));
        this.addRenderableWidget(this.grokKeyField);

        // Grok Model
        this.grokModelField = new EditBox(this.font, leftMargin, startY + spacing * 5, fieldWidth, fieldHeight,
            Component.literal("Grok Model"));
        this.grokModelField.setMaxLength(100);
        this.grokModelField.setValue(Config.GROK_MODEL.get());
        this.grokModelField.setHint(Component.literal("grok-beta"));
        this.addRenderableWidget(this.grokModelField);

        // Default LLM
        this.defaultLlmField = new EditBox(this.font, leftMargin, startY + spacing * 6, fieldWidth, fieldHeight,
            Component.literal("Default LLM"));
        this.defaultLlmField.setMaxLength(20);
        this.defaultLlmField.setValue(Config.DEFAULT_LLM.get());
        this.defaultLlmField.setHint(Component.literal("openai, gemini, or grok"));
        this.addRenderableWidget(this.defaultLlmField);

        // Save button
        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.saveAndClose())
            .bounds(this.width / 2 - 155, startY + spacing * 7 + 10, 150, 20).build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
            .bounds(this.width / 2 + 5, startY + spacing * 7 + 10, 150, 20).build());
    }

    private void saveAndClose() {
        this.saveConfig();
        this.minecraft.setScreen(this.parent);
    }

    private void saveConfig() {
        // Save all values to config
        Config.OPENAI_KEY.set(this.openaiKeyField.getValue());
        Config.OPENAI_MODEL.set(this.openaiModelField.getValue());
        Config.GEMINI_KEY.set(this.geminiKeyField.getValue());
        Config.GEMINI_MODEL.set(this.geminiModelField.getValue());
        Config.GROK_KEY.set(this.grokKeyField.getValue());
        Config.GROK_MODEL.set(this.grokModelField.getValue());

        String defaultLlm = this.defaultLlmField.getValue().toLowerCase();
        if (defaultLlm.equals("openai") || defaultLlm.equals("gemini") || defaultLlm.equals("grok")) {
            Config.DEFAULT_LLM.set(defaultLlm);
        }

        // Save to disk
        Config.SPEC.save();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render a simple transparent background without blur to avoid the "Can only blur once per frame" error
        graphics.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render background first
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        // Render all widgets (buttons and text fields) BEFORE labels so labels appear on top
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        // Render labels for each field (AFTER widgets so they're visible)
        int fieldWidth = Math.min(400, this.width - 40);
        int leftMargin = (this.width - fieldWidth) / 2;
        int startY = 40;
        int spacing = 30;

        graphics.drawString(this.font, "OpenAI API Key:", leftMargin, startY - 12, 0xFFFFFF);
        graphics.drawString(this.font, "OpenAI Model:", leftMargin, startY + spacing - 12, 0xFFFFFF);
        graphics.drawString(this.font, "Gemini API Key:", leftMargin, startY + spacing * 2 - 12, 0xFFFFFF);
        graphics.drawString(this.font, "Gemini Model:", leftMargin, startY + spacing * 3 - 12, 0xFFFFFF);
        graphics.drawString(this.font, "Grok API Key:", leftMargin, startY + spacing * 4 - 12, 0xFFFFFF);
        graphics.drawString(this.font, "Grok Model:", leftMargin, startY + spacing * 5 - 12, 0xFFFFFF);
        graphics.drawString(this.font, "Default LLM (openai/gemini/grok):", leftMargin, startY + spacing * 6 - 12, 0xFFFFFF);

        // Render help text at bottom
        String helpText = "API keys support up to 512 characters. Changes are saved to config file.";
        graphics.drawCenteredString(this.font, helpText, this.width / 2, this.height - 25, 0x808080);
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
