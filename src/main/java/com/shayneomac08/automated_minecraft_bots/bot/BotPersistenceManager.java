package com.shayneomac08.automated_minecraft_bots.bot;

import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import com.shayneomac08.automated_minecraft_bots.llm.LLMProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Saves and restores bot state (identity, position, task, LLM, inventory, recipes)
 * across server restarts.
 *
 * Save model: bots log off on shutdown, log back in on startup.
 *
 * Registry: <worldDir>/playerdata/ + amb_bots.txt — one line per bot: name|llm|x|y|z|task|brain
 * Per-bot data: <worldDir>/playerdata/<uuid>.dat — via standard PlayerDataStorage.save()
 *
 * The deterministic UUID in AmbNpcEntity's constructor (nameUUIDFromBytes) ensures each bot
 * always maps to the same .dat file across restarts, preserving inventory and recipe book.
 */
public final class BotPersistenceManager {
    private BotPersistenceManager() {}

    private static final String REGISTRY_FILE = "amb_bots.txt";

    /**
     * Deferred load entries: populated during onServerStarted(), consumed by BotTicker on first
     * live tick to avoid entity-not-yet-ticking race conditions.
     */
    public record PendingRestore(String botName, CompoundTag nbt, double x, double y, double z) {}
    public static final ConcurrentLinkedQueue<PendingRestore> PENDING_RESTORES = new ConcurrentLinkedQueue<>();

    // ── Save ──────────────────────────────────────────────────────────────────

    public static void onServerStopping(MinecraftServer server) {
        System.out.println("[AMB-PERSIST] Server stopping — saving all bot state");
        Path worldRoot = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);

        StringBuilder registry = new StringBuilder();
        int saved = 0;

        for (String name : BotRegistry.getAllBotNames()) {
            BotPair pair = BotRegistry.get(name);
            if (pair == null) continue;
            if (!(pair.body() instanceof AmbNpcEntity ambBot)) continue;
            if (ambBot.isRemoved()) continue;

            // Save inventory, recipe book, health etc. via PlayerDataStorage (handles ValueOutput bridge)
            try {
                server.getPlayerList().getPlayerIo().save(ambBot);
                System.out.printf("[AMB-PERSIST] %s: player data saved (uuid=%s)%n",
                    name, ambBot.getStringUUID());
            } catch (Exception e) {
                System.err.printf("[AMB-PERSIST] %s: player data save failed: %s%n", name, e.getMessage());
            }

            // Registry line: name|llm|x|y|z|task|brainEnabled
            BotBrain.State st = BotBrain.stateForName(name);
            var pos = ambBot.position();
            registry.append(name).append('|')
                    .append(st.llmProvider.getId()).append('|')
                    .append(String.format("%.3f", pos.x)).append('|')
                    .append(String.format("%.3f", pos.y)).append('|')
                    .append(String.format("%.3f", pos.z)).append('|')
                    .append(ambBot.getCurrentTask()).append('|')
                    .append(ambBot.isBrainEnabled()).append('\n');

            System.out.printf("[AMB-PERSIST] %s: saved at (%.1f %.1f %.1f) task=%s%n",
                name, pos.x, pos.y, pos.z, ambBot.getCurrentTask());
            saved++;
        }

        try {
            Files.writeString(worldRoot.resolve(REGISTRY_FILE), registry.toString());
            System.out.printf("[AMB-PERSIST] Registry written: %d bot(s)%n", saved);
        } catch (IOException e) {
            System.err.println("[AMB-PERSIST] Registry write failed: " + e.getMessage());
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public static void onServerStarted(MinecraftServer server) {
        Path worldRoot = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path registryPath = worldRoot.resolve(REGISTRY_FILE);

        if (!Files.exists(registryPath)) {
            System.out.println("[AMB-PERSIST] No saved registry at " + registryPath + " — fresh start");
            return;
        }

        System.out.println("[AMB-PERSIST] Loading saved bots from " + registryPath);
        List<String> lines;
        try {
            lines = Files.readAllLines(registryPath);
        } catch (IOException e) {
            System.err.println("[AMB-PERSIST] Failed to read registry: " + e.getMessage());
            return;
        }

        int loaded = 0;
        ServerLevel overworld = server.overworld();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\\|");
            if (parts.length < 7) {
                System.err.println("[AMB-PERSIST] Malformed line (expected 7 fields): " + line);
                continue;
            }

            String name      = parts[0].trim();
            String llmId     = parts[1].trim();
            double x, y, z;
            try {
                x = Double.parseDouble(parts[2]);
                y = Double.parseDouble(parts[3]);
                z = Double.parseDouble(parts[4]);
            } catch (NumberFormatException e) {
                System.err.printf("[AMB-PERSIST] %s: bad position — skipping%n", name);
                continue;
            }
            String  task         = parts[5].trim();
            boolean brainEnabled = Boolean.parseBoolean(parts[6].trim());

            // Spawn fresh entity (UUID is deterministic from name — matches the saved .dat file)
            AmbNpcEntity ambBot = AvatarFactory.spawn(overworld, name, x, y, z);
            if (ambBot == null) {
                System.err.printf("[AMB-PERSIST] %s: spawn failed — skipping%n", name);
                continue;
            }

            // Restore task / brain / LLM
            ambBot.setTask(task);
            ambBot.setBrainEnabled(brainEnabled);
            String keyName = name.toLowerCase().trim();
            LLMProvider provider = LLMProvider.fromString(llmId);
            BotBrain.setLLMProvider(keyName, provider);
            BotBrain.setAutonomous(keyName, brainEnabled);
            BotRegistry.put(keyName, new BotPair(ambBot, ambBot));

            // Load saved player data (.dat file) and queue deferred NBT restore
            try {
                Optional<CompoundTag> savedData = server.getPlayerList()
                    .loadPlayerData(new NameAndId(ambBot.getGameProfile()));
                if (savedData.isPresent()) {
                    PENDING_RESTORES.add(new PendingRestore(keyName, savedData.get(), x, y, z));
                    System.out.printf("[AMB-PERSIST] %s: saved data found — queued for deferred restore%n", name);
                } else {
                    System.out.printf("[AMB-PERSIST] %s: no saved data — starting fresh inventory%n", name);
                }
            } catch (Exception e) {
                System.err.printf("[AMB-PERSIST] %s: data load error: %s%n", name, e.getMessage());
            }

            System.out.printf("[AMB-PERSIST] %s: respawned at (%.1f %.1f %.1f) task=%s llm=%s brain=%b%n",
                name, x, y, z, task, llmId, brainEnabled);
            loaded++;
        }

        System.out.printf("[AMB-PERSIST] Restore pass complete: %d bot(s) spawned, %d restore(s) pending%n",
            loaded, PENDING_RESTORES.size());
    }

    // ── Apply deferred NBT restore (called from BotTicker on first live tick) ──

    /**
     * Applies a saved CompoundTag to a live bot entity.
     * Uses TagValueInput to bridge the CompoundTag → ValueInput required by Entity.load().
     * Position is re-applied after load to prevent save-position drift.
     */
    public static void applyPendingRestore(AmbNpcEntity ambBot, PendingRestore restore) {
        try {
            ValueInput vi = TagValueInput.create(
                ProblemReporter.DISCARDING,
                ambBot.registryAccess(),
                restore.nbt()
            );
            ambBot.load(vi);
            ambBot.setPos(restore.x(), restore.y(), restore.z());
            System.out.printf("[AMB-PERSIST] %s: NBT restore applied (inventory + recipes) at (%.1f %.1f %.1f)%n",
                restore.botName(), restore.x(), restore.y(), restore.z());
        } catch (Exception e) {
            System.err.printf("[AMB-PERSIST] %s: NBT restore failed: %s%n", restore.botName(), e.getMessage());
        }
    }
}
