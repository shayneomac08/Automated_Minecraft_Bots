package com.shayneomac08.automated_minecraft_bots.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * REALISTIC PLAYER-LIKE ACTIONS
 * Makes bots perform actions like real players: mining, crafting, eating, fighting
 */
public class RealisticActions {

    /**
     * Mining state tracker for progressive block breaking
     */
    public static class MiningState {
        public BlockPos targetBlock = BlockPos.ZERO;
        public int miningTicks = 0;
        public int requiredTicks = 0;
        public boolean isMining = false;

        public void reset() {
            targetBlock = BlockPos.ZERO;
            miningTicks = 0;
            requiredTicks = 0;
            isMining = false;
        }
    }

    /**
     * Start mining a block (realistic progressive mining)
     */
    public static void startMining(FakePlayer player, BlockPos target, MiningState state) {
        if (player == null || target == null || state == null) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockState blockState = level.getBlockState(target);

        if (blockState.isAir()) {
            state.reset();
            return;
        }

        // Calculate mining time based on block hardness and tool
        float hardness = blockState.getDestroySpeed(level, target);
        ItemStack tool = player.getMainHandItem();
        float speedMultiplier = tool.getDestroySpeed(blockState);

        // Calculate ticks required (realistic mining time)
        int baseTicks = (int) (hardness * 20); // 20 ticks per hardness unit
        state.requiredTicks = Math.max(1, (int) (baseTicks / Math.max(1, speedMultiplier)));

        state.targetBlock = target;
        state.miningTicks = 0;
        state.isMining = true;

        // Look at the block being mined
        RealisticMovement.lookAt(player, Vec3.atCenterOf(target));

        // Start breaking animation
        level.destroyBlockProgress(player.getId(), target, 0);
    }

    /**
     * Continue mining a block (call every tick)
     * Returns true when block is broken
     */
    public static boolean continueMining(FakePlayer player, MiningState state) {
        if (player == null || state == null || !state.isMining) return false;

        ServerLevel level = (ServerLevel) player.level();
        BlockState blockState = level.getBlockState(state.targetBlock);

        // Block was already broken or changed
        if (blockState.isAir()) {
            state.reset();
            level.destroyBlockProgress(player.getId(), state.targetBlock, -1);
            return true;
        }

        // Increment mining progress
        state.miningTicks++;

        // Update breaking animation (0-9 stages)
        int stage = Math.min(9, (state.miningTicks * 10) / state.requiredTicks);
        level.destroyBlockProgress(player.getId(), state.targetBlock, stage);

        // Keep looking at the block
        RealisticMovement.lookAt(player, Vec3.atCenterOf(state.targetBlock));

        // Swing arm for visual feedback
        if (state.miningTicks % 4 == 0) {
            player.swing(InteractionHand.MAIN_HAND);
        }

        // Check if mining is complete
        if (state.miningTicks >= state.requiredTicks) {
            // Break the block
            player.gameMode.destroyBlock(state.targetBlock);
            level.destroyBlockProgress(player.getId(), state.targetBlock, -1);
            state.reset();
            return true;
        }

        return false;
    }

    /**
     * Stop mining (cancel current mining operation)
     */
    public static void stopMining(FakePlayer player, MiningState state) {
        if (player == null || state == null) return;

        if (state.isMining) {
            ServerLevel level = (ServerLevel) player.level();
            level.destroyBlockProgress(player.getId(), state.targetBlock, -1);
            state.reset();
        }
    }

    /**
     * Equip the best tool for a specific block type
     */
    public static void equipBestTool(FakePlayer player, BlockState blockState) {
        if (player == null || blockState == null) return;

        ItemStack bestTool = ItemStack.EMPTY;
        float bestSpeed = 1.0f;

        // Check all inventory slots for the best tool
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                float speed = stack.getDestroySpeed(blockState);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestTool = stack;
                }
            }
        }

        // Equip the best tool
        if (!bestTool.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, bestTool.copy());
        }
    }

    /**
     * Eat food from inventory (realistic eating with animation)
     */
    public static boolean eatFood(FakePlayer player) {
        if (player == null) return false;

        // Find food in inventory
        ItemStack food = findFood(player);
        if (food.isEmpty()) return false;

        // Get food properties
        int hungerRestored = 0;
        if (food.is(Items.COOKED_BEEF) || food.is(Items.COOKED_PORKCHOP)) {
            hungerRestored = 8;
        } else if (food.is(Items.COOKED_CHICKEN) || food.is(Items.COOKED_MUTTON)) {
            hungerRestored = 6;
        } else if (food.is(Items.BREAD)) {
            hungerRestored = 5;
        } else if (food.is(Items.BEEF) || food.is(Items.PORKCHOP)) {
            hungerRestored = 3;
        } else if (food.is(Items.CHICKEN) || food.is(Items.MUTTON)) {
            hungerRestored = 2;
        } else if (food.is(Items.APPLE)) {
            hungerRestored = 4;
        }

        // Restore hunger
        player.getFoodData().eat(hungerRestored, 0.6f);

        // Remove from inventory
        food.shrink(1);

        // Swing arm for eating animation
        player.swing(InteractionHand.MAIN_HAND);

        return true;
    }

    /**
     * Find food in inventory
     */
    private static ItemStack findFood(FakePlayer player) {
        // Priority order: cooked meat > raw meat > bread > apples
        ItemStack[] foodPriority = {
            new ItemStack(Items.COOKED_BEEF),
            new ItemStack(Items.COOKED_PORKCHOP),
            new ItemStack(Items.COOKED_CHICKEN),
            new ItemStack(Items.COOKED_MUTTON),
            new ItemStack(Items.BREAD),
            new ItemStack(Items.BEEF),
            new ItemStack(Items.PORKCHOP),
            new ItemStack(Items.CHICKEN),
            new ItemStack(Items.MUTTON),
            new ItemStack(Items.APPLE)
        };

        for (ItemStack foodType : foodPriority) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(foodType.getItem())) {
                    return stack;
                }
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Attack an entity (realistic combat)
     */
    public static void attackEntity(FakePlayer player, LivingEntity target) {
        if (player == null || target == null) return;

        // Look at target
        RealisticMovement.lookAt(player, target.getEyePosition());

        // Equip best weapon
        equipBestWeapon(player);

        // Attack with cooldown (realistic combat timing)
        if (player.getAttackStrengthScale(0.5f) >= 0.9f) {
            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND);
            player.resetAttackStrengthTicker();
        }
    }

    /**
     * Equip the best weapon from inventory
     */
    private static void equipBestWeapon(FakePlayer player) {
        ItemStack bestWeapon = ItemStack.EMPTY;
        float bestDamage = 1.0f; // Fist damage

        // Check inventory for weapons
        ItemStack[] weapons = {
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.STONE_SWORD),
            new ItemStack(Items.WOODEN_SWORD),
            new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.IRON_AXE),
            new ItemStack(Items.STONE_AXE),
            new ItemStack(Items.WOODEN_AXE)
        };

        for (ItemStack weaponType : weapons) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(weaponType.getItem())) {
                    // Found a weapon - equip it
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                    return;
                }
            }
        }
    }

    /**
     * Place a block at a position (realistic building)
     */
    public static boolean placeBlock(FakePlayer player, BlockPos pos, Block block) {
        if (player == null || pos == null || block == null) return false;

        ServerLevel level = (ServerLevel) player.level();

        // Check if position is valid for placement
        if (!level.getBlockState(pos).isAir()) return false;

        // Find block in inventory
        ItemStack blockItem = findBlockInInventory(player, block);
        if (blockItem.isEmpty()) return false;

        // Equip block
        player.setItemInHand(InteractionHand.MAIN_HAND, blockItem.copy());

        // Look at placement position
        RealisticMovement.lookAt(player, Vec3.atCenterOf(pos));

        // Place block
        level.setBlock(pos, block.defaultBlockState(), 3);

        // Remove from inventory
        blockItem.shrink(1);

        // Swing arm
        player.swing(InteractionHand.MAIN_HAND);

        return true;
    }

    /**
     * Find a specific block in inventory
     */
    private static ItemStack findBlockInInventory(FakePlayer player, Block block) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == block.asItem()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Check if player has a specific item
     */
    public static boolean hasItem(FakePlayer player, ItemStack item) {
        if (player == null || item == null) return false;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item.getItem()) && stack.getCount() >= item.getCount()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Count how many of an item the player has
     */
    public static int countItem(FakePlayer player, ItemStack item) {
        if (player == null || item == null) return 0;

        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item.getItem())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Drop an item from inventory (realistic item dropping)
     */
    public static void dropItem(FakePlayer player, ItemStack item) {
        if (player == null || item == null || item.isEmpty()) return;

        player.drop(item, false);
    }

    /**
     * Interact with a block (right-click action)
     */
    public static void interactWithBlock(FakePlayer player, BlockPos pos) {
        if (player == null || pos == null) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(pos);

        // Look at block
        RealisticMovement.lookAt(player, Vec3.atCenterOf(pos));

        // Use block (opens crafting table, furnace, chest, etc.)
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(
                Vec3.atCenterOf(pos),
                net.minecraft.core.Direction.UP,
                pos,
                false
            )
        );

        // Swing arm
        player.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Check if bot is hungry and should eat
     */
    public static boolean shouldEat(FakePlayer player) {
        if (player == null) return false;
        return player.getFoodData().getFoodLevel() < 14; // Eat when hunger < 14
    }

    /**
     * Check if bot is low on health
     */
    public static boolean isLowHealth(FakePlayer player) {
        if (player == null) return false;
        return player.getHealth() < 10.0f; // Low health threshold
    }

    /**
     * Check if bot is critically low on health
     */
    public static boolean isCriticalHealth(FakePlayer player) {
        if (player == null) return false;
        return player.getHealth() < 5.0f; // Critical health threshold
    }
}
