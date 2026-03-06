package com.shayneomac08.automated_minecraft_bots.movement;

import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import com.shayneomac08.automated_minecraft_bots.llm.LLMInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;

/**
 * ENHANCED STUCK DETECTION SYSTEM
 * Multi-level stuck detection with progressive recovery strategies
 * Level 1: Jump + Strafe
 * Level 2: Recompute path with relaxed constraints
 * Level 3: Vertical navigation (pillar/dig)
 * Level 4: LLM override for complex situations
 */
public class StuckDetection {

    /**
     * Stuck detection state tracker
     */
    public static class StuckState {
        public Vec3 lastPosition = Vec3.ZERO;
        public int stuckTicks = 0;
        public int recoveryLevel = 0; // 0=none, 1=jump+strafe, 2=recompute, 3=vertical, 4=LLM
        public int ticksSinceProgress = 0;
        public double totalDistanceMoved = 0.0;
        public int measurementTicks = 0;
        public int recoveryCooldown = 0; // Cooldown between recovery attempts
        public BlockPos lastStuckPos = BlockPos.ZERO;
        public int pathNullTicks = 0; // Ticks with null/empty path

        public void reset() {
            stuckTicks = 0;
            recoveryLevel = 0;
            ticksSinceProgress = 0;
            totalDistanceMoved = 0.0;
            measurementTicks = 0;
            pathNullTicks = 0;
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

        // Decrement recovery cooldown
        if (state.recoveryCooldown > 0) {
            state.recoveryCooldown--;
        }

        // Criterion 1: Position unchanged for 60 ticks (3 seconds)
        double distanceMoved = currentPos.distanceTo(state.lastPosition);
        if (distanceMoved < 0.05) { // ~0.05 blocks
            state.stuckTicks++;
        } else {
            state.stuckTicks = 0;
            state.ticksSinceProgress = 0;
        }

        // Criterion 2: No progress toward goal (average speed < 0.01 blocks/tick)
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

        // Criterion 3: Distance to goal stalled — update reference position each check
        if (goal != null && !goal.equals(BlockPos.ZERO)) {
            double distToGoal = entity.blockPosition().distSqr(goal);
            if (!state.lastStuckPos.equals(BlockPos.ZERO)) {
                double lastDistToGoal = state.lastStuckPos.distSqr(goal);
                if (Math.abs(distToGoal - lastDistToGoal) < 1.0) {
                    // Not making meaningful progress toward goal
                    state.ticksSinceProgress++;
                }
            }
            // Always update reference so next check is from current position
            state.lastStuckPos = entity.blockPosition();
        }

        // Stuck if position-based criteria are met
        // Note: inNonSolid (leaf/glass blocks) was removed — bots can legitimately stand on
        // non-occluding blocks (leaves, slabs) and this caused an infinite recovery loop.
        return state.stuckTicks >= 60 || state.ticksSinceProgress >= 40;
    }

    /**
     * Execute recovery strategy based on stuck level
     * Returns true if recovery action was taken
     */
    public static boolean executeRecovery(LivingEntity entity, StuckState state, BlockPos goal) {
        if (entity == null || state == null) return false;

        // Don't execute recovery if on cooldown
        if (state.recoveryCooldown > 0) {
            return false;
        }

        // Progressive recovery level escalation
        if (state.recoveryLevel == 0) {
            state.recoveryLevel = 1;
        } else if (state.stuckTicks > 80) { // 4 seconds at level 1
            state.recoveryLevel = 2;
        } else if (state.stuckTicks > 120) { // 6 seconds at level 2
            state.recoveryLevel = 3;
        } else if (state.stuckTicks > 160) { // 8 seconds at level 3
            state.recoveryLevel = 4;
        }

        boolean success = false;

        switch (state.recoveryLevel) {
            case 1:
                // Level 1: Jump and strafe randomly (30% chance per tick)
                success = executeLevel1Recovery(entity);
                break;
            case 2:
                // Level 2: Recompute path with relaxed constraints
                success = executeLevel2Recovery(entity, goal);
                break;
            case 3:
                // Level 3: Vertical navigation (pillar/dig)
                success = executeLevel3Recovery(entity, goal);
                break;
            case 4:
                // Level 4: LLM override for complex situations
                success = executeLevel4Recovery(entity, goal);
                break;
            default:
                return false;
        }

        // Set cooldown after recovery attempt (400 ticks = 20 seconds)
        if (success) {
            state.recoveryCooldown = 400;
        }

        return success;
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
     * Level 2 Recovery: Recompute path with relaxed constraints
     */
    private static boolean executeLevel2Recovery(LivingEntity entity, BlockPos goal) {
        System.out.println("[AMB-STUCK] Level 2 Recovery: Recomputing path with relaxed constraints");

        // Signal to recompute path (caller should handle this)
        // This allows pathfinding through 1-block obstacles
        return true;
    }

    /**
     * Level 3 Recovery: Vertical navigation (pillar building or digging)
     */
    private static boolean executeLevel3Recovery(LivingEntity entity, BlockPos goal) {
        System.out.println("[AMB-STUCK] Level 3 Recovery: Attempting vertical navigation");

        if (!(entity instanceof FakePlayer player)) {
            return false;
        }

        if (goal == null || goal.equals(BlockPos.ZERO)) {
            return false;
        }

        int verticalDiff = goal.getY() - entity.blockPosition().getY();

        if (verticalDiff > 2) {
            // Need to go up - try pillar building
            System.out.println("[AMB-STUCK] Attempting to build pillar (height: " + verticalDiff + ")");
            return VerticalNavigation.buildPillar(player, verticalDiff);
        } else if (verticalDiff < -2) {
            // Need to go down - try placing ladder or digging
            System.out.println("[AMB-STUCK] Attempting to descend (depth: " + (-verticalDiff) + ")");
            // For now, just allow falling
            return true;
        } else {
            // Try breaking block ahead
            BlockPos ahead = entity.blockPosition().relative(entity.getDirection());
            BlockState blockAhead = entity.level().getBlockState(ahead);
            if (blockAhead.canOcclude()) {
                System.out.println("[AMB-STUCK] Would break block at " + ahead + " to clear path");
                // Placeholder - actual block breaking would go here
                return true;
            }
        }

        return false;
    }

    /**
     * Level 4 Recovery: LLM override for complex situations
     */
    private static boolean executeLevel4Recovery(LivingEntity entity, BlockPos goal) {
        System.out.println("[AMB-STUCK] Level 4 Recovery: LLM override needed");
        System.out.println("[AMB-STUCK] Entity at " + entity.blockPosition() + ", goal at " + goal);

        // Scan nearby area for context
        StringBuilder nearbyBlocks = new StringBuilder();
        BlockPos pos = entity.blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    BlockState state = entity.level().getBlockState(checkPos);
                    if (!state.isAir()) {
                        nearbyBlocks.append(state.getBlock().getName().getString()).append(" ");
                    }
                }
            }
        }

        System.out.println("[AMB-STUCK] Nearby blocks: " + nearbyBlocks.toString());

        // ENHANCED: Call LLM for assistance if entity is AmbNpcEntity
        if (entity instanceof AmbNpcEntity bot) {
            String reason = String.format("Stuck at %s, goal %s, nearby: %s",
                pos, goal, nearbyBlocks.toString());

            String llmResponse = LLMInterface.requestLLMAssistance(bot, reason);
            List<LLMInterface.ParsedAction> actions = LLMInterface.parseResponse(llmResponse);

            if (!actions.isEmpty()) {
                LLMInterface.executeActions(bot, actions);
                return true;
            }
        }

        System.out.println("[AMB-STUCK] Suggest: dig/build/replan");
        return false;
    }
}
