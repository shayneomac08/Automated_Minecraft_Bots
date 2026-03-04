package com.shayneomac08.automated_minecraft_bots.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * VERTICAL NAVIGATION SYSTEM
 * Handles multi-block jumps, climbing, and block placement for vertical movement
 */
public class VerticalNavigation {

    /**
     * Calculate cost for vertical movement in pathfinding
     * Returns additional cost to add to node
     */
    public static double getVerticalMovementCost(BlockPos from, BlockPos to, ServerLevel level) {
        int verticalDiff = to.getY() - from.getY();

        if (verticalDiff == 0) {
            return 0.0; // No vertical movement
        } else if (verticalDiff == 1) {
            // Jumping up 1 block
            return 0.5;
        } else if (verticalDiff == 2) {
            // Jumping up 2 blocks (requires block placement or special terrain)
            BlockState blockAt = level.getBlockState(from.above());
            if (canPlaceBlockAt(level, from.above())) {
                return 2.0; // Need to place a block
            } else {
                return 10.0; // Can't easily reach
            }
        } else if (verticalDiff > 2) {
            // Need multiple block placements or ladder
            return 2.0 + (verticalDiff - 2) * 1.5;
        } else if (verticalDiff == -1) {
            // Falling down 1 block
            return 0.3;
        } else if (verticalDiff < -1) {
            // Falling down multiple blocks - check for safe landing
            if (isSafeFall(level, from, to)) {
                return 0.5;
            } else {
                return 15.0; // Dangerous fall
            }
        }

        return 0.0;
    }

    /**
     * Check if entity can jump to reach a position
     */
    public static boolean canJumpTo(LivingEntity entity, BlockPos target) {
        BlockPos current = entity.blockPosition();
        int verticalDiff = target.getY() - current.getY();

        // Can jump up 1 block normally
        if (verticalDiff == 1) {
            return entity.onGround();
        }

        // Can't jump higher than 1 block without assistance
        return verticalDiff <= 1;
    }

    /**
     * Attempt to navigate vertically to a position
     * Returns true if vertical movement was initiated
     */
    public static boolean navigateVertically(FakePlayer player, BlockPos target) {
        BlockPos current = player.blockPosition();
        int verticalDiff = target.getY() - current.getY();

        if (verticalDiff == 0) {
            return false; // No vertical movement needed
        }

        if (verticalDiff > 0) {
            // Need to go up
            return navigateUp(player, target, verticalDiff);
        } else {
            // Need to go down
            return navigateDown(player, target, -verticalDiff);
        }
    }

    /**
     * Navigate upward to a higher position
     */
    private static boolean navigateUp(FakePlayer player, BlockPos target, int height) {
        if (height == 1 && player.onGround()) {
            // Simple jump
            player.setDeltaMovement(player.getDeltaMovement().x, 0.42, player.getDeltaMovement().z);
            System.out.println("[AMB-VERTICAL] Jumping up 1 block");
            return true;
        } else if (height == 2) {
            // Need to place a block or find stairs
            BlockPos placePos = player.blockPosition();
            if (canPlaceBlockAt((ServerLevel) player.level(), placePos)) {
                // Would place block here - placeholder
                System.out.println("[AMB-VERTICAL] Would place block at " + placePos + " to climb");
                return true;
            }
        } else if (height > 2) {
            // Need multiple blocks or ladder
            System.out.println("[AMB-VERTICAL] Need to place " + height + " blocks to climb");
            return false; // Too high for now
        }

        return false;
    }

    /**
     * Navigate downward to a lower position
     */
    private static boolean navigateDown(FakePlayer player, BlockPos target, int depth) {
        if (depth <= 3) {
            // Safe to fall
            System.out.println("[AMB-VERTICAL] Falling down " + depth + " blocks");
            return true; // Just walk off the edge
        } else {
            // Need to place blocks or find stairs
            System.out.println("[AMB-VERTICAL] Fall of " + depth + " blocks is too dangerous");
            return false;
        }
    }

    /**
     * Check if a block can be placed at a position
     */
    private static boolean canPlaceBlockAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.canOcclude();
    }

    /**
     * Check if a fall is safe (won't cause damage)
     */
    private static boolean isSafeFall(ServerLevel level, BlockPos from, BlockPos to) {
        int fallDistance = from.getY() - to.getY();

        // Falls of 3 blocks or less are safe
        if (fallDistance <= 3) {
            return true;
        }

        // Check for water or other safe landing
        BlockState landingBlock = level.getBlockState(to);
        if (landingBlock.is(Blocks.WATER)) {
            return true;
        }

        // Falls of more than 3 blocks are dangerous
        return false;
    }

    /**
     * Get neighbors including vertical positions for A* pathfinding
     */
    public static void addVerticalNeighbors(BlockPos pos, ServerLevel level, java.util.List<BlockPos> neighbors) {
        // Add positions 1 block up (if can jump)
        BlockPos up = pos.above();
        if (isPassableVertical(level, up)) {
            neighbors.add(up);
        }

        // Add positions 1 block down (if safe to fall)
        BlockPos down = pos.below();
        if (isPassableVertical(level, down) && isSafeFall(level, pos, down)) {
            neighbors.add(down);
        }

        // Add positions 2 blocks up (if can place block)
        BlockPos up2 = pos.above(2);
        if (canPlaceBlockAt(level, pos.above()) && isPassableVertical(level, up2)) {
            neighbors.add(up2);
        }
    }

    /**
     * Check if a vertical position is passable
     */
    private static boolean isPassableVertical(ServerLevel level, BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState below = level.getBlockState(pos.below());

        // Must have clear space for body
        boolean feetClear = !feet.canOcclude() || feet.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
        boolean headClear = !head.canOcclude() || head.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;

        // Must have solid ground or be able to place a block
        boolean hasGround = below.canOcclude() || canPlaceBlockAt(level, pos.below());

        return feetClear && headClear && hasGround;
    }
}
