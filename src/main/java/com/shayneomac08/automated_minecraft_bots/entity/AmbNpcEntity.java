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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
            default -> {
                // Unknown task, go idle
                currentTask = "idle";
            }
        }
    }

    // ==================== TASK IMPLEMENTATIONS ====================

    private void gatherWood() {
        // Find nearest tree (oak log)
        BlockPos nearestLog = findNearestBlock(Blocks.OAK_LOG, 32);

        if (nearestLog != null) {
            // Move to tree
            if (position().distanceTo(Vec3.atCenterOf(nearestLog)) > 3.0) {
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
        // Find nearest stone/cobblestone
        BlockPos nearestStone = findNearestBlock(Blocks.STONE, 32);
        if (nearestStone == null) {
            nearestStone = findNearestBlock(Blocks.COBBLESTONE, 32);
        }

        if (nearestStone != null) {
            // Move to stone
            if (position().distanceTo(Vec3.atCenterOf(nearestStone)) > 3.0) {
                setMoveTarget(nearestStone, 0.2f);
            } else {
                // Stop moving and mine the stone
                stopMovement();
                mineBlockLikePlayer(nearestStone);
            }
        } else {
            broadcastGroupChat("No stone nearby!");
            currentTask = "idle";
        }
    }

    /**
     * Mine a block like a player would - with proper timing and item drops
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

        // Show mining progress (crack animation would go here in full implementation)
        if (miningProgress % 10 == 0) {
            float progress = (float)miningProgress / miningTotalTime * 100;
            if (progress < 100) {
                // Could send block damage packet here for visual cracks
            }
        }

        // Check if mining is complete
        if (miningProgress >= miningTotalTime) {
            // Break the block using gameMode (this handles drops properly)
            boolean broken = gameMode.destroyBlock(pos);

            if (broken) {
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

    private void craft() {
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

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = botPos.offset(x, y, z);
                    if (level().getBlockState(checkPos).is(block)) {
                        double dist = botPos.distSqr(checkPos);
                        if (dist < nearestDist) {
                            nearest = checkPos;
                            nearestDist = dist;
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
