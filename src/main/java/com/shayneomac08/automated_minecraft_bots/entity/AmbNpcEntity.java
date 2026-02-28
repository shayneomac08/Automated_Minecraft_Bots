package com.shayneomac08.automated_minecraft_bots.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * FakePlayer-based bot entity with REAL player inventory (36 slots)
 * LLM-controlled through programmatic actions
 * NO AI GOALS - fully manual/LLM control
 */
public class AmbNpcEntity extends FakePlayer {

    // ==================== DUMMY CONNECTION FOR FAKEPLAYER ====================
    // FakePlayer requires a connection - we use a minimal implementation

    // ==================== BOT STATE ====================

    private boolean brainEnabled = true;
    private String group = "none";
    private String currentTask = "idle";
    private BlockPos targetPos = null;
    private int taskTicks = 0;
    private Vec3 moveTarget = null;
    private float moveSpeed = 0.2f;

    // Navigation optimization
    private int pathCooldown = 0;
    private int stuckTimer = 0;
    private BlockPos lastPos = BlockPos.ZERO;

    // Mining state (for player-like block breaking)
    private BlockPos miningBlock = null;
    private int miningProgress = 0;
    private int miningTotalTime = 0;

    // Surroundings awareness for LLM
    private String surroundingsInfo = "";
    private BlockPos bedPos = null; // Respawn point

    // ==================== CONSTRUCTOR ====================

    public AmbNpcEntity(ServerLevel level, GameProfile profile) {
        super(level, profile);

        // Initialize bot
        this.brainEnabled = true;
        this.setHealth(20.0f);
        this.getFoodData().setFoodLevel(20);
        this.setCustomNameVisible(true);

        System.out.println("[AMB] Created FakePlayer bot: " + profile.name());
    }

    // ==================== FAKEPLAYER OVERRIDES ====================

    @Override
    public boolean isSilent() {
        return true; // Silent movement
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // No step sounds
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        // Only play hurt/death sounds
        if (sound == SoundEvents.PLAYER_HURT || sound == SoundEvents.PLAYER_DEATH) {
            super.playSound(sound, volume, pitch);
        }
    }

    @Override
    public float getSoundVolume() {
        return 0.0f; // Silent
    }

    @Override
    protected void spawnSprintParticle() {
        // No sprint particles
    }

    // ==================== LIFECYCLE METHODS ====================

    @Override
    public void remove(RemovalReason reason) {
        // Clean up when bot is removed
        System.out.println("[AMB] Removing bot: " + getGameProfile().name());
        super.remove(reason);
    }

    // ==================== TICK - MAIN CONTROL LOOP ====================

    @Override
    public void tick() {
        super.tick(); // FakePlayer tick - handles inventory, movement, etc.

        if (!brainEnabled) return;

        // ===== FEATURE 1: STUCK DETECTION =====
        // Now using proper player movement physics, so stuck detection should work correctly
        if (moveTarget != null) {
            stuckTimer++;

            // If bot hasn't moved in 60 ticks (3 seconds) while trying to move, try to unstuck
            if (stuckTimer > 60 && blockPosition().distSqr(lastPos) < 0.5) {
                // Try jumping to unstuck
                if (onGround()) {
                    setDeltaMovement(getDeltaMovement().add(0, 0.42, 0)); // Standard jump
                }

                // If still stuck after 120 ticks (6 seconds), clear the target
                if (stuckTimer > 120) {
                    broadcastGroupChat("Can't reach target, giving up");
                    stopMovement();
                }

                stuckTimer = 0;
            }
            lastPos = blockPosition();
        } else {
            // Reset stuck timer when not moving
            stuckTimer = 0;
            lastPos = blockPosition();
        }

        pathCooldown--;

        // ===== FEATURE 2: AUTO DOOR OPENING =====
        attemptOpenDoors();

        // Process current task
        if (currentTask != null && !currentTask.equals("idle")) {
            processTask();
        }

        // Auto-pickup nearby items every 10 ticks
        if (tickCount % 10 == 0) {
            pickupNearbyItems();
            // Auto-craft tools when materials are available
            autoCraftTools();
            // Equip best tool in hand
            equipBestTool();
            // Update surroundings for LLM awareness
            updateSurroundingsForLLM();
        }

        // Move towards target if set (with path cooldown optimization)
        if (moveTarget != null) {
            // Debug: Log movement target every 100 ticks (5 seconds)
            if (tickCount % 100 == 0) {
                broadcastGroupChat("Moving to: " + String.format("%.1f, %.1f, %.1f (dist: %.1f)",
                    moveTarget.x, moveTarget.y, moveTarget.z, position().distanceTo(moveTarget)));
            }
            moveTowardsTargetOptimized();
        }
    }

    // ==================== MOVEMENT CONTROL ====================

    /**
     * Set a movement target for the bot
     */
    public void setMoveTarget(Vec3 target, float speed) {
        this.moveTarget = target;
        this.moveSpeed = speed;
    }

    /**
     * Set a movement target from BlockPos
     */
    public void setMoveTarget(BlockPos pos, float speed) {
        this.moveTarget = Vec3.atCenterOf(pos);
        this.moveSpeed = speed;
    }

    /**
     * Stop movement
     */
    public void stopMovement() {
        this.moveTarget = null;
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * Move towards the current target (legacy method - use moveTowardsTargetOptimized instead)
     */
    private void moveTowardsTarget() {
        // Redirect to optimized version with proper physics
        moveTowardsTargetOptimized();
    }

    /**
     * ===== FEATURE 3: OPTIMIZED MOVEMENT WITH PROPER FAKEPLAYER PHYSICS =====
     * Move towards target using player movement simulation (not simple velocity)
     */
    private void moveTowardsTargetOptimized() {
        if (moveTarget == null) return;

        Vec3 currentPos = position();

        // Check if reached target
        if (currentPos.distanceTo(moveTarget) < 0.5) {
            stopMovement();
            stuckTimer = 0; // Reset stuck timer when reaching target
            pathCooldown = 0;
            return;
        }

        // Calculate direction to target
        Vec3 direction = moveTarget.subtract(currentPos).normalize();

        // Calculate yaw angle to face target
        double dx = moveTarget.x - currentPos.x;
        double dz = moveTarget.z - currentPos.z;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        // Smoothly rotate towards target
        setYRot(targetYaw);
        setYHeadRot(targetYaw);
        yBodyRot = targetYaw;

        // Look at target for proper head rotation
        lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, moveTarget);

        // PROPER PLAYER MOVEMENT PHYSICS
        // We need to simulate player movement like the client does

        // Get current velocity
        Vec3 currentVelocity = getDeltaMovement();

        // Calculate horizontal movement direction (forward toward target)
        Vec3 horizontalMovement = new Vec3(direction.x, 0, direction.z).normalize();

        // Player walking speed: ~0.1 blocks/tick, sprinting: ~0.13 blocks/tick
        // We'll use a conservative 0.1 for walking speed
        double targetSpeed = 0.1; // Realistic walking speed

        // Apply movement with friction (0.91 is Minecraft's ground friction)
        double friction = onGround() ? 0.91 : 0.98; // Air friction is less

        // Calculate new horizontal velocity
        double newX = horizontalMovement.x * targetSpeed;
        double newZ = horizontalMovement.z * targetSpeed;

        // Apply gravity to Y velocity
        double newY = currentVelocity.y;
        if (!onGround()) {
            newY -= 0.08; // Gravity acceleration
            newY *= 0.98; // Air resistance
        } else {
            newY = -0.0784; // Small downward force to keep on ground (prevents floating)
        }

        // Set the new velocity
        setDeltaMovement(newX, newY, newZ);

        // Apply the movement with collision detection
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());

        // Apply friction after movement (like Minecraft does)
        Vec3 afterMove = getDeltaMovement();
        if (onGround()) {
            setDeltaMovement(afterMove.x * friction, afterMove.y, afterMove.z * friction);
        }

        // Handle jumping over obstacles
        if (pathCooldown <= 0) {
            // Check if there's a block in front that needs jumping
            BlockPos frontPos = blockPosition().relative(getDirection());
            BlockPos aboveFront = frontPos.above();

            if (!level().getBlockState(frontPos).isAir() &&
                level().getBlockState(aboveFront).isAir() &&
                onGround()) {
                // Jump to get over obstacle
                setDeltaMovement(getDeltaMovement().add(0, 0.42, 0)); // Standard jump velocity
            }

            pathCooldown = 12; // Reset cooldown
        }
    }

    /**
     * ===== FEATURE 2: AUTO DOOR OPENING =====
     * Automatically open doors the bot is looking at
     */
    private void attemptOpenDoors() {
        // Check what the bot is looking at (4 blocks ahead)
        HitResult hit = pick(4.0, 1.0f, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos doorPos = blockHit.getBlockPos();
            BlockState state = level().getBlockState(doorPos);

            // If it's a door, toggle it
            if (state.getBlock() instanceof DoorBlock) {
                boolean isOpen = state.getValue(DoorBlock.OPEN);

                // Open closed doors, close open doors
                level().setBlock(doorPos, state.setValue(DoorBlock.OPEN, !isOpen), 3);

                // Play door sound
                if (!isOpen) {
                    playSound(SoundEvents.WOODEN_DOOR_OPEN, 1.0f, 1.0f);
                } else {
                    playSound(SoundEvents.WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
                }
            }
        }
    }

    // ==================== TASK SYSTEM ====================

    /**
     * Set the current task for the bot
     */
    public void setTask(String task) {
        this.currentTask = task;
        this.taskTicks = 0;
        System.out.println("[AMB] " + getName().getString() + " starting task: " + task);
    }

    /**
     * Get current task
     */
    public String getCurrentTask() {
        return currentTask;
    }

    /**
     * Process the current task
     */
    private void processTask() {
        taskTicks++;

        switch (currentTask) {
            case "gather_wood" -> gatherWood();
            case "mine_stone" -> mineStone();
            case "build_shelter" -> buildShelter();
            case "craft" -> craft();
            case "place_crafting_table" -> placeCraftingTable();
            case "explore" -> explore();
            case "hunt_animals" -> huntAnimals();
            case "manage_resources" -> manageResources();
            case "idle" -> {} // Do nothing
            default -> {
                // Unknown task - treat as explore
                broadcastGroupChat("Don't know how to '" + currentTask + "', exploring instead");
                currentTask = "explore";
            }
        }
    }

    // ==================== TASK IMPLEMENTATIONS ====================

    private void gatherWood() {
        // Find nearest tree (oak log)
        BlockPos nearestLog = findNearestBlock(Blocks.OAK_LOG, 32);

        if (nearestLog != null) {
            // Check if block is within reach (4.5 blocks for players)
            double distance = position().distanceTo(Vec3.atCenterOf(nearestLog));
            if (distance > 4.5) {
                setMoveTarget(nearestLog, 0.2f);
            } else {
                // Stop moving and mine the log
                stopMovement();
                mineBlockLikePlayer(nearestLog);
            }
        } else {
            broadcastGroupChat("No trees nearby!");
            currentTask = "idle";
        }
    }

    private void mineStone() {
        // Find nearest stone/cobblestone/dirt
        BlockPos nearestStone = findNearestBlock(Blocks.STONE, 32);
        if (nearestStone == null) {
            nearestStone = findNearestBlock(Blocks.COBBLESTONE, 32);
        }
        if (nearestStone == null) {
            nearestStone = findNearestBlock(Blocks.DIRT, 32);
        }

        if (nearestStone != null) {
            // Check if block is within reach (4.5 blocks for players)
            double distance = position().distanceTo(Vec3.atCenterOf(nearestStone));
            if (distance > 4.5) {
                setMoveTarget(nearestStone, 0.2f);
            } else {
                // Stop moving and mine the stone
                stopMovement();
                mineBlockLikePlayer(nearestStone);
            }
        } else {
            broadcastGroupChat("No stone or dirt nearby!");
            currentTask = "idle";
        }
    }

    /**
     * Mine a block like a player would - with proper timing and item drops
     * NOW ENFORCES TOOL REQUIREMENTS (real player rules)
     */
    private void mineBlockLikePlayer(BlockPos pos) {
        if (level().isClientSide()) return;

        BlockState state = level().getBlockState(pos);
        if (state.isAir()) {
            // Block already broken, reset mining
            miningBlock = null;
            miningProgress = 0;
            return;
        }

        // If we're starting to mine a new block
        if (miningBlock == null || !miningBlock.equals(pos)) {
            miningBlock = pos;
            miningProgress = 0;

            // Calculate mining time based on block hardness
            float hardness = state.getDestroySpeed(level(), pos);

            // Get the tool speed multiplier
            ItemStack heldItem = getMainHandItem();
            float speedMultiplier = heldItem.getDestroySpeed(state);

            // Calculate ticks needed to break (similar to player)
            // Base formula: hardness * 30 / speedMultiplier
            if (hardness < 0) {
                miningTotalTime = -1; // Unbreakable
                return;
            } else if (hardness == 0) {
                miningTotalTime = 1; // Instant break
            } else {
                // Calculate mining time (in ticks)
                float baseTime = hardness * 1.5f; // Hardness to seconds conversion
                float toolEfficiency = speedMultiplier > 1.0f ? speedMultiplier : 1.0f;
                miningTotalTime = Math.max(1, (int)(baseTime * 20 / toolEfficiency)); // Convert to ticks
            }

            broadcastGroupChat("Mining " + state.getBlock().getName().getString() + "...");
        }

        // Increment mining progress
        miningProgress++;

        // Show mining progress with crack animation
        if (level() instanceof ServerLevel serverLevel) {
            // Calculate damage stage (0-9, where 9 is almost broken)
            int damageStage = (int)((float)miningProgress / miningTotalTime * 10);
            if (damageStage > 9) damageStage = 9;

            // Send block damage packet to show cracks
            serverLevel.destroyBlockProgress(getId(), pos, damageStage);
        }

        // Check if mining is complete
        if (miningProgress >= miningTotalTime) {
            // ENFORCE TOOL REQUIREMENTS before breaking
            checkToolRequirements(pos);

            // Break the block like a real player - drops items to ground
            if (level() instanceof ServerLevel serverLevel) {
                // Send block break animation to all players
                serverLevel.destroyBlockProgress(getId(), pos, -1); // -1 = complete break

                // Drop items to ground (like a real player)
                state.getBlock().playerDestroy(serverLevel, this, pos, state,
                    serverLevel.getBlockEntity(pos), getMainHandItem());

                // Remove the block
                serverLevel.removeBlock(pos, false);

                // Award stats (like a real player)
                awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(state.getBlock()));

                broadcastGroupChat("Mined " + state.getBlock().getName().getString() + "!");
            }

            // Reset mining state
            miningBlock = null;
            miningProgress = 0;
            miningTotalTime = 0;
        }
    }

    private void buildShelter() {
        BlockPos base = blockPosition().below();

        // Build simple 5x5 shelter
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos floorPos = base.offset(x, 0, z);
                if (level().getBlockState(floorPos).isAir()) {
                    level().setBlock(floorPos, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                }
            }
        }

        broadcastGroupChat("Built a shelter!");
        currentTask = "idle";
    }

    private void explore() {
        // Wander around randomly to discover new areas
        if (moveTarget == null || position().distanceTo(moveTarget) < 2.0) {
            // Pick a random direction to explore
            double range = 20.0;
            double rx = getX() + (random.nextDouble() * 2 - 1) * range;
            double ry = getY();
            double rz = getZ() + (random.nextDouble() * 2 - 1) * range;
            setMoveTarget(new Vec3(rx, ry, rz), 0.2f);
            broadcastGroupChat("Exploring...");
        }
    }

    private void huntAnimals() {
        // Find nearest passive animal
        if (level() instanceof ServerLevel serverLevel) {
            List<net.minecraft.world.entity.animal.Animal> animals = serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class,
                getBoundingBox().inflate(16.0)
            );

            if (!animals.isEmpty()) {
                net.minecraft.world.entity.animal.Animal nearest = animals.get(0);
                double nearestDist = distanceToSqr(nearest);

                for (net.minecraft.world.entity.animal.Animal animal : animals) {
                    double dist = distanceToSqr(animal);
                    if (dist < nearestDist) {
                        nearest = animal;
                        nearestDist = dist;
                    }
                }

                // Move towards animal
                if (nearestDist > 4.0) {
                    setMoveTarget(nearest.position(), 0.25f);
                } else {
                    // Attack the animal
                    stopMovement();
                    attack(nearest);
                    broadcastGroupChat("Hunting " + nearest.getName().getString());
                }
            } else {
                broadcastGroupChat("No animals nearby to hunt");
                currentTask = "explore"; // Explore to find animals
            }
        }
    }

    private void manageResources() {
        // Look for nearby chests to store items
        BlockPos nearestChest = findNearestBlock(Blocks.CHEST, 16);

        if (nearestChest != null) {
            if (position().distanceTo(Vec3.atCenterOf(nearestChest)) > 3.0) {
                setMoveTarget(nearestChest, 0.2f);
                broadcastGroupChat("Moving to chest...");
            } else {
                stopMovement();
                broadcastGroupChat("At chest, managing resources");
                // TODO: Implement chest interaction
                currentTask = "idle";
            }
        } else {
            broadcastGroupChat("No chest nearby");
            currentTask = "idle";
        }
    }

    private void craft() {
        // ENFORCE PLAYER CRAFTING RULES - must be at crafting table
        if (!enforcePlayerCraftingRules()) {
            broadcastGroupChat("Need to be at a crafting table to craft!");
            currentTask = "idle";
            return;
        }

        // Simple crafting logic - craft planks from logs
        int logCount = countItemInInventory(Items.OAK_LOG);

        if (logCount >= 1) {
            // Remove 1 log, add 4 planks
            removeItemFromInventory(Items.OAK_LOG, 1);
            addItemToInventory(new ItemStack(Items.OAK_PLANKS, 4));
            broadcastGroupChat("Crafted planks!");
        } else {
            broadcastGroupChat("No logs to craft!");
        }

        currentTask = "idle";
    }

    // ==================== INVENTORY HELPERS ====================

    /**
     * Get the bot's REAL player inventory (36 slots)
     */
    public Inventory getBotInventory() {
        return super.getInventory(); // FakePlayer's built-in real inventory
    }

    /**
     * Count items in inventory
     */
    public int countItemInInventory(Item item) {
        int count = 0;
        Inventory inv = getBotInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Add item to inventory
     */
    public boolean addItemToInventory(ItemStack stack) {
        return getBotInventory().add(stack);
    }

    /**
     * Remove item from inventory
     */
    public void removeItemFromInventory(Item item, int amount) {
        int remaining = amount;
        Inventory inv = getBotInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }
    }

    /**
     * Pickup nearby items
     */
    private void pickupNearbyItems() {
        List<ItemEntity> nearbyItems = level().getEntitiesOfClass(
            ItemEntity.class,
            getBoundingBox().inflate(2.0)
        );

        for (ItemEntity itemEntity : nearbyItems) {
            if (!itemEntity.isRemoved()) {
                ItemStack stack = itemEntity.getItem();
                if (addItemToInventory(stack.copy())) {
                    itemEntity.discard();
                    broadcastGroupChat("Picked up " + stack.getCount() + " " + stack.getHoverName().getString());
                }
            }
        }
    }

    /**
     * Auto-craft tools when materials are available (like a real player would)
     * ENFORCES PLAYER RULES: Must have crafting table nearby for 3x3 recipes
     */
    private void autoCraftTools() {
        // Count materials
        int oakLogs = countItemInInventory(Items.OAK_LOG);
        int planks = countItemInInventory(Items.OAK_PLANKS);
        int sticks = countItemInInventory(Items.STICK);
        int cobblestone = countItemInInventory(Items.COBBLESTONE);

        // Auto-craft planks from logs (2x2 recipe - can do in inventory)
        // Craft ALL logs into planks immediately
        while (oakLogs > 0) {
            removeItemFromInventory(Items.OAK_LOG, 1);
            addItemToInventory(new ItemStack(Items.OAK_PLANKS, 4));
            broadcastGroupChat("Crafted 4 planks from oak log");
            oakLogs--;
            planks += 4;
        }

        // Auto-craft sticks from planks (2x2 recipe - can do in inventory)
        // Make sure we have enough sticks for tools
        if (planks >= 2 && sticks < 4) {
            removeItemFromInventory(Items.OAK_PLANKS, 2);
            addItemToInventory(new ItemStack(Items.STICK, 4));
            broadcastGroupChat("Crafted 4 sticks from planks");
            planks -= 2;
            sticks += 4;
        }

        // Auto-craft crafting table if we don't have one (2x2 recipe - can do in inventory)
        int craftingTables = countItemInInventory(Items.CRAFTING_TABLE);
        if (craftingTables == 0 && planks >= 4) {
            removeItemFromInventory(Items.OAK_PLANKS, 4);
            addItemToInventory(new ItemStack(Items.CRAFTING_TABLE));
            broadcastGroupChat("Crafted crafting table! Need to place it to craft tools.");
            planks -= 4;
            craftingTables = 1;
        }

        // Check if we have a crafting table nearby (required for 3x3 recipes)
        boolean hasCraftingTable = isNearCraftingTable();

        // ONLY craft 3x3 recipes if we have a crafting table nearby
        if (hasCraftingTable) {
            // Auto-craft wooden pickaxe if we have materials and no pickaxe
            if (planks >= 3 && sticks >= 2 && !hasPickaxe()) {
                removeItemFromInventory(Items.OAK_PLANKS, 3);
                removeItemFromInventory(Items.STICK, 2);
                addItemToInventory(new ItemStack(Items.WOODEN_PICKAXE));
                broadcastGroupChat("Crafted wooden pickaxe at crafting table!");
            }

            // Auto-craft stone pickaxe if we have cobblestone and no stone/iron pickaxe
            if (cobblestone >= 3 && sticks >= 2 && !hasPickaxe(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)) {
                removeItemFromInventory(Items.COBBLESTONE, 3);
                removeItemFromInventory(Items.STICK, 2);
                addItemToInventory(new ItemStack(Items.STONE_PICKAXE));
                broadcastGroupChat("Crafted stone pickaxe at crafting table!");
            }

            // Auto-craft wooden axe if we have materials and no axe
            if (planks >= 3 && sticks >= 2 && !hasAxe()) {
                removeItemFromInventory(Items.OAK_PLANKS, 3);
                removeItemFromInventory(Items.STICK, 2);
                addItemToInventory(new ItemStack(Items.WOODEN_AXE));
                broadcastGroupChat("Crafted wooden axe at crafting table!");
            }

            // Auto-craft stone axe if we have cobblestone and no stone/iron axe
            if (cobblestone >= 3 && sticks >= 2 && !hasAxe(Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE)) {
                removeItemFromInventory(Items.COBBLESTONE, 3);
                removeItemFromInventory(Items.STICK, 2);
                addItemToInventory(new ItemStack(Items.STONE_AXE));
                broadcastGroupChat("Crafted stone axe at crafting table!");
            }

            // Auto-craft wooden sword if we have materials and no sword
            if (planks >= 2 && sticks >= 1 && !hasSword()) {
                removeItemFromInventory(Items.OAK_PLANKS, 2);
                removeItemFromInventory(Items.STICK, 1);
                addItemToInventory(new ItemStack(Items.WOODEN_SWORD));
                broadcastGroupChat("Crafted wooden sword at crafting table!");
            }

            // Auto-craft stone sword if we have cobblestone and no stone/iron sword
            if (cobblestone >= 2 && sticks >= 1 && !hasSword(Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD)) {
                removeItemFromInventory(Items.COBBLESTONE, 2);
                removeItemFromInventory(Items.STICK, 1);
                addItemToInventory(new ItemStack(Items.STONE_SWORD));
                broadcastGroupChat("Crafted stone sword at crafting table!");
            }
        } else if ((planks >= 3 || cobblestone >= 2) && craftingTables > 0) {
            // We have materials for tools AND a crafting table in inventory, but it's not placed
            if (tickCount % 100 == 0) { // Only every 5 seconds
                broadcastGroupChat("Need to place crafting table to craft tools!");
            }
        }
    }

    /**
     * Equip the best tool in the main hand for rendering and use
     */
    private void equipBestTool() {
        Inventory inv = getInventory();
        ItemStack bestTool = ItemStack.EMPTY;
        int bestSlot = -1;

        // Priority: Pickaxe > Axe > Sword > Other
        // Find best pickaxe
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            Item item = stack.getItem();

            // Check for pickaxes (highest priority)
            if (item == Items.NETHERITE_PICKAXE || item == Items.DIAMOND_PICKAXE ||
                item == Items.IRON_PICKAXE || item == Items.STONE_PICKAXE ||
                item == Items.WOODEN_PICKAXE || item == Items.GOLDEN_PICKAXE) {
                if (bestTool.isEmpty() || isToolBetter(stack, bestTool)) {
                    bestTool = stack;
                    bestSlot = i;
                }
            }
        }

        // If no pickaxe, find best axe
        if (bestTool.isEmpty()) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                Item item = stack.getItem();

                if (item == Items.NETHERITE_AXE || item == Items.DIAMOND_AXE ||
                    item == Items.IRON_AXE || item == Items.STONE_AXE ||
                    item == Items.WOODEN_AXE || item == Items.GOLDEN_AXE) {
                    if (bestTool.isEmpty() || isToolBetter(stack, bestTool)) {
                        bestTool = stack;
                        bestSlot = i;
                    }
                }
            }
        }

        // If no axe, find best sword
        if (bestTool.isEmpty()) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                Item item = stack.getItem();

                if (item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD ||
                    item == Items.IRON_SWORD || item == Items.STONE_SWORD ||
                    item == Items.WOODEN_SWORD || item == Items.GOLDEN_SWORD) {
                    if (bestTool.isEmpty() || isToolBetter(stack, bestTool)) {
                        bestTool = stack;
                        bestSlot = i;
                    }
                }
            }
        }

        // Equip the best tool in main hand
        if (!bestTool.isEmpty() && bestSlot >= 0) {
            setItemInHand(InteractionHand.MAIN_HAND, bestTool);
        }
    }

    /**
     * Check if tool1 is better than tool2 (higher tier)
     */
    private boolean isToolBetter(ItemStack tool1, ItemStack tool2) {
        Item item1 = tool1.getItem();
        Item item2 = tool2.getItem();

        // Tool tier ranking: Netherite > Diamond > Iron > Stone > Wood > Gold
        int tier1 = getToolTier(item1);
        int tier2 = getToolTier(item2);

        return tier1 > tier2;
    }

    /**
     * Get tool tier for comparison
     */
    private int getToolTier(Item item) {
        if (item.toString().contains("netherite")) return 6;
        if (item.toString().contains("diamond")) return 5;
        if (item.toString().contains("iron")) return 4;
        if (item.toString().contains("stone")) return 3;
        if (item.toString().contains("wooden")) return 2;
        if (item.toString().contains("golden")) return 1;
        return 0;
    }

    /**
     * Check if there's a crafting table within 3x3x3 area
     */
    private boolean isNearCraftingTable() {
        BlockPos center = blockPosition();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos checkPos = center.offset(x, y, z);
                    if (level().getBlockState(checkPos).is(Blocks.CRAFTING_TABLE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Place a crafting table nearby (manual action - bot must have one in inventory)
     */
    private void placeCraftingTable() {
        // Check if we have a crafting table in inventory
        if (countItemInInventory(Items.CRAFTING_TABLE) == 0) {
            broadcastGroupChat("No crafting table in inventory to place!");
            currentTask = "idle";
            return;
        }

        BlockPos center = blockPosition();

        // Try to find a suitable spot nearby (ground level, air above)
        // Start with closest positions first
        for (int radius = 1; radius <= 3; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    // Only check perimeter of current radius
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;

                    BlockPos placePos = center.offset(x, 0, z);
                    BlockPos groundPos = placePos.below();
                    BlockPos abovePos = placePos.above();

                    // Check if there's solid ground, air at place position, and air above
                    if (!level().getBlockState(groundPos).isAir() &&
                        level().getBlockState(placePos).isAir() &&
                        level().getBlockState(abovePos).isAir()) {

                        // Remove crafting table from inventory
                        removeItemFromInventory(Items.CRAFTING_TABLE, 1);

                        // Place the crafting table
                        level().setBlock(placePos, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                        broadcastGroupChat("Placed crafting table!");
                        currentTask = "idle";
                        return;
                    }
                }
            }
        }

        broadcastGroupChat("No suitable spot to place crafting table!");
        currentTask = "idle";
    }

    private boolean hasPickaxe(Item... betterPickaxes) {
        // Check if we have any pickaxe by checking for specific pickaxe items
        for (int i = 0; i < getInventory().getContainerSize(); i++) {
            ItemStack stack = getInventory().getItem(i);
            Item item = stack.getItem();

            // Check if it's any type of pickaxe
            if (item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE ||
                item == Items.IRON_PICKAXE || item == Items.GOLDEN_PICKAXE ||
                item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE) {

                // If we're checking for better pickaxes, make sure we don't have them
                if (betterPickaxes.length > 0) {
                    for (Item better : betterPickaxes) {
                        if (stack.is(better)) {
                            return true; // We have a better pickaxe
                        }
                    }
                    return false; // We have a pickaxe but not a better one
                }
                return true; // We have any pickaxe
            }
        }
        return false;
    }

    private boolean hasAxe(Item... betterAxes) {
        for (int i = 0; i < getInventory().getContainerSize(); i++) {
            ItemStack stack = getInventory().getItem(i);
            Item item = stack.getItem();

            // Check if it's any type of axe
            if (item == Items.WOODEN_AXE || item == Items.STONE_AXE ||
                item == Items.IRON_AXE || item == Items.GOLDEN_AXE ||
                item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE) {

                if (betterAxes.length > 0) {
                    for (Item better : betterAxes) {
                        if (stack.is(better)) {
                            return true;
                        }
                    }
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private boolean hasSword(Item... betterSwords) {
        for (int i = 0; i < getInventory().getContainerSize(); i++) {
            ItemStack stack = getInventory().getItem(i);
            Item item = stack.getItem();

            // Check if it's any type of sword
            if (item == Items.WOODEN_SWORD || item == Items.STONE_SWORD ||
                item == Items.IRON_SWORD || item == Items.GOLDEN_SWORD ||
                item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD) {

                if (betterSwords.length > 0) {
                    for (Item better : betterSwords) {
                        if (stack.is(better)) {
                            return true;
                        }
                    }
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Open GUI for a player to view bot's inventory
     */
    public void openGui(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, getBotInventory(), 4),
            getName()
        ));
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Find nearest block of a type
     */
    private BlockPos findNearestBlock(net.minecraft.world.level.block.Block block, int radius) {
        BlockPos botPos = blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        // Search in a more reasonable vertical range (not full radius)
        int verticalRange = Math.min(10, radius);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = botPos.offset(x, y, z);

                    // Skip if too far (spherical check)
                    double distSq = botPos.distSqr(checkPos);
                    if (distSq > radius * radius) continue;

                    if (level().getBlockState(checkPos).is(block)) {
                        if (distSq < nearestDist) {
                            nearest = checkPos;
                            nearestDist = distSq;
                        }
                    }
                }
            }
        }

        return nearest;
    }

    /**
     * Broadcast chat message to all players
     */
    public void broadcastGroupChat(String msg) {
        if (!level().isClientSide() && level().getServer() != null) {
            Component chatMessage = Component.literal("<" + getName().getString() + "> " + msg);
            level().getServer().getPlayerList().broadcastSystemMessage(chatMessage, false);
        }
    }

    // ==================== REAL PLAYER RULES ENFORCEMENT ====================

    /**
     * RULE 1: Enforce player crafting rules - LLM can only craft at crafting table
     * Returns true if crafting is allowed, false otherwise
     */
    private boolean enforcePlayerCraftingRules() {
        // Check if standing at/near a crafting table
        BlockPos pos = blockPosition();

        // Check block below (standing on)
        if (level().getBlockState(pos.below()).is(Blocks.CRAFTING_TABLE)) {
            return true;
        }

        // Check surrounding blocks (within reach)
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = pos.offset(x, y, z);
                    if (level().getBlockState(checkPos).is(Blocks.CRAFTING_TABLE)) {
                        return true;
                    }
                }
            }
        }

        // No crafting table nearby - block hand-crafting
        return false;
    }

    /**
     * RULE 2: Check tool requirements - enforce real player mining rules
     * Blocks that need specific tools won't drop items without correct tool
     */
    private void checkToolRequirements(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        ItemStack heldItem = getMainHandItem();

        // Check if block requires a tool and if we have the correct tool
        if (!heldItem.isCorrectToolForDrops(state)) {
            // Check specific tool requirements
            if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
                broadcastGroupChat("Need diamond tool to mine " + state.getBlock().getName().getString());
                level().destroyBlock(pos, false); // No drops
                return;
            } else if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
                broadcastGroupChat("Need iron tool to mine " + state.getBlock().getName().getString());
                level().destroyBlock(pos, false); // No drops
                return;
            } else if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
                broadcastGroupChat("Need stone tool to mine " + state.getBlock().getName().getString());
                level().destroyBlock(pos, false); // No drops
                return;
            }
        }

        // Tool is correct or block doesn't require specific tool - normal drops will occur
    }

    /**
     * RULE 3: Update surroundings for LLM awareness
     * Provides line-of-sight, render distance, and threat information
     */
    private void updateSurroundingsForLLM() {
        StringBuilder info = new StringBuilder();

        // Line of sight - what can the bot see?
        Vec3 eyePos = getEyePosition();
        Vec3 lookVec = getLookAngle();
        Vec3 targetVec = eyePos.add(lookVec.scale(16)); // 16 block view distance

        ClipContext context = new ClipContext(
            eyePos,
            targetVec,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            this
        );

        BlockHitResult result = level().clip(context);
        if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = result.getBlockPos();
            BlockState hitState = level().getBlockState(hitPos);
            info.append("Looking at: ").append(hitState.getBlock().getName().getString())
                .append(" at ").append(hitPos.toShortString()).append("; ");
        }

        // Nearby threats (hostile mobs within 16 blocks)
        List<Entity> nearbyEntities = level().getEntities(this, getBoundingBox().inflate(16.0));
        int threatCount = 0;
        for (Entity entity : nearbyEntities) {
            if (entity instanceof net.minecraft.world.entity.monster.Monster) {
                threatCount++;
            }
        }
        if (threatCount > 0) {
            info.append("THREATS: ").append(threatCount).append(" hostile mobs nearby; ");
        }

        // Current position and health
        info.append("Pos: ").append(blockPosition().toShortString())
            .append(", Health: ").append(String.format("%.1f", getHealth()))
            .append("/20.0");

        surroundingsInfo = info.toString();
    }

    /**
     * Get surroundings info for LLM decision making
     */
    public String getSurroundingsInfo() {
        return surroundingsInfo;
    }

    /**
     * RULE 4: Death & Respawn (real player style)
     * Combat/fall damage = respawn at bed with inventory
     * Other causes = permanent death (as designed)
     */
    @Override
    public void die(DamageSource source) {
        // Check if this is a "respawnable" death (combat, fall, etc.)
        if (source.getEntity() instanceof Player ||
            source.is(DamageTypes.FALL) ||
            source.is(DamageTypes.MOB_ATTACK) ||
            source.is(DamageTypes.PLAYER_ATTACK)) {

            broadcastGroupChat("Oof, that hurt! Respawning...");

            // Respawn at bed if set, otherwise near a player
            if (bedPos != null && level().getBlockState(bedPos).is(net.minecraft.world.level.block.Blocks.RED_BED)) {
                teleportTo(bedPos.getX() + 0.5, bedPos.getY() + 1, bedPos.getZ() + 0.5);
                setHealth(20.0f);
                getFoodData().setFoodLevel(20);
                broadcastGroupChat("Respawned at bed!");
                return;
            } else {
                // No bed - respawn near nearest player
                ServerPlayer nearestPlayer = (ServerPlayer) level().getNearestPlayer(this, 128);
                if (nearestPlayer != null) {
                    teleportTo(nearestPlayer.getX(), nearestPlayer.getY(), nearestPlayer.getZ());
                    setHealth(20.0f);
                    getFoodData().setFoodLevel(20);
                    broadcastGroupChat("Respawned near " + nearestPlayer.getName().getString());
                    return;
                }
            }
        }

        // Permanent death for other causes (old age, void, etc.)
        broadcastGroupChat("I have died permanently...");
        super.die(source);
    }

    /**
     * Set bed spawn point
     */
    public void setBedPos(BlockPos pos) {
        this.bedPos = pos;
        broadcastGroupChat("Bed spawn point set!");
    }

    /**
     * Get bed spawn point
     */
    public BlockPos getBedPos() {
        return bedPos;
    }

    // ==================== GETTERS/SETTERS ====================

    public void setBrainEnabled(boolean enabled) {
        this.brainEnabled = enabled;
        System.out.println("[AMB] " + getName().getString() + " brain set to " + (enabled ? "ON" : "OFF"));
    }

    public boolean isBrainEnabled() {
        return brainEnabled;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getGroup() {
        return group;
    }

    // ==================== STATIC SPAWN METHOD ====================

    /**
     * Spawn a bot at a player's location
     */
    public static AmbNpcEntity spawnAtPlayer(ServerPlayer player, String name, String group) {
        ServerLevel level = (ServerLevel) player.level();
        MinecraftServer server = level.getServer();
        if (server == null) {
            System.out.println("[AMB] ERROR: Cannot spawn bot - server is null");
            return null;
        }

        GameProfile profile = new GameProfile(UUID.randomUUID(), name);

        AmbNpcEntity bot = new AmbNpcEntity(level, profile);

        // Find ground level at player's X/Z position
        double spawnX = player.getX();
        double spawnZ = player.getZ();
        BlockPos playerPos = player.blockPosition();
        BlockPos groundPos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, playerPos);
        double spawnY = groundPos.getY();

        bot.setPos(spawnX, spawnY, spawnZ);
        bot.setCustomName(Component.literal(name));
        bot.setGroup(group);

        // Auto-activate brain when spawned
        bot.setBrainEnabled(true);

        // CRITICAL: Send player info packet BEFORE spawning entity
        // This tells clients about the player so they can render it
        ClientboundPlayerInfoUpdatePacket playerInfoPacket = new ClientboundPlayerInfoUpdatePacket(
            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
            bot
        );
        server.getPlayerList().broadcastAll(playerInfoPacket);

        // Add bot to the world as an entity
        level.addFreshEntity(bot);

        // CRITICAL FIX: FakePlayer entities need to override tick() to have physics
        // The default FakePlayer.tick() is empty, so we need to call super.tick() ourselves
        // This is handled in our overridden tick() method above

        System.out.println("[AMB] Spawned FakePlayer bot: " + name + " in group: " + group + " (brain: ON)");
        return bot;
    }
}
