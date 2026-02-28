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
        // Create GameProfile for the FakePlayer
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);

        // Create FakePlayer bot
        AmbNpcEntity body = new AmbNpcEntity(level, profile);

        body.setPos(x, y, z);
        body.setCustomName(Component.literal(name));
        body.setCustomNameVisible(true);

        level.addFreshEntity(body);
        return body;
    }
}
