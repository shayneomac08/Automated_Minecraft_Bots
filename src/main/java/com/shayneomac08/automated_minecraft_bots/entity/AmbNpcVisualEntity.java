package com.shayneomac08.automated_minecraft_bots.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * HYBRID SYSTEM — VISUAL MIRROR ENTITY FOR CLIENT RENDERING
 * This PathfinderMob mirrors the FakePlayer's position/rotation for client visibility.
 * The FakePlayer (AmbNpcEntity) handles all logic, AI, inventory, and tasks.
 *
 * Skin randomization: SKIN_VARIANT is a synced data field set from the entity UUID on
 * server spawn; the client renderer reads it via getSkinVariant() to pick from a skin pool.
 * This is deterministic — same UUID always picks same skin — and costs one extra int per
 * entity in the entity data packet (negligible).
 */
public class AmbNpcVisualEntity extends PathfinderMob {

    /** Number of distinct skin variants available (must match ClientModEvents.SKIN_POOL length). */
    public static final int SKIN_COUNT = 8;

    /** Synced data key: skin variant index (0..SKIN_COUNT-1). Synced client automatically. */
    private static final EntityDataAccessor<Integer> SKIN_VARIANT =
        SynchedEntityData.defineId(AmbNpcVisualEntity.class, EntityDataSerializers.INT);

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
        this.setSilent(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_VARIANT, 0);
    }

    /**
     * Assign skin variant from this entity's UUID so every bot gets a different look.
     * Call this immediately after the entity is added to the world (UUID is then stable).
     */
    public void initSkinFromUUID() {
        int variant = (int)(Math.abs(getUUID().getMostSignificantBits()) % SKIN_COUNT);
        entityData.set(SKIN_VARIANT, variant);
        System.out.printf("[AMB-SKIN] visualEntity id=%d → skinVariant=%d (UUID=%s)%n",
            getId(), variant, getUUID());
    }

    /** Returns the skin variant index (0..SKIN_COUNT-1). Used by the client renderer. */
    public int getSkinVariant() {
        return entityData.get(SKIN_VARIANT);
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

    // Keep defaults for riding/passengers/collision to avoid signature mismatches in 1.21

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

    /**
     * When the visual entity dies (drowning, mob attack, etc.), also kill the logic entity.
     * This ensures respawn triggers correctly since the respawn system is on AmbNpcEntity.die().
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        super.die(cause);
        if (logicEntity != null && logicEntity.isAlive()) {
            logicEntity.die(cause);
        }
    }
}
