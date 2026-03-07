package com.shayneomac08.automated_minecraft_bots.bot;

/**
 * A single step in a bot's queued multi-step plan.
 * When the LLM returns a plan with multiple steps, each step becomes a BotSubGoal.
 * The bot works through the queue in order, only calling the LLM again when empty.
 */
public record BotSubGoal(String task, int durationTicks, String chatMessage) {

    /** Convenience constructor: converts minutes to ticks (20 ticks/sec × 60 sec/min). */
    public BotSubGoal(String task, int minutes) {
        this(task, Math.max(200, minutes * 1200), null);
    }
}
