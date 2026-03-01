package com.shayneomac08.automated_minecraft_bots.registry;

import com.shayneomac08.automated_minecraft_bots.AutomatedMinecraftBots;
import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcVisualEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity registration for visual bot entities
 * The actual logic runs in FakePlayer (server-side), this is just for rendering
 */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AutomatedMinecraftBots.MODID);

    // Register visual entity that mirrors the FakePlayer
    public static final DeferredHolder<EntityType<?>, EntityType<AmbNpcVisualEntity>> AMB_NPC_VISUAL = ENTITIES.register("amb_npc_visual",
        () -> EntityType.Builder.<AmbNpcVisualEntity>of(AmbNpcVisualEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.8F)           // exact player hitbox
            .clientTrackingRange(64)     // visible from normal distance
            .updateInterval(1)           // very smooth updates to mirror FakePlayer
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(AutomatedMinecraftBots.MODID, "amb_npc_visual"))));

    @EventBusSubscriber(modid = AutomatedMinecraftBots.MODID)
    public static class EntityAttributeRegistration {
        @SubscribeEvent
        public static void registerAttributes(EntityAttributeCreationEvent event) {
            // Register attributes for visual entity
            event.put(AMB_NPC_VISUAL.get(), AmbNpcVisualEntity.createAttributes().build());
        }
    }
}
