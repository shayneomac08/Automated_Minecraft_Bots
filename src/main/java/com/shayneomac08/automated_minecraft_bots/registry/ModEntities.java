package com.shayneomac08.automated_minecraft_bots.registry;

import com.shayneomac08.automated_minecraft_bots.AutomatedMinecraftBots;
import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity registration for FakePlayer-based bots with custom renderer
 */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AutomatedMinecraftBots.MODID);

    // Register FakePlayer entity for custom rendering
    public static final DeferredHolder<EntityType<?>, EntityType<AmbNpcEntity>> AMB_NPC = ENTITIES.register("amb_npc",
        () -> EntityType.Builder.<AmbNpcEntity>of(AmbNpcEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.8F)           // exact player hitbox
            .clientTrackingRange(64)
            .updateInterval(3)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(AutomatedMinecraftBots.MODID, "amb_npc"))));
}
