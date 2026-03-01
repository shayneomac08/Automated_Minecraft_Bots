package com.shayneomac08.automated_minecraft_bots;

import com.shayneomac08.automated_minecraft_bots.client.ConfigScreen;
import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import com.shayneomac08.automated_minecraft_bots.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = AutomatedMinecraftBots.MODID, dist = Dist.CLIENT)
public class AutomatedMinecraftBotsClient {
    public AutomatedMinecraftBotsClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (client, parent) -> new ConfigScreen(parent));

        // Register mod bus events
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerRenderers);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        AutomatedMinecraftBots.LOGGER.info("HELLO FROM CLIENT SETUP");
        AutomatedMinecraftBots.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // CLIENT RENDERER REGISTRATION — MAKES SERVERPLAYER BOTS VISIBLE AS REAL PLAYERS
        event.registerEntityRenderer(
            ModEntities.AMB_NPC.get(),
            context -> new LivingEntityRenderer<>(
                context,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)),
                0.5F
            ) {
                @Override
                public HumanoidRenderState createRenderState() {
                    return new HumanoidRenderState();
                }

                @Override
                public Identifier getTextureLocation(HumanoidRenderState state) {
                    // Use default Steve skin
                    return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
                }
            }
        );
    }
}
