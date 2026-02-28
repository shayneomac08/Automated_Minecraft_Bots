package com.shayneomac08.automated_minecraft_bots.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BotMovementHelper {

    public static class MovementState {
        public Vec3 lastPosition = Vec3.ZERO;
        public int stuckTicks = 0;
        public int consecutiveStuckCount = 0;
        public long lastTeleportTime = 0;
    }

    private static final double STUCK_THRESHOLD = 0.05; // More sensitive (was 0.1)
    private static final int STUCK_CHECK_INTERVAL = 40; // Check less often (was 20)
    private static final int MAX_STUCK_TICKS = 200; // Allow more time before unsticking (was 60)

    private BotMovementHelper() {}

    public static boolean checkAndFixStuck(ServerLevel level, LivingEntity body, MovementState state, int currentTick) {
        if (body == null || body.isRemoved()) return false;

        Vec3 currentPos = body.position();

        if (currentTick % STUCK_CHECK_INTERVAL == 0) {
            double distMoved = currentPos.distanceTo(state.lastPosition);

            if (distMoved < STUCK_THRESHOLD) {
                state.stuckTicks += STUCK_CHECK_INTERVAL;

                if (state.stuckTicks >= MAX_STUCK_TICKS) {
                    return unstickBot(level, body, state);
                }
            } else {
                state.stuckTicks = 0;
                state.consecutiveStuckCount = 0;
            }

            state.lastPosition = currentPos;
        }

        return false;
    }

    private static boolean unstickBot(ServerLevel level, LivingEntity body, MovementState state) {
        BlockPos currentPos = body.blockPosition();

        // Strategy 1: Stop navigation and force recalculation (most common fix)
        if (body instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.getNavigation().recomputePath();
            // Only log if truly stuck (reduce spam)
            if (state.consecutiveStuckCount == 0) {
                System.out.println("[AMB] Bot appears stuck at " + currentPos + " - Attempting navigation reset");
            }
            state.stuckTicks = 0;
            state.consecutiveStuckCount++;
            return true;
        }

        // Strategy 2: Navigate to random nearby position (only if Strategy 1 didn't work)
        if (state.consecutiveStuckCount >= 1 && state.consecutiveStuckCount < 3 && body instanceof Mob mob) {
            double angle = Math.random() * Math.PI * 2;
            double dist = 3.0 + Math.random() * 3.0;
            double tx = body.getX() + Math.cos(angle) * dist;
            double tz = body.getZ() + Math.sin(angle) * dist;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                   (int)Math.floor(tx), (int)Math.floor(tz));

            mob.getNavigation().moveTo(tx, y, tz, 1.25);
            System.out.println("[AMB] Unsticking bot - Random navigation to " + tx + "," + y + "," + tz);
            state.consecutiveStuckCount++;
            state.stuckTicks = 0;
            return true;
        }

        // Strategy 3: Check if stuck in block and teleport out
        if (isStuckInBlock(level, currentPos)) {
            Vec3 escapePos = findNearestSafePosition(level, currentPos);
            if (escapePos != null) {
                body.teleportTo(escapePos.x, escapePos.y, escapePos.z);
                body.setDeltaMovement(Vec3.ZERO);
                System.out.println("[AMB] Bot stuck in block - Teleported to safe position: " + escapePos);
                state.lastTeleportTime = System.currentTimeMillis();
                state.stuckTicks = 0;
                state.consecutiveStuckCount = 0;
                return true;
            }
        }

        // Strategy 4: Last resort teleport to nearby safe position
        if (state.consecutiveStuckCount >= 5) {
            Vec3 safePos = findSafePositionNearby(level, currentPos);
            if (safePos != null) {
                body.teleportTo(safePos.x, safePos.y, safePos.z);
                body.setDeltaMovement(Vec3.ZERO);
                System.out.println("[AMB] Bot severely stuck - Emergency teleport to: " + safePos);
                state.lastTeleportTime = System.currentTimeMillis();
                state.stuckTicks = 0;
                state.consecutiveStuckCount = 0;
                return true;
            }
        }

        state.stuckTicks = 0;
        return false;
    }

    private static boolean isStuckInBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        return (!state.isAir() && state.canOcclude()) || (!above.isAir() && above.canOcclude());
    }

    private static Vec3 findNearestSafePosition(ServerLevel level, BlockPos stuck) {
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos check = stuck.offset(dx, dy, dz);
                    if (isSafePosition(level, check)) {
                        return Vec3.atBottomCenterOf(check);
                    }
                }
            }
        }
        return null;
    }

    private static Vec3 findSafePositionNearby(ServerLevel level, BlockPos center) {
        int radius = 5;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    if (isSafePosition(level, check)) {
                        return Vec3.atBottomCenterOf(check);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafePosition(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockPos head = pos.above();

        BlockState belowState = level.getBlockState(below);
        BlockState feetState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(head);

        return belowState.canOcclude() && feetState.isAir() && headState.isAir() && level.getFluidState(pos).isEmpty();
    }
}