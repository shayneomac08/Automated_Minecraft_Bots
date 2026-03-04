package com.shayneomac08.automated_minecraft_bots.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * HUMAN-LIKE MOVEMENT SYSTEM
 * Adds natural movement patterns, head sway, smooth acceleration
 */
public class HumanlikeMovement {

    private static final Random random = new Random();

    /**
     * Movement state for smooth transitions
     */
    public static class MovementState {
        public float currentYaw = 0.0f;
        public float targetYaw = 0.0f;
        public float currentSpeed = 0.0f;
        public float targetSpeed = 0.0f;
        public int yawLerpTicks = 0;
        public int speedLerpTicks = 0;
        public boolean isSprinting = false;

        public void reset() {
            currentYaw = 0.0f;
            targetYaw = 0.0f;
            currentSpeed = 0.0f;
            targetSpeed = 0.0f;
            yawLerpTicks = 0;
            speedLerpTicks = 0;
            isSprinting = false;
        }
    }

    /**
     * Apply human-like head movement
     * Adds slight sway when idle, smooth rotation when moving
     */
    public static void applyHumanlikeHeadMovement(LivingEntity entity, MovementState state, BlockPos goal) {
        if (entity == null || state == null) return;

        // If no target, add slight random sway
        if (goal == null || goal.equals(BlockPos.ZERO)) {
            applyIdleSway(entity);
        } else {
            // Moving toward goal - lerp yaw smoothly
            applySmoothRotation(entity, state, goal);
        }

        // Always keep pitch level (looking forward, not at ground)
        entity.setXRot(0.0f + (random.nextFloat() * 2.0f - 1.0f)); // Slight vertical sway ±1 degree
    }

    /**
     * Apply idle head sway when not moving
     */
    private static void applyIdleSway(LivingEntity entity) {
        // Add slight random sway ±2.5 degrees
        float currentYaw = entity.getYRot();
        float sway = random.nextFloat() * 5.0f - 2.5f;
        entity.setYRot(currentYaw + sway);
        entity.setYHeadRot(entity.getYRot());
    }

    /**
     * Apply smooth rotation toward movement direction
     */
    private static void applySmoothRotation(LivingEntity entity, MovementState state, BlockPos goal) {
        // Calculate target yaw
        double dx = goal.getX() - entity.getX();
        double dz = goal.getZ() - entity.getZ();

        if (Math.abs(dx) + Math.abs(dz) > 0.0001) {
            state.targetYaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;

            // Lerp yaw over 5 ticks for smooth rotation
            if (state.yawLerpTicks < 5) {
                float currentYaw = entity.getYRot();
                float yawDiff = state.targetYaw - currentYaw;

                // Normalize angle difference to -180 to 180
                while (yawDiff > 180) yawDiff -= 360;
                while (yawDiff < -180) yawDiff += 360;

                float lerpedYaw = currentYaw + (yawDiff * 0.2f); // 20% per tick = 5 ticks total
                entity.setYRot(lerpedYaw);
                entity.setYHeadRot(lerpedYaw);
                entity.yBodyRot = lerpedYaw;

                state.yawLerpTicks++;
            } else {
                // Finished lerping, set to target
                entity.setYRot(state.targetYaw);
                entity.setYHeadRot(state.targetYaw);
                entity.yBodyRot = state.targetYaw;
                state.yawLerpTicks = 0;
            }
        }
    }

    /**
     * Apply smooth acceleration/deceleration
     */
    public static float applySmoothSpeed(MovementState state, float targetSpeed, boolean sprinting) {
        state.targetSpeed = targetSpeed;
        state.isSprinting = sprinting;

        // Lerp speed over 3 ticks for smooth acceleration
        if (state.speedLerpTicks < 3) {
            float speedDiff = state.targetSpeed - state.currentSpeed;
            state.currentSpeed += speedDiff * 0.33f; // 33% per tick = 3 ticks total
            state.speedLerpTicks++;
        } else {
            state.currentSpeed = state.targetSpeed;
            state.speedLerpTicks = 0;
        }

        return state.currentSpeed;
    }

    /**
     * Toggle sprint based on distance and stamina
     */
    public static boolean shouldSprint(LivingEntity entity, BlockPos goal, float currentSpeed) {
        if (entity == null || goal == null) return false;

        // Don't sprint if hungry
        if (entity instanceof net.neoforged.neoforge.common.util.FakePlayer player) {
            if (player.getFoodData().getFoodLevel() < 6) {
                return false;
            }
        }

        // Don't sprint if very close to goal (< 3 blocks)
        double distance = entity.position().distanceTo(Vec3.atCenterOf(goal));
        if (distance < 3.0) {
            return false;
        }

        // Sprint if moving fast and far from goal
        return currentSpeed >= 0.13f && distance >= 3.0;
    }

    /**
     * Add natural movement variation
     * Prevents perfectly straight lines, adds slight wobble
     */
    public static Vec3 addMovementVariation(Vec3 movement, boolean isMoving) {
        if (!isMoving) return movement;

        // Add slight random variation to movement (±5% in each direction)
        double variation = 0.05;
        double vx = movement.x * (1.0 + (random.nextDouble() * variation * 2 - variation));
        double vz = movement.z * (1.0 + (random.nextDouble() * variation * 2 - variation));

        return new Vec3(vx, movement.y, vz);
    }

    /**
     * Calculate human-like path deviation
     * Real players don't walk in perfectly straight lines
     */
    public static BlockPos addPathDeviation(BlockPos waypoint, LivingEntity entity) {
        // Add slight random offset (±1 block) to waypoint
        if (random.nextFloat() < 0.1) { // 10% chance to deviate
            int offsetX = random.nextInt(3) - 1; // -1, 0, or 1
            int offsetZ = random.nextInt(3) - 1;
            return waypoint.offset(offsetX, 0, offsetZ);
        }

        return waypoint;
    }

    /**
     * Check if entity should take a break (human-like behavior)
     */
    public static boolean shouldTakeBreak(LivingEntity entity, int ticksMoving) {
        // Randomly pause for 1-2 seconds after moving for 30+ seconds
        if (ticksMoving > 600 && random.nextFloat() < 0.01) { // 1% chance per tick after 30 seconds
            return true;
        }

        return false;
    }

    /**
     * Apply human-like looking around behavior
     */
    public static void lookAround(LivingEntity entity) {
        // Look in a random direction for a moment
        float randomYaw = entity.getYRot() + (random.nextFloat() * 90 - 45); // ±45 degrees
        float randomPitch = random.nextFloat() * 30 - 15; // ±15 degrees

        entity.setYRot(randomYaw);
        entity.setXRot(randomPitch);
        entity.setYHeadRot(randomYaw);
    }
}
