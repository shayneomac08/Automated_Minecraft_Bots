package com.shayneomac08.automated_minecraft_bots.client;

import com.shayneomac08.automated_minecraft_bots.AutomatedMinecraftBots;
import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcVisualEntity;
import com.shayneomac08.automated_minecraft_bots.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * CLIENT RENDERER REGISTRATION — MAKES THE VISUAL BOTS VISIBLE AS REAL PLAYERS
 *
 * Root cause of missing held-item rendering:
 * LivingEntityRenderer.extractRenderState() does NOT call
 * ArmedEntityRenderState.extractArmedEntityRenderState(), so rightHandItemStack /
 * leftHandItemStack / rightHandItemState / leftHandItemState are never populated.
 * HumanoidModel.setupAnim() reads those fields for arm pose; item-in-hand layers
 * read itemState for geometry. We override extractRenderState() to call the static
 * helper that correctly populates all held-item fields via the item model resolver.
 */
@EventBusSubscriber(modid = AutomatedMinecraftBots.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            ModEntities.AMB_NPC_VISUAL.get(),
            context -> new LivingEntityRenderer<AmbNpcVisualEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>>(
                context,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)),
                0.5f) {

                @Override
                public HumanoidRenderState createRenderState() {
                    return new HumanoidRenderState();
                }

                @Override
                public Identifier getTextureLocation(HumanoidRenderState state) {
                    return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
                }

                /**
                 * Populate held-item render state so HumanoidModel.setupAnim() produces the
                 * correct arm pose and item geometry is passed to rendering layers.
                 * Without this, extractRenderState leaves rightHandItemStack/leftHandItemStack
                 * empty and the model renders with bare hands regardless of inventory state.
                 */
                @Override
                public void extractRenderState(AmbNpcVisualEntity entity, HumanoidRenderState state, float partialTick) {
                    super.extractRenderState(entity, state, partialTick);
                    ArmedEntityRenderState.extractArmedEntityRenderState(
                        entity, state, this.itemModelResolver, partialTick);
                }
            }
        );
    }
}
