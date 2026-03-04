package com.shayneomac08.automated_minecraft_bots.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * TASK VALIDATION SYSTEM
 * Validates goals before setting them, checks reachability and task requirements
 */
public class TaskValidation {

    /**
     * Validation result
     */
    public static class ValidationResult {
        public boolean isValid;
        public String reason;
        public BlockPos adjustedGoal; // Alternative goal if original is invalid

        public ValidationResult(boolean isValid, String reason, BlockPos adjustedGoal) {
            this.isValid = isValid;
            this.reason = reason;
            this.adjustedGoal = adjustedGoal;
        }

        public static ValidationResult valid(BlockPos goal) {
            return new ValidationResult(true, "Valid", goal);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason, BlockPos.ZERO);
        }

        public static ValidationResult adjusted(String reason, BlockPos newGoal) {
            return new ValidationResult(true, reason, newGoal);
        }
    }

    /**
     * Validate a goal before setting it
     */
    public static ValidationResult validateGoal(FakePlayer player, BlockPos goal, String task) {
        if (player == null || goal == null || goal.equals(BlockPos.ZERO)) {
            return ValidationResult.invalid("Null or zero goal");
        }

        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();

        // Check 1: Vertical reachability (max 5 blocks without climbing)
        int verticalDiff = Math.abs(goal.getY() - playerPos.getY());
        if (verticalDiff > 5) {
            // Try to find a goal at the same level
            BlockPos adjustedGoal = findSameLevelAlternative(level, goal, playerPos, task);
            if (adjustedGoal != null && !adjustedGoal.equals(BlockPos.ZERO)) {
                return ValidationResult.adjusted("Goal too high (" + verticalDiff + " blocks), using same-level alternative", adjustedGoal);
            }
            return ValidationResult.invalid("Goal is " + verticalDiff + " blocks away vertically (unreachable)");
        }

        // Check 2: Horizontal distance (max 50 blocks for now)
        double horizontalDist = Math.sqrt(
            Math.pow(goal.getX() - playerPos.getX(), 2) +
            Math.pow(goal.getZ() - playerPos.getZ(), 2)
        );
        if (horizontalDist > 50) {
            return ValidationResult.invalid("Goal is too far away (" + horizontalDist + " blocks)");
        }

        // Check 3: Block at goal is mineable/valid for task
        BlockState goalState = level.getBlockState(goal);
        if (!isValidForTask(goalState, task)) {
            return ValidationResult.invalid("Block at goal is not valid for task '" + task + "'");
        }

        // Check 4: Goal is in loaded chunks
        if (!level.isLoaded(goal)) {
            return ValidationResult.invalid("Goal is in unloaded chunks");
        }

        // Check 5: Path exists (basic check - not blocked by bedrock or void)
        if (goal.getY() < 0 || goal.getY() > 320) {
            return ValidationResult.invalid("Goal is outside world bounds");
        }

        return ValidationResult.valid(goal);
    }

    /**
     * Check if a block is valid for the given task
     */
    private static boolean isValidForTask(BlockState state, String task) {
        if (state.isAir()) return false;

        switch (task) {
            case "gather_wood":
                return state.is(BlockTags.LOGS);
            case "mine_stone":
                return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) ||
                       state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) ||
                       state.is(Blocks.GRANITE) || state.is(Blocks.DEEPSLATE);
            case "mine_ore":
                return state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE) ||
                       state.is(Blocks.COPPER_ORE) || state.is(Blocks.GOLD_ORE) ||
                       state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.EMERALD_ORE) ||
                       state.is(Blocks.LAPIS_ORE) || state.is(Blocks.REDSTONE_ORE) ||
                       state.is(Blocks.DEEPSLATE_COAL_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE) ||
                       state.is(Blocks.DEEPSLATE_COPPER_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) ||
                       state.is(Blocks.DEEPSLATE_DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE);
            case "mine_dirt":
                return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) ||
                       state.is(Blocks.COARSE_DIRT);
            default:
                return true; // Unknown task, allow any block
        }
    }

    /**
     * Find an alternative goal at the same Y level
     */
    private static BlockPos findSameLevelAlternative(ServerLevel level, BlockPos original, BlockPos playerPos, String task) {
        int searchRadius = 20;
        BlockPos bestAlternative = BlockPos.ZERO;
        double bestScore = Double.MAX_VALUE;

        // Search for blocks at the same Y level as the player
        int targetY = playerPos.getY();

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                // Search at player's Y level and ±2 blocks
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos checkPos = playerPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);

                    if (isValidForTask(state, task)) {
                        // Calculate score (prefer closer blocks)
                        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                        double verticalDist = Math.abs(dy);
                        double score = horizontalDist + (verticalDist * 2.0);

                        if (score < bestScore) {
                            bestScore = score;
                            bestAlternative = checkPos;
                        }
                    }
                }
            }
        }

        return bestAlternative;
    }

    /**
     * Check if a goal is reachable (basic pathfinding check)
     */
    public static boolean isGoalReachable(FakePlayer player, BlockPos goal) {
        if (player == null || goal == null) return false;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();

        // Check vertical reachability
        int verticalDiff = Math.abs(goal.getY() - playerPos.getY());
        if (verticalDiff > 5) {
            return false; // Too high/low without climbing
        }

        // Check if goal is in loaded chunks
        if (!level.isLoaded(goal)) {
            return false;
        }

        // Check if there's a clear path (simplified - just check if not blocked by bedrock)
        BlockState goalState = level.getBlockState(goal);
        if (goalState.is(Blocks.BEDROCK) || goalState.is(Blocks.BARRIER)) {
            return false;
        }

        return true;
    }

    /**
     * Validate that player has the right tools for a task
     */
    public static boolean hasRequiredTools(FakePlayer player, String task) {
        switch (task) {
            case "gather_wood":
                // Can gather wood with hands, but axe is better
                return true;
            case "mine_stone":
                // Need at least a wooden pickaxe
                return player.getInventory().countItem(net.minecraft.world.item.Items.WOODEN_PICKAXE) > 0 ||
                       player.getInventory().countItem(net.minecraft.world.item.Items.STONE_PICKAXE) > 0 ||
                       player.getInventory().countItem(net.minecraft.world.item.Items.IRON_PICKAXE) > 0 ||
                       player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND_PICKAXE) > 0;
            case "mine_ore":
                // Need at least a stone pickaxe for most ores
                return player.getInventory().countItem(net.minecraft.world.item.Items.STONE_PICKAXE) > 0 ||
                       player.getInventory().countItem(net.minecraft.world.item.Items.IRON_PICKAXE) > 0 ||
                       player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND_PICKAXE) > 0;
            default:
                return true; // Unknown task, assume tools are available
        }
    }
}
