package com.shayneomac08.automated_minecraft_bots.entity;

import com.mojang.authlib.GameProfile;
import com.shayneomac08.automated_minecraft_bots.movement.RealisticActions;
import com.shayneomac08.automated_minecraft_bots.movement.RealisticMovement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.Random;
import java.util.UUID;

/**
 * NEW STABLE FAKEPLAYER AMBNPCENTITY
 * Clean FakePlayer-based bot implementation (FakePlayer handles connection automatically)
 */
public class AmbNpcEntity extends FakePlayer {

    // Bot roles
    public enum BotRole { LEADER, BUILDER, MINER, GATHERER, EXPLORER }

    // Core state
    public BotRole currentRole = BotRole.GATHERER;
    public int hunger = 20;
    public BlockPos baseLocation = BlockPos.ZERO;
    public BlockPos knownCraftingTable = BlockPos.ZERO;

    // Legacy compatibility fields
    private boolean brainEnabled = true;
    private String currentTask = "explore";
    private net.minecraft.world.phys.Vec3 moveTarget = null;

    // Movement and action state
    private BlockPos currentGoal = BlockPos.ZERO;
    private int goalLockTimer = 0;
    private BlockPos currentBreakingBlock = BlockPos.ZERO;
    private int messageCooldown = 0;
    private int spawnIdleTimer = 100;
    private int toolEquipTimer = 0;
    private int yawUpdateTimer = 0;
    private boolean roleAnnouncementDone = false;

    private final Random random = new Random();

    // Realistic movement and action systems
    private final RealisticActions.MiningState miningState = new RealisticActions.MiningState();
    private boolean isMovingToGoal = false;
    private int stuckTimer = 0;
    private BlockPos lastPosition = BlockPos.ZERO;
    private float desiredSpeed = 0.13f; // default sprint-like speed
    private Vec3 lastExactPos = Vec3.ZERO;
    // Door navigation
    private BlockPos doorPos = BlockPos.ZERO;
    private int doorPhase = 0; // 0=none,1=approach,2=open,3=pass
    private int doorTimer = 0;
    // Local avoidance (simple wall-following)
    private int avoidTicks = 0;
    private int avoidDir = 1; // +1=right, -1=left

    // Constructor for programmatic spawning
    public AmbNpcEntity(ServerLevel level, String name) {
        super(level, new GameProfile(UUID.randomUUID(), name));
        this.setGameMode(GameType.SURVIVAL);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
        this.setHealth(20.0F);
        this.setInvisible(false);
        this.setInvulnerable(false);
    }

    // Visual entity that mirrors this FakePlayer
    private AmbNpcVisualEntity visualEntity = null;

    // ==================== PERMANENT NO DIRT KICKING ====================

    @Override
    public boolean isSilent() {
        return true;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // No step sounds
    }

    @Override
    public void spawnSprintParticle() {
        // No sprint particles
    }

    @Override
    public float getSoundVolume() {
        return 0.0F;
    }

    // ==================== VISIBLE HANDS + REAL MINING ====================

    public void equipToolInHand(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        this.setItemInHand(InteractionHand.MAIN_HAND, stack);
        this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
    }

    public void removeItemFromInventory(net.minecraft.world.item.Item item, int count) {
        for (int i = 0; i < getInventory().getContainerSize(); i++) {
            ItemStack stack = getInventory().getItem(i);
            if (stack.is(item)) {
                int toRemove = Math.min(count, stack.getCount());
                stack.shrink(toRemove);
                count -= toRemove;
                if (count <= 0) break;
            }
        }
    }

    public void broadcastGroupChat(String message) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal("[" + getName().getString() + "] " + message),
                false
            );
        }
    }

    // ==================== LEGACY COMPATIBILITY METHODS ====================

    public void setBrainEnabled(boolean enabled) {
        this.brainEnabled = enabled;
    }

    public boolean isBrainEnabled() {
        return this.brainEnabled;
    }

    public void setTask(String task) {
        this.currentTask = task;
    }

    public String getCurrentTask() {
        return this.currentTask;
    }

    public void setMoveTarget(net.minecraft.world.phys.Vec3 target, float speed) {
        this.moveTarget = target;
        if (target != null) {
            this.currentGoal = new BlockPos((int)target.x, (int)target.y, (int)target.z);
            this.isMovingToGoal = true;
            this.desiredSpeed = speed;
            this.setSprinting(speed >= 0.13f);
        }
    }

    public void stopMovement() {
        this.moveTarget = null;
        this.currentGoal = BlockPos.ZERO;
        this.isMovingToGoal = false;
        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
        this.setSprinting(false);
    }

    public void openGui(net.minecraft.server.level.ServerPlayer player) {
        // Open the bot's inventory as a chest GUI
        player.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.literal(getName().getString() + "'s Inventory");
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId,
                    net.minecraft.world.entity.player.Inventory playerInventory,
                    net.minecraft.world.entity.player.Player player) {
                // Create a chest menu that shows the bot's inventory
                return net.minecraft.world.inventory.ChestMenu.threeRows(containerId, playerInventory, getInventory());
            }
        });
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Bot GUI for " + getName().getString()));
    }

    public static AmbNpcEntity spawnAtPlayer(net.minecraft.server.level.ServerPlayer player, String name, String llmType) {
        ServerLevel level = (ServerLevel) player.level();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        // Create the FakePlayer bot (server-side logic)
        AmbNpcEntity bot = new AmbNpcEntity(level, name);
        bot.setPos(x, y, z);
        bot.setCustomName(net.minecraft.network.chat.Component.literal(name));
        bot.setCustomNameVisible(true);
        bot.setYRot(player.getYRot());
        bot.setYHeadRot(player.getYRot());

        // IMPORTANT: Add the FakePlayer entity to the world so its tick() runs
        // Without this, the bot will never tick and thus never move
        level.addFreshEntity(bot);

        // Create the visual entity (client-side rendering)
        AmbNpcVisualEntity visual = new AmbNpcVisualEntity(
            com.shayneomac08.automated_minecraft_bots.registry.ModEntities.AMB_NPC_VISUAL.get(),
            level,
            bot
        );
        visual.setPos(x, y, z);
        level.addFreshEntity(visual);
        bot.visualEntity = visual;

        return bot;
    }

    // ==================== FULL PERSISTENCE ====================

    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", currentRole.name());
        tag.putInt("Hunger", hunger);
        if (!baseLocation.equals(BlockPos.ZERO)) tag.putLong("BasePos", baseLocation.asLong());
        if (!knownCraftingTable.equals(BlockPos.ZERO)) tag.putLong("TablePos", knownCraftingTable.asLong());
    }

    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        tag.getString("Role").ifPresent(role -> currentRole = BotRole.valueOf(role));
        tag.getInt("Hunger").ifPresent(h -> hunger = h);
        tag.getLong("BasePos").ifPresent(pos -> baseLocation = BlockPos.of(pos));
        tag.getLong("TablePos").ifPresent(pos -> knownCraftingTable = BlockPos.of(pos));
    }

    // ==================== ROLE ASSIGNMENT ====================

    private void assignInitialRole() {
        currentRole = BotRole.values()[random.nextInt(BotRole.values().length)];
        broadcastGroupChat("I am now the " + currentRole + " of the tribe. Let's begin our journey.");
    }

    // ==================== MASTER ACTION RUNNER ====================

    private void runAllPlayerActions() {
        // One-time role announcement on first tick
        if (spawnIdleTimer > 0 && spawnIdleTimer == 100 && !roleAnnouncementDone) {
            assignInitialRole();
            roleAnnouncementDone = true;
        }

        if (spawnIdleTimer > 0) {
            spawnIdleTimer--;
            // On last idle tick, execute task to get initial goal
            if (spawnIdleTimer == 0) {
                executeCurrentTask();
            }
            return; // stand still for 5 seconds to get bearings
        }

        // CRITICAL SURVIVAL - Eat if hungry
        if (RealisticActions.shouldEat(this)) {
            RealisticActions.eatFood(this);
        }

        // CRITICAL SURVIVAL - Flee if critically low health
        if (RealisticActions.isCriticalHealth(this)) {
            stopMovement();
            currentGoal = BlockPos.ZERO;
            return; // Don't do anything else, just recover
        }

        // REALISTIC MOVEMENT SYSTEM
        if (!currentGoal.equals(BlockPos.ZERO)) {
            // Use realistic movement to navigate to goal
            float speed = (this.desiredSpeed > 0f) ? this.desiredSpeed : RealisticMovement.calculateSpeed(this, true);
            boolean stillMoving;

            // If in door handling, bias toward door first
            if (doorPhase > 0 && !doorPos.equals(BlockPos.ZERO)) {
                stillMoving = RealisticMovement.moveTowards(this, doorPos, speed);
                handleDoorPlan();
            } else if (avoidTicks > 0 && moveTarget != null) {
                // Perform strafe to get around obstacle
                avoidTicks--;
                RealisticMovement.strafeAround(this, moveTarget, avoidDir, speed * 0.85f);
                stillMoving = true;
            } else {
                stillMoving = RealisticMovement.moveTowards(this, currentGoal, speed);
            }

            // Debug logging every 2 seconds
            if (tickCount % 40 == 0) {
                System.out.println("[AMB] " + getName().getString() + " moving to goal " + currentGoal +
                    " (current pos: " + blockPosition() + ", distance: " +
                    Math.sqrt(blockPosition().distSqr(currentGoal)) + ")");
            }

            // Check if stuck (not moving for 3 seconds) using precise position threshold
            double moved2 = this.position().distanceToSqr(lastExactPos);
            if (moved2 < 0.0025) { // ~0.05 blocks
                stuckTimer++;
                if (stuckTimer > 60) { // 3 seconds
                    // Try door rescue first
                    if (attemptDoorRescue()) {
                        System.out.println("[AMB] " + getName().getString() + " stuck; attempting door rescue toward " + doorPos);
                        stuckTimer = 0;
                    } else {
                        // Start local avoidance strafe
                        avoidDir = (random.nextBoolean() ? 1 : -1);
                        avoidTicks = 20;
                        System.out.println("[AMB] " + getName().getString() + " stuck; strafing " + (avoidDir>0?"right":"left"));
                        stuckTimer = 0;
                        // If still no progress after avoidance, pick a new goal
                        if (random.nextBoolean()) {
                            currentGoal = BlockPos.ZERO;
                            executeCurrentTask();
                        }
                    }
                }
            } else {
                stuckTimer = 0;
                lastPosition = blockPosition();
                lastExactPos = this.position();
            }

            // Reached goal
            if (!stillMoving) {
                // Check if we should mine the block at goal
                BlockState targetState = level().getBlockState(currentGoal);
                if (shouldMineBlock(targetState)) {
                    // Start mining
                    if (!miningState.isMining) {
                        RealisticActions.equipBestTool(this, targetState);
                        RealisticActions.startMining(this, currentGoal, miningState);
                    }
                } else {
                    // Goal reached, pick new one
                    currentGoal = BlockPos.ZERO;
                    executeCurrentTask();
                }
            }
        } else {
            // No goal - execute current task to find one
            if (tickCount % 100 == 0) {
                executeCurrentTask();
            }
        }

        // REALISTIC MINING - Continue mining if in progress
        if (miningState.isMining) {
            boolean blockBroken = RealisticActions.continueMining(this, miningState);
            if (blockBroken) {
                // Block broken - find next goal
                currentGoal = BlockPos.ZERO;
                executeCurrentTask();
            }
        }

        // REALISTIC TOOL SWITCHING - Equip appropriate tool for current task
        toolEquipTimer++;
        if (toolEquipTimer > 40) { // Every 2 seconds
            equipAppropriateToolForTask();
            toolEquipTimer = 0;
        }

        // NATURAL MESSAGES
        if (tickCount % 600 == 0 && messageCooldown == 0) {
            if (getFoodData().getFoodLevel() < 8) {
                broadcastGroupChat("My stomach is growling... need to find food soon.");
            }
            messageCooldown = 300;
        }
        if (messageCooldown > 0) messageCooldown--;
    }

    /**
     * Check if a block should be mined based on current task
     */
    private boolean shouldMineBlock(BlockState state) {
        if (state.isAir()) return false;

        switch (currentTask) {
            case "gather_wood":
                return state.is(BlockTags.LOGS);
            case "mine_stone":
                return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) ||
                       state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE);
            case "mine_ore":
                return state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE) ||
                       state.is(Blocks.COPPER_ORE) || state.is(Blocks.GOLD_ORE) ||
                       state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.EMERALD_ORE);
            default:
                return false;
        }
    }

    /**
     * Equip the appropriate tool for the current task
     */
    private void equipAppropriateToolForTask() {
        switch (currentTask) {
            case "gather_wood":
                // Equip best axe
                if (getInventory().countItem(Items.DIAMOND_AXE) > 0) {
                    equipToolInHand(Items.DIAMOND_AXE);
                } else if (getInventory().countItem(Items.IRON_AXE) > 0) {
                    equipToolInHand(Items.IRON_AXE);
                } else if (getInventory().countItem(Items.STONE_AXE) > 0) {
                    equipToolInHand(Items.STONE_AXE);
                } else if (getInventory().countItem(Items.WOODEN_AXE) > 0) {
                    equipToolInHand(Items.WOODEN_AXE);
                } else if (getInventory().countItem(Items.WOODEN_PICKAXE) > 0) {
                    // fallback to any tool for break attempts in door navigation
                    equipToolInHand(Items.WOODEN_PICKAXE);
                }
                break;
            case "mine_stone":
            case "mine_ore":
                // Equip best pickaxe
                if (getInventory().countItem(Items.DIAMOND_PICKAXE) > 0) {
                    equipToolInHand(Items.DIAMOND_PICKAXE);
                } else if (getInventory().countItem(Items.IRON_PICKAXE) > 0) {
                    equipToolInHand(Items.IRON_PICKAXE);
                } else if (getInventory().countItem(Items.STONE_PICKAXE) > 0) {
                    equipToolInHand(Items.STONE_PICKAXE);
                } else if (getInventory().countItem(Items.WOODEN_PICKAXE) > 0) {
                    equipToolInHand(Items.WOODEN_PICKAXE);
                }
                break;
            case "hunt_animals":
                // Equip best sword
                if (getInventory().countItem(Items.DIAMOND_SWORD) > 0) {
                    equipToolInHand(Items.DIAMOND_SWORD);
                } else if (getInventory().countItem(Items.IRON_SWORD) > 0) {
                    equipToolInHand(Items.IRON_SWORD);
                } else if (getInventory().countItem(Items.STONE_SWORD) > 0) {
                    equipToolInHand(Items.STONE_SWORD);
                } else if (getInventory().countItem(Items.WOODEN_SWORD) > 0) {
                    equipToolInHand(Items.WOODEN_SWORD);
                }
                break;
        }
    }

    // ============ Door handling and local avoidance ============
    private boolean attemptDoorRescue() {
        // scan for nearest wooden door within 6 blocks
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos p = blockPosition().offset(dx, dy, dz);
                    BlockState st = level().getBlockState(p);
                    if (st.getBlock() instanceof DoorBlock) {
                        double d2 = p.distSqr(blockPosition());
                        if (d2 < bestD2) { bestD2 = d2; best = p; }
                    }
                }
            }
        }
        if (best != null) {
            doorPos = best;
            doorPhase = 1;
            doorTimer = 60; // 3 seconds plan
            return true;
        }
        return false;
    }

    private void handleDoorPlan() {
        if (doorPhase <= 0) return;
        if (doorTimer-- <= 0) { doorPhase = 0; doorPos = BlockPos.ZERO; return; }

        BlockState st = level().getBlockState(doorPos);
        if (!(st.getBlock() instanceof DoorBlock door)) { doorPhase = 0; doorPos = BlockPos.ZERO; return; }

        // If close to the door, try toggling it open
        if (this.blockPosition().closerThan(doorPos, 2.2)) {
            Boolean open = st.getOptionalValue(BlockStateProperties.OPEN).orElse(false);
            boolean targetOpen = true;
            if (!open) {
                // Right click to open (simulate use)
                RealisticActions.interactWithBlock(this, doorPos);
            }
            // After opening attempt, aim to pass through to the other side
            Direction facing = st.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
            BlockPos beyond = doorPos.relative(facing);
            this.currentGoal = RealisticMovement.findNearestWalkable((ServerLevel) level(), beyond, blockPosition());
            doorPhase = 3; // pass through
        } else {
            // keep approaching door
            // slight offset to center of doorway
            this.currentGoal = doorPos;
        }
    }

    // ==================== TASK EXECUTION ====================

    private void executeCurrentTask() {
        if (currentTask == null || currentTask.isEmpty()) {
            // No task - just wander
            currentGoal = blockPosition().offset(
                random.nextInt(20) - 10,
                0,
                random.nextInt(20) - 10
            );
            goalLockTimer = 200;
            System.out.println("[AMB] " + getName().getString() + " no task, wandering to " + currentGoal);
            return;
        }

        System.out.println("[AMB] " + getName().getString() + " executing task: " + currentTask);

        switch (currentTask) {
            case "gather_wood" -> {
                // Find nearest tree (oak logs)
                BlockPos tree = findNearestBlock(BlockTags.LOGS, 32);
                if (tree != null) {
                    // Move to a walkable spot near the tree, not to the log's top block
                    BlockPos approach = RealisticMovement.findNearestWalkable((ServerLevel) level(), tree, blockPosition());
                    currentGoal = approach;
                    // Clear any door plan when pursuing resource
                    doorPhase = 0; doorPos = BlockPos.ZERO; doorTimer = 0; avoidTicks = 0;
                    goalLockTimer = 400;
                    System.out.println("[AMB] " + getName().getString() + " found tree at " + tree + ", moving to it");
                } else {
                    // No tree found - explore
                    currentGoal = blockPosition().offset(
                        random.nextInt(40) - 20,
                        0,
                        random.nextInt(40) - 20
                    );
                    goalLockTimer = 300;
                    System.out.println("[AMB] " + getName().getString() + " no tree found, exploring to " + currentGoal);
                }
            }
            case "mine_stone" -> {
                // Find nearest stone
                BlockPos stone = findNearestBlock(Blocks.STONE, 32);
                if (stone != null) {
                    BlockPos approach = RealisticMovement.findNearestWalkable((ServerLevel) level(), stone, blockPosition());
                    currentGoal = approach;
                    doorPhase = 0; doorPos = BlockPos.ZERO; doorTimer = 0; avoidTicks = 0;
                    goalLockTimer = 400;
                    System.out.println("[AMB] " + getName().getString() + " found stone at " + stone + ", moving to it");
                } else {
                    // No stone found - explore
                    currentGoal = blockPosition().offset(
                        random.nextInt(40) - 20,
                        0,
                        random.nextInt(40) - 20
                    );
                    goalLockTimer = 300;
                    System.out.println("[AMB] " + getName().getString() + " no stone found, exploring to " + currentGoal);
                }
            }
            case "explore" -> {
                // Random exploration
                currentGoal = blockPosition().offset(
                    random.nextInt(60) - 30,
                    0,
                    random.nextInt(60) - 30
                );
                goalLockTimer = 400;
                System.out.println("[AMB] " + getName().getString() + " exploring to " + currentGoal);
            }
            case "idle" -> {
                // Do nothing
                currentGoal = BlockPos.ZERO;
                goalLockTimer = 100;
                System.out.println("[AMB] " + getName().getString() + " idling");
            }
            default -> {
                // Unknown task - wander
                currentGoal = blockPosition().offset(
                    random.nextInt(20) - 10,
                    0,
                    random.nextInt(20) - 10
                );
                goalLockTimer = 200;
                System.out.println("[AMB] " + getName().getString() + " unknown task '" + currentTask + "', wandering to " + currentGoal);
            }
        }
    }

    private BlockPos findNearestBlock(net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag, int radius) {
        BlockPos myPos = blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = myPos.offset(x, y, z);
                    if (level().getBlockState(check).is(tag)) {
                        double dist = myPos.distSqr(check);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = check;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private BlockPos findNearestBlock(net.minecraft.world.level.block.Block block, int radius) {
        BlockPos myPos = blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = myPos.offset(x, y, z);
                    if (level().getBlockState(check).is(block)) {
                        double dist = myPos.distSqr(check);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = check;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    // ==================== TICK - MAIN CONTROL LOOP ====================

    /**
     * Explicit bot tick for when FakePlayer isn't in the normal player tick loop.
     * This is invoked from the global server tick handler.
     */
    public void tickBot() {
        runAllPlayerActions();
    }

    @Override
    public void tick() {
        super.tick();
        runAllPlayerActions();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        // Remove the visual entity when the FakePlayer is removed
        if (visualEntity != null && !visualEntity.isRemoved()) {
            visualEntity.discard();
        }
    }
}
