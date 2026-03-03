package com.shayneomac08.automated_minecraft_bots.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * REALISTIC PLAYER-LIKE MOVEMENT SYSTEM
 * Makes bots move like real Minecraft players with physics, pathfinding, and environmental awareness
 */
public class RealisticMovement {

    /**
     * Move towards a target position with realistic player physics
     * Returns true if still moving, false if reached destination
     */
    public static boolean moveTowards(LivingEntity entity, BlockPos target, float speed) {
        if (entity == null || target == null) return false;

        Vec3 currentPos = entity.position();
        Vec3 targetPos = Vec3.atBottomCenterOf(target);

        // Calculate distance
        double dx = targetPos.x - currentPos.x;
        double dz = targetPos.z - currentPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Reached destination (within 1 block)
        if (horizontalDist < 1.5) {
            entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);
            return false;
        }

        // Normalize direction and apply acceleration toward target speed
        double dirX = dx / horizontalDist;
        double dirZ = dz / horizontalDist;

        // If in water, use swimming movement rather than walking logic
        if (entity.isInWater()) {
            // Swim toward target with upward bias if underwater
            Vec3 swimDir = new Vec3(dirX, 0, dirZ);
            swim(entity, swimDir);
            // Face direction
            float wyaw = (float) (Math.atan2(dirZ, dirX) * 180 / Math.PI) - 90;
            entity.setYRot(wyaw);
            entity.setYHeadRot(wyaw);
            entity.yBodyRot = wyaw;
            entity.setSprinting(false);
            return true;
        }

        // Smooth acceleration/deceleration
        Vec3 cur = entity.getDeltaMovement();
        double targetVX = dirX * speed;
        double targetVZ = dirZ * speed;
        double ax = (targetVX - cur.x) * 0.35; // acceleration factor
        double az = (targetVZ - cur.z) * 0.35;
        double newVX = cur.x + ax;
        double newVZ = cur.z + az;

        Vec3 movement = new Vec3(newVX, cur.y, newVZ);

        // Apply movement using Entity.move to get proper collisions and onGround updates
        Vec3 prePos = entity.position();
        Vec3 moveVec = new Vec3(movement.x, movement.y - 0.08, movement.z);
        entity.move(MoverType.SELF, moveVec);
        Vec3 postPos = entity.position();

        // If we barely moved horizontally and we're on the ground, try a jump next tick
        double movedXZ = Math.hypot(postPos.x - prePos.x, postPos.z - prePos.z);
        if (movedXZ < 0.02 && entity.onGround()) {
            entity.setDeltaMovement(entity.getDeltaMovement().x, 0.42, entity.getDeltaMovement().z);
        }

        // Update rotation to face movement direction
        float yaw = (float) (Math.atan2(dirZ, dirX) * 180 / Math.PI) - 90;
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.yBodyRot = yaw;

        // Set sprinting if moving fast
        entity.setSprinting(speed >= 0.13f);

        return true;
    }

    /**
     * Strafe around an obstacle relative to a target, to get around corners/door frames.
     */
    public static void strafeAround(LivingEntity entity, Vec3 target, int dir, float speed) {
        Vec3 pos = entity.position();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double len = Math.sqrt(dx*dx + dz*dz);
        if (len < 1e-4) return;
        dx /= len; dz /= len;
        // perpendicular vector (left/right)
        double px = -dz * dir;
        double pz = dx * dir;
        Vec3 cur = entity.getDeltaMovement();
        double targetVX = px * speed;
        double targetVZ = pz * speed;
        double ax = (targetVX - cur.x) * 0.35;
        double az = (targetVZ - cur.z) * 0.35;
        Vec3 mv = new Vec3(cur.x + ax, cur.y - 0.08, cur.z + az);
        entity.move(MoverType.SELF, mv);
        // face roughly toward target while strafing
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.yBodyRot = yaw;
    }

    /**
     * Check if the path ahead is blocked by solid blocks
     */
    private static boolean isPathBlocked(LivingEntity entity, Vec3 movement) {
        Vec3 start = entity.position().add(0, 0.5, 0); // Eye level
        Vec3 end = start.add(movement.normalize().scale(1.0)); // Check 1 block ahead

        ClipContext context = new ClipContext(
            start,
            end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            entity
        );

        BlockHitResult result = entity.level().clip(context);
        return result.getType() != HitResult.Type.MISS;
    }

    /**
     * Check if entity can jump over the obstacle ahead
     */
    private static boolean canJumpOver(LivingEntity entity) {
        BlockPos feetPos = entity.blockPosition();
        BlockPos ahead = feetPos.relative(entity.getDirection());
        BlockPos aboveAhead = ahead.above();

        // Can jump if block ahead is 1 block high and space above is clear
        BlockState blockAhead = entity.level().getBlockState(ahead);
        BlockState blockAbove = entity.level().getBlockState(aboveAhead);

        return blockAhead.canOcclude() && !blockAbove.canOcclude() && entity.onGround();
    }

    /**
     * Find an alternative path around an obstacle
     */
    private static Vec3 findAlternativePath(LivingEntity entity, Vec3 target) {
        Vec3 currentPos = entity.position();

        // Try moving at 45-degree angles to go around obstacle
        double[] angles = {45, -45, 90, -90};

        for (double angleOffset : angles) {
            double dx = target.x - currentPos.x;
            double dz = target.z - currentPos.z;
            double angle = Math.atan2(dz, dx) + Math.toRadians(angleOffset);

            double newDx = Math.cos(angle) * 0.2;
            double newDz = Math.sin(angle) * 0.2;

            Vec3 testMovement = new Vec3(newDx, entity.getDeltaMovement().y, newDz);

            if (!isPathBlocked(entity, testMovement) && !isDangerousPath(entity, testMovement)) {
                return testMovement;
            }
        }

        return null; // No alternative path found
    }

    /**
     * Check if the path ahead is dangerous (lava, void, deep water)
     */
    private static boolean isDangerousPath(LivingEntity entity, Vec3 movement) {
        BlockPos nextPos = BlockPos.containing(entity.position().add(movement));
        BlockPos below = nextPos.below();

        // Check for void (y < 0)
        if (nextPos.getY() < 0) {
            return true;
        }

        // Check for cliff (fall > 3 blocks)
        int fallDistance = 0;
        BlockPos checkPos = below;
        while (fallDistance < 5 && !entity.level().getBlockState(checkPos).canOcclude()) {
            checkPos = checkPos.below();
            fallDistance++;
        }

        return fallDistance > 3; // Dangerous fall
    }

    /**
     * Make entity look at a specific position (realistic head movement)
     */
    public static void lookAt(LivingEntity entity, Vec3 target) {
        Vec3 eyePos = entity.getEyePosition();

        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        float pitch = (float) -(Math.atan2(dy, horizontalDist) * 180 / Math.PI);

        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.setYHeadRot(yaw);
    }

    /**
     * Check if entity can reach a block (within interaction range)
     */
    public static boolean canReach(LivingEntity entity, BlockPos target) {
        double distance = entity.position().distanceTo(Vec3.atCenterOf(target));
        return distance <= 5.0; // Player reach distance
    }

    /**
     * Navigate to a block and face it (for mining/interaction)
     */
    public static boolean navigateToBlock(LivingEntity entity, BlockPos target, float speed) {
        // Move towards the block
        boolean stillMoving = moveTowards(entity, target, speed);

        // Look at the block while moving
        lookAt(entity, Vec3.atCenterOf(target));

        return stillMoving;
    }

    /**
     * Find the nearest walkable position to a target (for unreachable targets)
     */
    public static BlockPos findNearestWalkable(ServerLevel level, BlockPos target, BlockPos startPos) {
        // Search in expanding radius
        for (int radius = 1; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = target.offset(x, 0, z);

                    // Check if position is walkable
                    if (isWalkable(level, checkPos)) {
                        return checkPos;
                    }
                }
            }
        }

        return startPos; // Fallback to current position
    }

    /**
     * Check if a position is safe to walk on
     */
    public static boolean isWalkable(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockPos feet = pos;
        BlockPos head = pos.above();

        BlockState belowState = level.getBlockState(below);
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);

        // Must have solid ground
        if (!belowState.canOcclude()) return false;

        // Must have space for body
        if (feetState.canOcclude() || headState.canOcclude()) return false;

        // Do not hard-block lava here; caller will choose path costs. Treat as potentially passable.

        return true;
    }

    /**
     * Calculate realistic movement speed based on terrain
     */
    public static float calculateSpeed(LivingEntity entity, boolean sprinting) {
        float baseSpeed = 0.1f; // Walking speed

        if (sprinting) {
            baseSpeed = 0.13f; // Sprinting speed
        }

        // Slower in water
        if (entity.isInWater()) {
            baseSpeed *= 0.5f;
        }

        // Slower when sneaking
        if (entity.isShiftKeyDown()) {
            baseSpeed *= 0.3f;
        }

        return baseSpeed;
    }

    /**
     * Check if entity needs to swim up (underwater)
     */
    public static boolean shouldSwimUp(LivingEntity entity) {
        return entity.isInWater() && !entity.isUnderWater();
    }

    /**
     * Apply swimming movement (realistic water navigation)
     */
    public static void swim(LivingEntity entity, Vec3 direction) {
        Vec3 movement = direction.normalize().scale(0.08); // Slower in water

        // Add upward movement if underwater
        if (entity.isUnderWater()) {
            movement = movement.add(0, 0.04, 0);
        }

        entity.setDeltaMovement(movement);
    }
}
