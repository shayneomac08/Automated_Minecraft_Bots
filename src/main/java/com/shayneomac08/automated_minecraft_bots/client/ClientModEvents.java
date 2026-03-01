package com.shayneomac08.automated_minecraft_bots.client;

import com.shayneomac08.automated_minecraft_bots.AutomatedMinecraftBots;
import com.shayneomac08.automated_minecraft_bots.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * CLIENT RENDERER REGISTRATION — MAKES THE BOTS VISIBLE AS REAL PLAYERS
 */
@EventBusSubscriber(modid = AutomatedMinecraftBots.MODID, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            ModEntities.AMB_NPC.get(),
            context -> new LivingEntityRenderer<>(
                context,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)),
                0.5f) {  // shadow size

                @Override
                public HumanoidRenderState createRenderState() {
                    return new HumanoidRenderState();
                }

                @Override
                public Identifier getTextureLocation(HumanoidRenderState state) {
                    return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");  // Steve skin
                }
            }
        );
    }
}
