package com.shayneomac08.automated_minecraft_bots.registry;

import com.shayneomac08.automated_minecraft_bots.AutomatedMinecraftBots;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity registration - DISABLED for FakePlayer-based bots
 * FakePlayer entities cannot be registered as EntityTypes
 * Bots are spawned directly using AmbNpcEntity.spawnAtPlayer()
 */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AutomatedMinecraftBots.MODID);

    // NOTE: No entity registration for FakePlayer-based bots
    // They are created and spawned programmatically
}
