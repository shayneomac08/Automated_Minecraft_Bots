package com.shayneomac08.automated_minecraft_bots.bot;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.FakePlayer;

public record BotPair(FakePlayer hands, LivingEntity body) {}
