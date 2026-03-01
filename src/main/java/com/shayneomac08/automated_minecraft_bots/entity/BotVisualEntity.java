package com.shayneomac08.automated_minecraft_bots.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Visual-only entity that mirrors the FakePlayer's position
 * This is what players see, while the FakePlayer handles all the logic
 */
public class BotVisualEntity extends PathfinderMob {

    private AmbNpcEntity linkedBot;

    public BotVisualEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setNoAi(true); // No AI - just follows the FakePlayer
    }

    public void setLinkedBot(AmbNpcEntity bot) {
        this.linkedBot = bot;
        if (bot != null) {
            this.setCustomName(bot.getCustomName());
            this.setCustomNameVisible(true);
        }
    }

    @Override
    public void tick() {
        super.tick();

        // Mirror the FakePlayer's position and rotation
        if (linkedBot != null && !linkedBot.isRemoved()) {
            this.setPos(linkedBot.getX(), linkedBot.getY(), linkedBot.getZ());
            this.setYRot(linkedBot.getYRot());
            this.setYHeadRot(linkedBot.getYHeadRot());
            this.yBodyRot = linkedBot.yBodyRot;

            // Mirror held items
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, linkedBot.getMainHandItem());
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, linkedBot.getOffhandItem());
        } else if (linkedBot != null && linkedBot.isRemoved()) {
            // FakePlayer was removed, remove this visual entity too
            this.discard();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0) // Doesn't move on its own
            .add(Attributes.FOLLOW_RANGE, 0.0);
    }

    @Override
    public boolean isPushable() {
        return false; // Can't be pushed
    }

    @Override
    public boolean isPickable() {
        return false; // Can't be targeted
    }
}
