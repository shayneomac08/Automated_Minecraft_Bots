package com.shayneomac08.automated_minecraft_bots.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * HYBRID SYSTEM — VISUAL MIRROR ENTITY FOR CLIENT RENDERING
 * This PathfinderMob mirrors the FakePlayer's position/rotation for client visibility
 * The FakePlayer (AmbNpcEntity) handles all logic, AI, inventory, and tasks
 */
public class AmbNpcVisualEntity extends PathfinderMob {

    private final AmbNpcEntity logicEntity;

    // Constructor for entity registration (logicEntity will be null initially)
    public AmbNpcVisualEntity(EntityType<? extends PathfinderMob> type, Level level) {
        this(type, level, null);
    }

    // Constructor with logic entity reference
    public AmbNpcVisualEntity(EntityType<? extends PathfinderMob> type, Level level, AmbNpcEntity logic) {
        super(type, level);
        this.logicEntity = logic;
        this.setNoAi(true); // No AI - just mirrors the FakePlayer
    }

    @Override
    public void tick() {
        super.tick();

        // Mirror the FakePlayer's position, rotation, and state
        if (logicEntity != null && !logicEntity.isRemoved()) {
            this.setPos(logicEntity.getX(), logicEntity.getY(), logicEntity.getZ());
            this.setYRot(logicEntity.getYRot());
            this.setXRot(logicEntity.getXRot());
            this.setYHeadRot(logicEntity.getYHeadRot());
            this.yBodyRot = logicEntity.yBodyRot;

            // Mirror held items for visual feedback
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, logicEntity.getMainHandItem());
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, logicEntity.getOffhandItem());

            // Mirror custom name
            if (logicEntity.getCustomName() != null) {
                this.setCustomName(logicEntity.getCustomName());
                this.setCustomNameVisible(true);
            }
        } else if (logicEntity != null && logicEntity.isRemoved()) {
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
        return false; // Can't be pushed - it's just a visual mirror
    }

    @Override
    public boolean isPickable() {
        return false; // Can't be targeted - interact with the FakePlayer instead
    }

    // Suppress visual-only entity particles/sounds
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        // no step sounds
    }

    @Override
    public void spawnSprintParticle() {
        // suppress dirt kicking/sprint particles
    }

    @Override
    public float getSoundVolume() {
        return 0.0F;
    }
}
