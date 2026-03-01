package com.shayneomac08.automated_minecraft_bots.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.shayneomac08.automated_minecraft_bots.bot.BotBrain;
import com.shayneomac08.automated_minecraft_bots.bot.BotPair;
import com.shayneomac08.automated_minecraft_bots.bot.BotRegistry;
import com.shayneomac08.automated_minecraft_bots.llm.LLMProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal command set for FakePlayer-based bots
 * Many advanced commands removed - will be rebuilt later
 */
public final class AmbCommands {
    private AmbCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                Commands.literal("amb")

                        .then(Commands.literal("ping")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("AMB: pong ✅ (FakePlayer mode)"), false);
                                    return 1;
                                })
                        )

                        // /amb spawn <name> [llm]
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> spawnBot(ctx, null))
                                        .then(Commands.argument("llm", StringArgumentType.word())
                                                .executes(ctx -> spawnBot(ctx, StringArgumentType.getString(ctx, "llm")))
                                        )
                                )
                        )

                        // /amb spawnmulti <count> <llm> [group]
                        .then(Commands.literal("spawnmulti")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 30))
                                        .then(Commands.argument("llm", StringArgumentType.word())
                                                .executes(ctx -> spawnMultiBots(ctx, null))
                                                .then(Commands.argument("group", StringArgumentType.word())
                                                        .executes(ctx -> spawnMultiBots(ctx, StringArgumentType.getString(ctx, "group")))
                                                )
                                        )
                                )
                        )

                        // /amb remove <name>
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String keyName = normalize(name);

                                            BotPair pair = BotRegistry.get(keyName);
                                            if (pair == null) {
                                                ctx.getSource().sendFailure(Component.literal("[AMB] No bot named " + name));
                                                return 0;
                                            }

                                            // Remove the entity from the world
                                            if (pair.body() != null && !pair.body().isRemoved()) {
                                                pair.body().discard();
                                            }

                                            // Remove from registry
                                            BotRegistry.remove(keyName);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[AMB] Removed bot: " + name), true);
                                            System.out.println("[AMB] Bot removed: " + name);
                                            return 1;
                                        })
                                )
                        )

                        // /amb where <name>
                        .then(Commands.literal("where")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String keyName = normalize(name);

                                            BotPair pair = BotRegistry.get(keyName);
                                            if (pair == null) {
                                                ctx.getSource().sendFailure(Component.literal("[AMB] No bot named " + name));
                                                return 0;
                                            }

                                            if (pair.body() == null || pair.body().isRemoved()) {
                                                ctx.getSource().sendFailure(Component.literal("[AMB] Bot body is missing"));
                                                return 0;
                                            }

                                            var pos = pair.body().blockPosition();
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "[AMB] " + name + " is at " + pos.toShortString()
                                            ), false);
                                            return 1;
                                        })
                                )
                        )

                        // /amb list
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    var allNames = BotRegistry.getAllBotNames();
                                    int count = 0;
                                    StringBuilder sb = new StringBuilder("[AMB] Bots:\n");

                                    for (String key : allNames) {
                                        BotPair pair = BotRegistry.get(key);
                                        if (pair != null) {
                                            count++;
                                            String status = (pair.body() != null && !pair.body().isRemoved()) ? "✓" : "✗";
                                            sb.append("  ").append(status).append(" ").append(key);

                                            if (pair.body() instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
                                                sb.append(" [").append(ambBot.getCurrentTask()).append("]");
                                            }
                                            sb.append("\n");
                                        }
                                    }

                                    if (count == 0) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("[AMB] No bots spawned"), false);
                                    } else {
                                        String finalMsg = "[AMB] Bots (" + count + "):\n" + sb.toString();
                                        ctx.getSource().sendSuccess(() -> Component.literal(finalMsg), false);
                                    }
                                    return 1;
                                })
                        )

                        // /amb task <name> <task>
                        .then(Commands.literal("task")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("task", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String task = StringArgumentType.getString(ctx, "task");
                                                    String keyName = normalize(name);

                                                    BotPair pair = BotRegistry.get(keyName);
                                                    if (pair == null) {
                                                        ctx.getSource().sendFailure(Component.literal("[AMB] No bot named " + name));
                                                        return 0;
                                                    }

                                                    if (pair.body() instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
                                                        ambBot.setTask(task);
                                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                                "[AMB] " + name + " task set to: " + task
                                                        ), false);
                                                        return 1;
                                                    }

                                                    ctx.getSource().sendFailure(Component.literal("[AMB] Bot is not a FakePlayer entity"));
                                                    return 0;
                                                })
                                        )
                                )
                        )

                        // /amb gui <name>
                        .then(Commands.literal("gui")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String keyName = normalize(name);

                                            BotPair pair = BotRegistry.get(keyName);
                                            if (pair == null) {
                                                ctx.getSource().sendFailure(Component.literal("[AMB] No bot named " + name));
                                                return 0;
                                            }

                                            Player player = ctx.getSource().getPlayer();
                                            if (!(player instanceof ServerPlayer serverPlayer)) {
                                                ctx.getSource().sendFailure(Component.literal("[AMB] Must be a player"));
                                                return 0;
                                            }

                                            if (pair.body() instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
                                                ambBot.openGui(serverPlayer);
                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "[AMB] Opening " + name + "'s inventory"
                                                ), false);
                                                return 1;
                                            }

                                            ctx.getSource().sendFailure(Component.literal("[AMB] Bot is not a FakePlayer entity"));
                                            return 0;
                                        })
                                )
                        )

                        // /amb brain <name> on|off|status
                        .then(Commands.literal("brain")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.literal("on")
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String keyName = normalize(name);

                                                    BotPair pair = BotRegistry.get(keyName);
                                                    if (pair == null) {
                                                        ctx.getSource().sendFailure(Component.literal("[AMB] No bot named " + name));
                                                        return 0;
                                                    }

                                                    if (pair.body() instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
                                                        ambBot.setBrainEnabled(true);
                                                        BotBrain.setAutonomous(keyName, true);
                                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                                "[AMB] " + name + " brain ON"
                                                        ), false);
                                                        return 1;
                                                    }

                                                    ctx.getSource().sendFailure(Component.literal("[AMB] Bot entity not found"));
                                                    return 0;
                                                })
                                        )
                                        .then(Commands.literal("off")
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String keyName = normalize(name);

                                                    BotPair pair = BotRegistry.get(keyName);
                                                    if (pair == null) {
                                                        ctx.getSource().sendFailure(Component.literal("[AMB] No bot named " + name));
                                                        return 0;
                                                    }

                                                    if (pair.body() instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
                                                        ambBot.setBrainEnabled(false);
                                                        BotBrain.setAutonomous(keyName, false);
                                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                                "[AMB] " + name + " brain OFF"
                                                        ), false);
                                                        return 1;
                                                    }

                                                    ctx.getSource().sendFailure(Component.literal("[AMB] Bot entity not found"));
                                                    return 0;
                                                })
                                        )
                                        .then(Commands.literal("status")
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String keyName = normalize(name);

                                                    BotPair pair = BotRegistry.get(keyName);
                                                    if (pair == null) {
                                                        ctx.getSource().sendFailure(Component.literal("[AMB] No bot named " + name));
                                                        return 0;
                                                    }

                                                    if (pair.body() instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
                                                        boolean isActive = ambBot.isBrainEnabled();
                                                        String status = isActive ? "ON ✓" : "OFF ✗";
                                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                                "[AMB] " + name + " brain: " + status
                                                        ), false);
                                                        return 1;
                                                    }

                                                    ctx.getSource().sendFailure(Component.literal("[AMB] Bot entity not found"));
                                                    return 0;
                                                })
                                        )
                                )
                        )

                        // /amb give <name> <item> [count]
                        .then(Commands.literal("give")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("item", ItemArgument.item(context))
                                                .executes(ctx -> giveItemToBot(ctx, 1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> giveItemToBot(ctx, IntegerArgumentType.getInteger(ctx, "count")))
                                                )
                                        )
                                )
                        )
        );
    }

    // ==================== HELPER METHODS ====================

    private static int spawnBot(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String llmArg) {
        try {
            String name = StringArgumentType.getString(ctx, "name");
            String keyName = normalize(name);

            CommandSourceStack src = ctx.getSource();
            Player player = src.getPlayer();
            if (player == null) {
                src.sendFailure(Component.literal("[AMB] Must be a player"));
                return 0;
            }

            ServerLevel level = (ServerLevel) player.level();

            // Determine LLM provider
            LLMProvider provider;
            String llmGroup;
            if (llmArg == null || llmArg.isEmpty()) {
                provider = LLMProvider.OPENAI;
                llmGroup = "openai";
            } else {
                provider = LLMProvider.fromString(llmArg);
                llmGroup = llmArg;
            }

            // Remove old bot if present
            BotPair old = BotRegistry.remove(keyName);
            if (old != null && old.body() != null && !old.body().isRemoved()) {
                old.body().discard();
            }

            // Spawn FakePlayer-based bot
            com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot =
                com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity.spawnAtPlayer(
                    (ServerPlayer) player,
                    name,
                    llmGroup
                );

            if (ambBot == null) {
                src.sendFailure(Component.literal("[AMB] Failed to spawn bot"));
                return 0;
            }

            // Force brain ON
            ambBot.setBrainEnabled(true);
            BotBrain.setAutonomous(keyName, true);

            // Match rotation
            ambBot.setYRot(player.getYRot());
            ambBot.setYHeadRot(player.getYRot());

            // Register bot (bot IS the FakePlayer, so use it for both hands and body)
            BotRegistry.put(keyName, new BotPair(ambBot, ambBot));

            // Set LLM provider
            BotBrain.setLLMProvider(keyName, provider);

            src.sendSuccess(() -> Component.literal(
                    "[AMB] Spawned " + name + " (" + llmGroup + ") - FakePlayer mode"
            ), true);

            System.out.println("[AMB] Spawned FakePlayer bot: " + name + " (LLM: " + llmGroup + ")");
            return 1;

        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[AMB] Error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int spawnMultiBots(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String groupArg) {
        try {
            int count = IntegerArgumentType.getInteger(ctx, "count");
            String llm = StringArgumentType.getString(ctx, "llm");
            String group = (groupArg != null) ? groupArg : llm;

            CommandSourceStack src = ctx.getSource();
            Player player = src.getPlayer();
            if (player == null) {
                src.sendFailure(Component.literal("[AMB] Must be a player"));
                return 0;
            }

            ServerLevel level = (ServerLevel) player.level();
            LLMProvider provider = LLMProvider.fromString(llm);
            int spawnedCount = 0;

            for (int i = 1; i <= count; i++) {
                String name = group + "_" + i;
                String keyName = normalize(name);

                // Remove old bot if exists
                BotPair old = BotRegistry.remove(keyName);
                if (old != null && old.body() != null && !old.body().isRemoved()) {
                    old.body().discard();
                }

                // Spawn new bot
                com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot =
                    com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity.spawnAtPlayer(
                        (ServerPlayer) player,
                        name,
                        group
                    );

                if (ambBot != null) {
                    ambBot.setBrainEnabled(true);
                    BotBrain.setAutonomous(keyName, true);
                    ambBot.setYRot(player.getYRot());
                    ambBot.setYHeadRot(player.getYRot());
                    BotRegistry.put(keyName, new BotPair(ambBot, ambBot));
                    BotBrain.setLLMProvider(keyName, provider);
                    spawnedCount++;
                }
            }

            final int finalCount = spawnedCount;
            src.sendSuccess(() -> Component.literal(
                    "[AMB] Spawned " + finalCount + " bots in group '" + group + "' (FakePlayer mode)"
            ), true);

            return spawnedCount;

        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[AMB] Error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int giveItemToBot(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int count) {
        try {
            String name = StringArgumentType.getString(ctx, "name");
            String keyName = normalize(name);

            BotPair pair = BotRegistry.get(keyName);
            if (pair == null) {
                ctx.getSource().sendFailure(Component.literal("[AMB] No bot named " + name));
                return 0;
            }

            if (!(pair.body() instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity bot)) {
                ctx.getSource().sendFailure(Component.literal("[AMB] Bot is not a FakePlayer entity"));
                return 0;
            }

            // Get the item from the argument
            var itemInput = ItemArgument.getItem(ctx, "item");
            ItemStack stack = new ItemStack(itemInput.getItem(), count);

            // Add to bot's inventory
            bot.getInventory().add(stack);

            String itemName = stack.getDisplayName().getString();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[AMB] Gave " + count + " " + itemName + " to " + name
            ), true);

            return 1;

        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("[AMB] Error: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static String normalize(String name) {
        return name.toLowerCase().trim();
    }
}
