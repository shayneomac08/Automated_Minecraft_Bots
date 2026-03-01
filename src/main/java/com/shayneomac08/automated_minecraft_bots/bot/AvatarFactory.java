package com.shayneomac08.automated_minecraft_bots.bot;

import com.mojang.authlib.GameProfile;
import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

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

        // FakePlayers must be added to the entity list to be visible
        level.addFreshEntity(body);

        // Ensure the bot is visible as a normal player
        body.setInvisible(false);

        return body;
    }
}
