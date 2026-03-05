package com.shayneomac08.automated_minecraft_bots.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * BOT TICKER SYSTEM
 * Handles per-tick physics updates, travel mechanics, and human-like movement behaviors
 */
public class BotTicker {

    /**
     * Execute bot tick - called every game tick for physics and movement updates
     */
    public static void tick(FakePlayer bot, BlockPos goal, HumanlikeMovement.MovementState movementState) {
        if (bot == null) return;

        // Apply human-like head movement
        HumanlikeMovement.applyHumanlikeHeadMovement(bot, movementState, goal);

        // Handle climbing (ladders, vines, water)
        handleClimbing(bot, goal);

        // Random exploration if no goal and no cooldown
        if (goal == null || goal.equals(BlockPos.ZERO)) {
            if (bot.tickCount % 100 == 0 && Math.random() < 0.3) {
                // 30% chance every 5 seconds to look around
                HumanlikeMovement.lookAround(bot);
            }
        }
    }

    /**
     * Handle climbing mechanics (ladders, vines, water)
     */
    private static void handleClimbing(FakePlayer bot, BlockPos goal) {
        if (bot == null) return;

        BlockPos pos = bot.blockPosition();
        boolean onLadder = bot.onClimbable();
        boolean inWater = bot.isInWater();

        // If on ladder or in water and goal is above, climb up
        if ((onLadder || inWater) && goal != null && !goal.equals(BlockPos.ZERO)) {
            int verticalDiff = goal.getY() - pos.getY();

            if (verticalDiff > 0) {
                // Need to go up
                Vec3 vel = bot.getDeltaMovement();
                if (onLadder) {
                    // Climb ladder
                    bot.setDeltaMovement(vel.x, 0.15, vel.z);
                } else if (inWater) {
                    // Swim up
                    bot.setDeltaMovement(vel.x, 0.08, vel.z);
                }
            } else if (verticalDiff < -1 && onLadder) {
                // Need to go down on ladder
                Vec3 vel = bot.getDeltaMovement();
                bot.setDeltaMovement(vel.x, -0.15, vel.z);
            }
        }
    }

    /**
     * Apply travel physics (movement with proper collision detection)
     */
    private static void applyTravelPhysics(FakePlayer bot, HumanlikeMovement.MovementState movementState) {
        if (bot == null) return;

        // Get current movement vector
        Vec3 movement = bot.getDeltaMovement();

        // Apply gravity if not on ground
        if (!bot.onGround() && !bot.isInWater() && !bot.onClimbable()) {
            movement = movement.add(0, -0.08, 0); // Gravity
        }

        // Apply movement with collision detection
        bot.move(MoverType.SELF, movement);

        // Apply friction
        if (bot.onGround()) {
            movement = bot.getDeltaMovement();
            bot.setDeltaMovement(movement.x * 0.91, movement.y * 0.98, movement.z * 0.91);
        } else {
            movement = bot.getDeltaMovement();
            bot.setDeltaMovement(movement.x * 0.98, movement.y * 0.98, movement.z * 0.98);
        }
    }

    /**
     * Calculate look direction based on path
     */
    public static void updateLookDirection(FakePlayer bot, BlockPos goal, boolean isMoving) {
        if (bot == null || goal == null || goal.equals(BlockPos.ZERO)) return;

        double dx = goal.getX() - bot.getX();
        double dy = goal.getY() - bot.getY();
        double dz = goal.getZ() - bot.getZ();

        if (Math.abs(dx) + Math.abs(dz) > 0.0001) {
            float targetYaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;

            // Smooth rotation (lerp over 5 ticks)
            float currentYaw = bot.getYRot();
            float yawDiff = targetYaw - currentYaw;

            // Normalize angle difference
            while (yawDiff > 180) yawDiff -= 360;
            while (yawDiff < -180) yawDiff += 360;

            float newYaw = currentYaw + (yawDiff * 0.2f);
            bot.setYRot(newYaw);
            bot.setYHeadRot(newYaw);
            bot.yBodyRot = newYaw;

            // Look up for trees, level for ground
            if (dy > 2) {
                bot.setXRot(-10.0f); // Look up slightly
            } else {
                bot.setXRot(0.0f + (float)(Math.random() * 4 - 2)); // Level with slight sway
            }
        }
    }

    /**
     * Determine if bot should sprint based on conditions
     */
    public static boolean shouldSprint(FakePlayer bot, BlockPos goal) {
        if (bot == null || goal == null) return false;

        // Don't sprint if hungry
        if (bot.getFoodData().getFoodLevel() < 6) {
            return false;
        }

        // Don't sprint if very close to goal
        double distance = bot.position().distanceTo(Vec3.atCenterOf(goal));
        if (distance < 3.0) {
            return false;
        }

        // Don't sprint if there's an obstacle ahead
        if (bot.horizontalCollision) {
            return false;
        }

        // Sprint if moving fast and far from goal
        return distance >= 10.0;
    }
}
