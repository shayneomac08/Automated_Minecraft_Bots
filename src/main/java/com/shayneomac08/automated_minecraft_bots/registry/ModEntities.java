package com.shayneomac08.automated_minecraft_bots.registry;

import com.shayneomac08.automated_minecraft_bots.AutomatedMinecraftBots;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity registration - NOT NEEDED for FakePlayer-based bots
 * FakePlayer entities are automatically rendered by Minecraft's player rendering system
 * They don't need custom EntityType registration or renderer registration
 */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AutomatedMinecraftBots.MODID);

    // NOTE: FakePlayers don't need EntityType registration
    // They are spawned programmatically and rendered automatically as players
}
