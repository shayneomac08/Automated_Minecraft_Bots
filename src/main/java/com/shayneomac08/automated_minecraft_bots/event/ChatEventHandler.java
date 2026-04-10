package com.shayneomac08.automated_minecraft_bots.event;

import com.shayneomac08.automated_minecraft_bots.bot.BotBrain;
import com.shayneomac08.automated_minecraft_bots.bot.BotPair;
import com.shayneomac08.automated_minecraft_bots.bot.BotRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles chat events to allow bots to read and respond to messages
 */
public class ChatEventHandler {

    // Pattern to detect bot commands: "botname, do something" or "@botname do something"
    private static final Pattern BOT_COMMAND_PATTERN = Pattern.compile("^[@]?([a-zA-Z0-9_]+)[,:]?\\s+(.+)$");

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString();
        String playerName = player.getName().getString();

        // Add message to all bots' chat history
        for (String botName : BotRegistry.getAllBotNames()) {
            BotBrain.addChatMessage(botName, playerName, message);
        }

        // Check if this is a command directed at a specific bot
        Matcher matcher = BOT_COMMAND_PATTERN.matcher(message);
        if (matcher.matches()) {
            String targetBotName = matcher.group(1).toLowerCase().trim();
            String command = matcher.group(2);

            // Check if this bot exists
            BotPair pair = BotRegistry.get(targetBotName);
            if (pair != null && pair.body() != null && !pair.body().isRemoved()) {

                // Classify message BEFORE async call so the classification is on the caller thread
                boolean conversational = isConversational(command);

                // Snapshot the active task for logging (pair.body() is AmbNpcEntity in our setup)
                final String activeTask = (pair.body() instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot2)
                    ? ambBot2.getCurrentTask() : "unknown";
                System.out.println("[AMB-CHAT] " + targetBotName
                    + " ← " + playerName
                    + ": type=" + (conversational ? "conversational" : "command")
                    + " activeTask=" + activeTask
                    + " text=\"" + command + "\"");

                // Process the LLM reply asynchronously — always queue a response
                BotBrain.processChatCommand(targetBotName, playerName, command, pair)
                    .thenAccept(willObey -> {
                        if (conversational) {
                            // Side-band: reply queued, active task preserved intact
                            System.out.println("[AMB-CHAT] " + targetBotName
                                + " conversational reply queued — task " + activeTask + " NOT interrupted");
                        } else if (willObey) {
                            System.out.println("[AMB-CHAT] " + targetBotName
                                + " command obey=true — interrupting task " + activeTask);
                            BotBrain.interruptWithCommand(targetBotName, playerName, command);
                        } else {
                            System.out.println("[AMB-CHAT] " + targetBotName
                                + " command obey=false — task " + activeTask + " preserved");
                        }
                    });
            }
        }
    }

    /**
     * Returns true if the message looks like casual conversation (question, small talk,
     * social reaction) rather than an explicit task directive.
     * Conversational messages get a reply queued but do NOT interrupt the active task.
     */
    private static boolean isConversational(String message) {
        String lower = message.trim().toLowerCase();

        // Anything ending with "?" is a question → conversational
        if (lower.endsWith("?")) return true;

        // Starts with common question words or auxiliary verbs used as question openers
        String[] questionStarters = {
            "what ", "how ", "why ", "where ", "who ", "when ",
            "is ", "are ", "was ", "were ", "do ", "does ", "did ",
            "can ", "could ", "would ", "should ", "have ", "has ",
            "tell me", "what's", "how's", "who are", "are you", "do you",
            "did you", "have you", "will you", "would you"
        };
        for (String q : questionStarters) {
            if (lower.startsWith(q)) return true;
        }

        // Pure social one-word or short reactions (exact match or leading word)
        String[] social = {
            "hi", "hello", "hey", "sup", "yo",
            "lol", "lmao", "haha", "xd", "rofl",
            "nice", "cool", "wow", "great", "awesome",
            "ok", "okay", "alright", "sure",
            "thanks", "thank you", "ty",
            "good", "bad", "hmm", "hm",
            "interesting", "really", "oh", "ah",
            "bye", "cya", "see ya", "good luck",
            "nope", "yep", "yes", "no", "nah"
        };
        for (String s : social) {
            if (lower.equals(s) || lower.startsWith(s + " ") || lower.startsWith(s + "!") || lower.startsWith(s + ",")) return true;
        }

        return false;
    }

    /**
     * Tick handler to send pending bot chat messages
     * This should be called from the main tick event
     */
    public static void tickBotChat(net.minecraft.server.MinecraftServer server) {
        for (String botName : BotRegistry.getAllBotNames()) {
            BotBrain.State state = BotBrain.stateForName(botName);

            // Throttle chat messages (max 1 per 2 seconds)
            int currentTick = server.getTickCount();
            if (currentTick - state.lastChatTick < 40) {
                continue;
            }

            String message = BotBrain.getNextChatMessage(botName);
            if (message != null && !message.isEmpty()) {
                // Send chat message as the bot
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("<" + botName + "> " + message),
                    false
                );
                state.lastChatTick = currentTick;
            }
        }
    }
}
