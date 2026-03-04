package com.shayneomac08.automated_minecraft_bots.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ENHANCED STUCK DETECTION SYSTEM
 * Multi-level stuck detection with progressive recovery strategies
 */
public class StuckDetection {

    /**
     * Stuck detection state tracker
     */
    public static class StuckState {
        public Vec3 lastPosition = Vec3.ZERO;
        public int stuckTicks = 0;
        public int recoveryLevel = 0; // 0=none, 1=jump+strafe, 2=place block, 3=LLM override
        public int ticksSinceProgress = 0;
        public double totalDistanceMoved = 0.0;
        public int measurementTicks = 0;

        public void reset() {
            stuckTicks = 0;
            recoveryLevel = 0;
            ticksSinceProgress = 0;
            totalDistanceMoved = 0.0;
            measurementTicks = 0;
        }

        public void updatePosition(Vec3 currentPos) {
            if (!lastPosition.equals(Vec3.ZERO)) {
                double moved = currentPos.distanceTo(lastPosition);
                totalDistanceMoved += moved;
                measurementTicks++;
            }
            lastPosition = currentPos;
        }

        public double getAverageSpeed() {
            if (measurementTicks == 0) return 0.0;
            return totalDistanceMoved / measurementTicks;
        }
    }

    /**
     * Check if entity is stuck using multiple criteria
     * Returns true if stuck
     */
    public static boolean isStuck(LivingEntity entity, StuckState state, BlockPos goal) {
        if (entity == null || state == null) return false;

        Vec3 currentPos = entity.position();
        state.updatePosition(currentPos);

        // Criterion 1: Position unchanged for 40 ticks (2 seconds)
        double distanceMoved = currentPos.distanceTo(state.lastPosition);
        if (distanceMoved < 0.05) { // ~0.05 blocks
            state.stuckTicks++;
        } else {
            state.stuckTicks = 0;
            state.ticksSinceProgress = 0;
        }

        // Criterion 2: No progress toward goal (average speed < 0.1 blocks/tick over 10 ticks)
        if (state.measurementTicks >= 10) {
            double avgSpeed = state.getAverageSpeed();
            if (avgSpeed < 0.01) { // Less than 0.01 blocks/tick average
                state.ticksSinceProgress++;
            } else {
                state.ticksSinceProgress = 0;
            }

            // Reset measurement window every 10 ticks
            state.totalDistanceMoved = 0.0;
            state.measurementTicks = 0;
        }

        // Stuck if either criterion is met
        return state.stuckTicks >= 40 || state.ticksSinceProgress >= 10;
    }

    /**
     * Execute recovery strategy based on stuck level
     * Returns true if recovery action was taken
     */
    public static boolean executeRecovery(LivingEntity entity, StuckState state, BlockPos goal) {
        if (entity == null || state == null) return false;

        // Increment recovery level
        if (state.recoveryLevel == 0) {
            state.recoveryLevel = 1;
        } else if (state.stuckTicks > 80) { // 4 seconds at level 1
            state.recoveryLevel = 2;
        } else if (state.stuckTicks > 120) { // 6 seconds at level 2
            state.recoveryLevel = 3;
        }

        switch (state.recoveryLevel) {
            case 1:
                // Level 1: Jump and strafe randomly
                return executeLevel1Recovery(entity);
            case 2:
                // Level 2: Place block below or ladder
                return executeLevel2Recovery(entity);
            case 3:
                // Level 3: Call LLM for override (placeholder for now)
                return executeLevel3Recovery(entity, goal);
            default:
                return false;
        }
    }

    /**
     * Level 1 Recovery: Jump and strafe randomly
     */
    private static boolean executeLevel1Recovery(LivingEntity entity) {
        System.out.println("[AMB-STUCK] Level 1 Recovery: Jump and strafe");

        // Jump
        if (entity.onGround()) {
            entity.setDeltaMovement(entity.getDeltaMovement().x, 0.42, entity.getDeltaMovement().z);
        }

        // Random strafe direction
        double randomAngle = Math.random() * Math.PI * 2;
        double strafeX = Math.cos(randomAngle) * 0.2;
        double strafeZ = Math.sin(randomAngle) * 0.2;

        Vec3 currentVel = entity.getDeltaMovement();
        entity.setDeltaMovement(
            currentVel.x + strafeX,
            currentVel.y,
            currentVel.z + strafeZ
        );

        return true;
    }

    /**
     * Level 2 Recovery: Place block below or attempt to climb
     */
    private static boolean executeLevel2Recovery(LivingEntity entity) {
        System.out.println("[AMB-STUCK] Level 2 Recovery: Attempting block placement");

        // Check if we can place a block below to climb up
        BlockPos below = entity.blockPosition().below();
        BlockPos feet = entity.blockPosition();

        // If there's air below and we're not on ground, try to place a block
        if (!entity.onGround() && entity.level().getBlockState(below).isAir()) {
            // This would require block placement logic - placeholder for now
            System.out.println("[AMB-STUCK] Would place block at " + below + " to climb");

            // For now, just try to move up
            entity.setDeltaMovement(entity.getDeltaMovement().x, 0.5, entity.getDeltaMovement().z);
            return true;
        }

        // Try breaking block above if stuck in a hole
        BlockPos above = entity.blockPosition().above();
        if (entity.level().getBlockState(above).canOcclude()) {
            System.out.println("[AMB-STUCK] Would break block at " + above + " to escape");
            // Placeholder - actual block breaking would go here
            return true;
        }

        return false;
    }

    /**
     * Level 3 Recovery: LLM override (placeholder)
     */
    private static boolean executeLevel3Recovery(LivingEntity entity, BlockPos goal) {
        System.out.println("[AMB-STUCK] Level 3 Recovery: LLM override needed");
        System.out.println("[AMB-STUCK] Entity at " + entity.blockPosition() + ", goal at " + goal);

        // This would call the LLM to get a custom recovery action
        // For now, just clear the stuck state and let the bot try a new path
        return false;
    }
}
