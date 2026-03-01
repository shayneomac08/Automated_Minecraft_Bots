package com.shayneomac08.automated_minecraft_bots.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
    private BotVisualEntity visualEntity = null;

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

            // Actually move towards the target - FakePlayers need manual position updates
            double dx = target.x - getX();
            double dz = target.z - getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance > 0.5) {
                // Normalize and apply speed
                double moveX = (dx / distance) * speed;
                double moveZ = (dz / distance) * speed;

                // Update position directly
                this.setPos(this.getX() + moveX, this.getY(), this.getZ() + moveZ);

                // Update rotation to face movement direction
                float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                this.setYRot(yaw);
                this.setYHeadRot(yaw);
                this.setSprinting(speed > 0.11f);
            }
        }
    }

    public void stopMovement() {
        this.moveTarget = null;
        this.currentGoal = BlockPos.ZERO;
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

        // Create the visual entity (client-side rendering)
        BotVisualEntity visual = new BotVisualEntity(
            com.shayneomac08.automated_minecraft_bots.registry.ModEntities.BOT_VISUAL.get(),
            level
        );
        visual.setPos(x, y, z);
        visual.setLinkedBot(bot);
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

        if (goalLockTimer > 0) goalLockTimer--;
        else if (!currentGoal.equals(BlockPos.ZERO)) goalLockTimer = 400; // 20-second calm commitment

        // SMOOTH HUMAN MOVEMENT — yaw only every 15 ticks with tiny natural sway
        yawUpdateTimer++;
        if (yawUpdateTimer > 15 && !currentGoal.equals(BlockPos.ZERO)) {
            double dx = currentGoal.getX() - getX();
            double dz = currentGoal.getZ() - getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance > 1.0) {
                float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
                this.setYRot(yaw + (random.nextFloat() * 3 - 1.5f)); // very subtle human sway
                this.setYHeadRot(this.getYRot());

                // Actually move towards the goal - FakePlayers need manual position updates
                double speed = 0.15;
                double moveX = (dx / distance) * speed;
                double moveZ = (dz / distance) * speed;

                // Move the FakePlayer directly
                this.setPos(this.getX() + moveX, this.getY(), this.getZ() + moveZ);
                this.setSprinting(true);
            } else {
                // Reached goal, pick a new one
                currentGoal = blockPosition().offset(
                    random.nextInt(20) - 10,
                    0,
                    random.nextInt(20) - 10
                );
                goalLockTimer = 200;
            }
            yawUpdateTimer = 0;
        } else if (currentGoal.equals(BlockPos.ZERO)) {
            // Execute current task to find a goal
            if (tickCount % 100 == 0) {
                executeCurrentTask();
            }
        }

        // ALWAYS VISIBLE HANDS
        toolEquipTimer++;
        if (toolEquipTimer > 20) {
            if (getInventory().countItem(Items.WOODEN_AXE) > 0 && !getMainHandItem().is(Items.WOODEN_AXE)) {
                equipToolInHand(Items.WOODEN_AXE);
            } else if (getInventory().countItem(Items.WOODEN_PICKAXE) > 0 && !getMainHandItem().is(Items.WOODEN_PICKAXE)) {
                equipToolInHand(Items.WOODEN_PICKAXE);
            } else if (getInventory().countItem(Items.WOODEN_SWORD) > 0 && !getMainHandItem().is(Items.WOODEN_SWORD)) {
                equipToolInHand(Items.WOODEN_SWORD);
            }
            toolEquipTimer = 0;
        }

        // REAL MINING LOCK + CRACKING
        if (tickCount % 4 == 0) {
            BlockPos target = blockPosition().relative(getDirection());
            BlockState state = level().getBlockState(target);
            if ((state.is(BlockTags.LOGS) || state.is(Blocks.STONE) || state.is(Blocks.DIRT) ||
                 state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SAND) || state.is(Blocks.COAL_ORE) ||
                 state.is(Blocks.IRON_ORE) || state.is(Blocks.COPPER_ORE) || state.is(Blocks.REDSTONE_ORE) ||
                 state.is(Blocks.GOLD_ORE) || state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DIAMOND_ORE)) &&
                !currentBreakingBlock.equals(target)) {
                currentBreakingBlock = target;
                goalLockTimer = 80;
            }
            if (!currentBreakingBlock.equals(BlockPos.ZERO)) {
                gameMode.destroyBlock(currentBreakingBlock);
                if (level().getBlockState(currentBreakingBlock).isAir()) {
                    currentBreakingBlock = BlockPos.ZERO;
                    goalLockTimer = 0;
                }
            }
        }

        // REAL EATING
        if (hunger < 10 && getInventory().countItem(Items.BREAD) > 0) {
            removeItemFromInventory(Items.BREAD, 1);
            hunger = Math.min(20, hunger + 5);
            broadcastGroupChat("Eating some bread... feeling better already.");
        } else if (hunger < 10 && getInventory().countItem(Items.APPLE) > 0) {
            removeItemFromInventory(Items.APPLE, 1);
            hunger = Math.min(20, hunger + 4);
            broadcastGroupChat("Munching an apple... good stuff.");
        }

        // NATURAL MESSAGES
        if (tickCount % 600 == 0 && messageCooldown == 0) {
            if (hunger < 8) {
                broadcastGroupChat("My stomach is growling... the tribe needs food soon.");
            }
            messageCooldown = 300;
        }
        if (messageCooldown > 0) messageCooldown--;
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
            return;
        }

        switch (currentTask) {
            case "gather_wood" -> {
                // Find nearest tree (oak logs)
                BlockPos tree = findNearestBlock(BlockTags.LOGS, 32);
                if (tree != null) {
                    currentGoal = tree;
                    goalLockTimer = 400;
                } else {
                    // No tree found - explore
                    currentGoal = blockPosition().offset(
                        random.nextInt(40) - 20,
                        0,
                        random.nextInt(40) - 20
                    );
                    goalLockTimer = 300;
                }
            }
            case "mine_stone" -> {
                // Find nearest stone
                BlockPos stone = findNearestBlock(Blocks.STONE, 32);
                if (stone != null) {
                    currentGoal = stone;
                    goalLockTimer = 400;
                } else {
                    // No stone found - explore
                    currentGoal = blockPosition().offset(
                        random.nextInt(40) - 20,
                        0,
                        random.nextInt(40) - 20
                    );
                    goalLockTimer = 300;
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
            }
            case "idle" -> {
                // Do nothing
                currentGoal = BlockPos.ZERO;
                goalLockTimer = 100;
            }
            default -> {
                // Unknown task - wander
                currentGoal = blockPosition().offset(
                    random.nextInt(20) - 10,
                    0,
                    random.nextInt(20) - 10
                );
                goalLockTimer = 200;
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
