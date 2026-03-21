package com.shayneomac08.automated_minecraft_bots.bot;

import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcVisualEntity;
import com.shayneomac08.automated_minecraft_bots.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Factory for creating bot entities.
 *
 * Architecture:
 * - AmbNpcEntity (FakePlayer) is SERVER-ONLY — clients never see it.
 *   Do NOT send ClientboundPlayerInfoUpdatePacket; that makes the FakePlayer visible
 *   and creates a second overlapping body alongside AmbNpcVisualEntity.
 * - AmbNpcVisualEntity (PathfinderMob) is the SOLE visible body on the client.
 *   It mirrors position/rotation/items from AmbNpcEntity every tick.
 * - Skin variant is set from UUID on the visual entity after both are added to the world.
 *
 * This factory mirrors the logic in AmbNpcEntity.spawnAtPlayer() to ensure the respawn
 * path produces identical embodiment structure to the command-spawn path.
 */
public final class AvatarFactory {
    private AvatarFactory() {}

    public static AmbNpcEntity spawn(ServerLevel level, String name, double x, double y, double z) {
        // Logic entity (FakePlayer) — server-only, never sent as a player to clients
        AmbNpcEntity body = new AmbNpcEntity(level, name);
        body.setPos(x, y, z);
        body.setCustomName(Component.literal(name));
        body.setCustomNameVisible(true);
        body.setInvisible(false);

        // IMPORTANT: Do NOT send ClientboundPlayerInfoUpdatePacket here.
        // Sending that packet makes the FakePlayer render as a player on the client,
        // creating a second overlapping Steve body on top of AmbNpcVisualEntity.
        // AmbNpcVisualEntity is the sole visible authority for rendering.
        level.addFreshEntity(body);

        // Visual entity — the only body clients see
        AmbNpcVisualEntity visual = new AmbNpcVisualEntity(
            ModEntities.AMB_NPC_VISUAL.get(), level, body);
        visual.setPos(x, y, z);
        level.addFreshEntity(visual);
        body.visualEntity = visual;

        // Assign randomized skin from UUID (UUID is stable after addFreshEntity)
        visual.initSkinFromUUID();

        System.out.printf("[AMB-SPAWN] AvatarFactory: %s spawned — logicId=%d visualId=%d skinVariant=%d%n",
            name, body.getId(), visual.getId(), visual.getSkinVariant());

        return body;
    }
}
