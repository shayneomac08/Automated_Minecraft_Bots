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
 * CLIENT RENDERER — SINGLE VISIBLE BOT BODY WITH RANDOMIZED SKIN
 *
 * Architecture:
 * - AmbNpcVisualEntity (PathfinderMob) is the SOLE visible entity.
 * - AmbNpcEntity (FakePlayer) is server-only; clients never see it directly.
 * - Skin is chosen from SKIN_POOL[0..7] using SKIN_VARIANT synced data set at spawn.
 * - Swing animation: visualEntity.swing(hand, true) on server sends
 *   ClientboundAnimatePacket with visual entity's ID → client plays arm swing.
 * - Held items: extractArmedEntityRenderState() populates rightHandItemState /
 *   leftHandItemState so HumanoidModel.setupAnim() picks the correct arm pose and
 *   ItemInHandLayer renders item geometry.
 *
 * BotRenderState extends HumanoidRenderState to carry skinIndex through the pipeline.
 * skinIndex is set in extractRenderState() and consumed in getTextureLocation().
 */
@EventBusSubscriber(modid = AutomatedMinecraftBots.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    /**
     * Wide-body (Steve proportions) vanilla skin pool.
     * Index matches AmbNpcVisualEntity.SKIN_COUNT — keep both in sync.
     * 0=steve 1=ari 2=efe 3=kai 4=makena 5=noor 6=sunny 7=zuri
     */
    private static final Identifier[] SKIN_POOL = {
        Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"),
        Identifier.withDefaultNamespace("textures/entity/player/wide/ari.png"),
        Identifier.withDefaultNamespace("textures/entity/player/wide/efe.png"),
        Identifier.withDefaultNamespace("textures/entity/player/wide/kai.png"),
        Identifier.withDefaultNamespace("textures/entity/player/wide/makena.png"),
        Identifier.withDefaultNamespace("textures/entity/player/wide/noor.png"),
        Identifier.withDefaultNamespace("textures/entity/player/wide/sunny.png"),
        Identifier.withDefaultNamespace("textures/entity/player/wide/zuri.png"),
    };

    /** Extended render state carrying the skin selection through the render pipeline. */
    public static class BotRenderState extends HumanoidRenderState {
        public int skinIndex = 0;
    }

    @SubscribeEvent
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            ModEntities.AMB_NPC_VISUAL.get(),
            context -> {
                var model = new HumanoidModel<BotRenderState>(context.bakeLayer(ModelLayers.PLAYER));

                var renderer = new LivingEntityRenderer<AmbNpcVisualEntity, BotRenderState, HumanoidModel<BotRenderState>>(
                    context, model, 0.5f) {

                    @Override
                    public BotRenderState createRenderState() {
                        return new BotRenderState();
                    }

                    /**
                     * Populate skin index and held-item state.
                     * skinIndex: from synced SKIN_VARIANT (set server-side from UUID at spawn).
                     * ArmedEntityRenderState.extractArmedEntityRenderState() fills
                     * rightHandItemState / leftHandItemState for item geometry and arm pose.
                     */
                    @Override
                    public void extractRenderState(AmbNpcVisualEntity entity, BotRenderState state, float partialTick) {
                        super.extractRenderState(entity, state, partialTick);
                        state.skinIndex = Math.max(0, Math.min(SKIN_POOL.length - 1, entity.getSkinVariant()));
                        ArmedEntityRenderState.extractArmedEntityRenderState(
                            entity, state, this.itemModelResolver, partialTick);
                    }

                    @Override
                    public Identifier getTextureLocation(BotRenderState state) {
                        return SKIN_POOL[state.skinIndex];
                    }
                };

                // ItemInHandLayer renders the 3D item model in the bot's hand.
                // Without this layer the arm pose changes correctly but no item geometry is drawn.
                // It reads rightHandItemState / leftHandItemState populated above.
                renderer.addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<>(renderer));

                return renderer;
            }
        );
    }
}
