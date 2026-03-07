package com.shayneomac08.automated_minecraft_bots.bot;

import com.mojang.authlib.GameProfile;
import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Factory for creating FakePlayer-based bot entities
 */
public final class AvatarFactory {
    private AvatarFactory() {}

    public static AmbNpcEntity spawn(ServerLevel level, String name, double x, double y, double z) {
        // Create FakePlayer bot with new simplified constructor
        AmbNpcEntity body = new AmbNpcEntity(level, name);

        body.setPos(x, y, z);
        body.setCustomName(Component.literal(name));
        body.setCustomNameVisible(true);
        body.setInvisible(false);

        // CRITICAL: Send ClientboundPlayerInfoUpdatePacket (ADD_PLAYER) to all connected clients
        // BEFORE adding the entity to the world. Without this, the client receives
        // ClientboundAddEntityPacket for a UUID it has no profile data for, and renders
        // the player entity as invisible. This affects both initial spawn and respawn.
        ClientboundPlayerInfoUpdatePacket infoPacket = new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
            ),
            List.of(body)
        );
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            sp.connection.send(infoPacket);
        }

        // FakePlayers must be added to the entity list to be visible
        level.addFreshEntity(body);

        return body;
    }
}
