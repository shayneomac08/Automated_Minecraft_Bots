package com.shayneomac08.automated_minecraft_bots;

import com.shayneomac08.automated_minecraft_bots.client.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = AutomatedMinecraftBots.MODID, dist = Dist.CLIENT)
public class AutomatedMinecraftBotsClient {
    public AutomatedMinecraftBotsClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (client, parent) -> new ConfigScreen(parent));

        // Register mod bus events
        modEventBus.addListener(this::onClientSetup);
        // Note: Entity renderer registration is now handled by ClientModEvents class
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        AutomatedMinecraftBots.LOGGER.info("HELLO FROM CLIENT SETUP");
        AutomatedMinecraftBots.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
