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
                // Process the command asynchronously
                BotBrain.processChatCommand(targetBotName, playerName, command, pair)
                    .thenAccept(willObey -> {
                        if (willObey) {
                            System.out.println("[AMB] Bot " + targetBotName + " will obey command from " + playerName);
                            // TODO: Execute the actual command action
                        } else {
                            System.out.println("[AMB] Bot " + targetBotName + " chose to ignore command from " + playerName);
                        }
                    });
            }
        }
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
