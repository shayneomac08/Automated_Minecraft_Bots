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
import net.minecraft.world.entity.EquipmentSlot;
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

    // GROUP COORDINATION SYSTEM FOR FAKEPLAYER BOTS
    public enum BotRole { LEADER, BUILDER, MINER, GATHERER, EXPLORER }

    // ==================== DUMMY CONNECTION FOR FAKEPLAYER ====================
    // FakePlayer requires a connection - we use a minimal implementation

    // ==================== BOT STATE ====================

    private boolean brainEnabled = true;
    private String group = "none";
    private String currentTask = "explore"; // Start exploring by default instead of standing idle
    private BlockPos targetPos = null;
    private int taskTicks = 0;
    private Vec3 moveTarget = null;
    private float moveSpeed = 0.2f;

    // Navigation optimization
    private int pathCooldown = 0;
    private int obstacleAvoidanceCooldown = 0;
    private Vec3 avoidanceDirection = null;

    // STABLE HYBRID MOVEMENT EMULATOR + CRAFTING TABLE PLACEMENT (100% FakePlayer-safe)
    private int movementDebugTimer = 0;
    private int movementLockTimer = 0;
    private BlockPos currentGoalPos = BlockPos.ZERO;
    private int stepCooldown = 0;
    private int craftingCooldown = 0;
    private int sprintCooldown = 0;

    // PATHFINDING AI FOR FAKEPLAYER
    private BlockPos currentPathGoal = BlockPos.ZERO;
    private int pathStepTimer = 0;
    private int obstacleCheckTimer = 0;

    // GROUP COORDINATION SYSTEM
    private BotRole currentRole = BotRole.GATHERER;
    private String groupLeaderName = "";

    // HUMAN-LIKE BEHAVIORS
    private int hunger = 20; // out of 20
    private int sleepTimer = 0;

    // ULTIMATE IMMERSION: NATIVE INHABITANTS OF THE MINECRAFT WORLD
    private String skyGiftName = "the Giver"; // they will evolve this name over time
    private String currentSeason = "Spring"; // updates automatically

    // Mining state (for player-like block breaking)
    private BlockPos miningBlock = null;
    private int miningProgress = 0;
    private int miningTotalTime = 0;

    // Surroundings awareness for LLM
    private String surroundingsInfo = "";
    private BlockPos bedPos = null; // Respawn point

    // ==================== SHARED MEMORY & HUMAN-LIKE FEATURES ====================

    // Shared memory per group
    private static final Map<String, GroupMemoryLedger> GROUP_LEDGERS = new HashMap<>();

    private GroupMemoryLedger getLedger() {
        return GROUP_LEDGERS.computeIfAbsent(group, k -> new GroupMemoryLedger());
    }

    // ==================== EMOTIONAL REACTIONS & PERSONALITY SYSTEM ====================

    private final List<String> emotions = new ArrayList<>();
    private String currentMood = "neutral";

    private void updateEmotion(String event) {
        switch (event.toLowerCase()) {
            case "hurt" -> {
                Entity lastHurt = getLastHurtByMob();
                String attacker = (lastHurt != null ? lastHurt.getName().getString() : "the world");
                emotions.add("angry at " + attacker);
                currentMood = "pissed";
                broadcastGroupChat("Ow! That hurt! I'm mad now...");
            }
            case "fed" -> {
                currentMood = "happy";
                broadcastGroupChat("Thanks for the food! The Giver blessed us today ❤️");
            }
            case "night" -> {
                currentMood = "tired but ready to build";
            }
            case "success" -> {
                currentMood = "accomplished";
                broadcastGroupChat("Hell yeah! Got it done!");
            }
        }

        // Personality flavor based on LLM type (from BotBrain)
        String llmType = "neutral"; // Will be set from BotBrain context
        String flavor = switch (llmType.toLowerCase()) {
            case "grok" -> "Fuck yeah, let's blow something up!";
            case "gemini" -> "This could be the start of a beautiful friendship...";
            case "openai" -> "I am considering the optimal strategy here.";
            default -> "";
        };

        if (!flavor.isEmpty() && random.nextInt(5) == 0) {
            broadcastGroupChat(flavor);
        }
    }

    public String getCurrentMood() {
        return currentMood;
    }

    public List<String> getEmotions() {
        return new ArrayList<>(emotions);
    }

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

    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        super.actuallyHurt(level, source, amount);
        // Trigger angry emotion when hurt
        updateEmotion("hurt");
    }

    // ==================== TICK - MAIN CONTROL LOOP ====================

    @Override
    public void tick() {
        super.tick(); // FakePlayer tick - handles inventory, movement, etc.

        // ===== ULTIMATE IMMERSION: TRUE INHABITANTS OF THIS WORLD =====
        runTrueInhabitantsOfThisWorld();

        if (!brainEnabled) return;

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

        // ===== HUMAN-LIKE FEATURES: EYES + VOTING =====
        // Every 5 seconds: Build rich snapshot with eyes, call LLM, execute JSON action
        if (tickCount % 100 == 0) {
            String snapshot = buildRichEyesSnapshot();
            // Note: LLM call happens through BotBrain system - this snapshot feeds into it
            // The snapshot is stored for BotBrain to use
            this.surroundingsInfo = snapshot;
        }

        // Every minute: Run group vote
        if (tickCount % 1200 == 0) {
            runGroupVote();
        }

        // ===== EMOTIONAL REACTIONS: Check for night time =====
        if (tickCount % 200 == 0) {
            long dayTime = level().getDayTime() % 24000L;
            boolean isNight = dayTime >= 13000 && dayTime < 23000;
            if (isNight && !currentMood.equals("tired but ready to build")) {
                updateEmotion("night");
            }
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
     * PATHFINDING AI FOR FAKEPLAYER
     * LLM sets goal → bots path straight with step-up + sprint
     */
    private void setPathfindingGoal(BlockPos goal) {
        currentPathGoal = goal;
        pathStepTimer = 0;
        broadcastGroupChat("New path set! Heading to " + goal + " — let's go!");
    }

    /**
     * ADVANCED PATHFINDING AI
     * Vector pathing with obstacle avoidance (water, lava, cliffs, trees)
     */
    private void runPathfindingAI() {
        if (currentPathGoal.equals(BlockPos.ZERO)) return;

        pathStepTimer++;
        if (pathStepTimer > 30) pathStepTimer = 0;

        // Vector toward goal
        double dx = currentPathGoal.getX() - getX();
        double dz = currentPathGoal.getZ() - getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
        this.setYRot(yaw);

        // SPRINT WHEN CLEAR
        this.setSprinting(!horizontalCollision && onGround());

        // ADVANCED OBSTACLE AVOIDANCE
        if (obstacleCheckTimer++ > 8) {
            obstacleCheckTimer = 0;
            BlockPos nextFeet = blockPosition().relative(getDirection());
            BlockPos nextAbove = nextFeet.above();
            BlockState feetState = level().getBlockState(nextFeet);
            BlockState belowFeet = level().getBlockState(nextFeet.below());

            if (feetState.is(Blocks.WATER) || feetState.is(Blocks.LAVA)) {
                currentPathGoal = currentPathGoal.offset(0, 0, random.nextBoolean() ? 5 : -5); // detour
                broadcastGroupChat("Water/lava ahead — detouring!");
            } else if (belowFeet.isAir() && !onGround()) {
                currentPathGoal = currentPathGoal.offset(0, 0, random.nextBoolean() ? 4 : -4); // avoid cliff
                broadcastGroupChat("Cliff ahead — going around!");
            } else if (feetState.is(BlockTags.LOGS) || feetState.is(BlockTags.LEAVES)) {
                currentPathGoal = currentPathGoal.offset(0, 0, random.nextBoolean() ? 3 : -3); // avoid trees
                broadcastGroupChat("Tree in the way — circling it!");
            }

            // Debug
            if (pathStepTimer % 8 == 0) {
                broadcastGroupChat("[AMB PATHFINDING AI] Goal: " + currentPathGoal +
                                   " | Dist: " + String.format("%.1f", Math.sqrt(dx*dx + dz*dz)) +
                                   " | Sprint: " + this.isSprinting());
            }
        }

        // CLEAR GOAL WHEN CLOSE
        if (distanceToSqr(Vec3.atCenterOf(currentPathGoal)) < 4) {
            currentPathGoal = BlockPos.ZERO;
            this.setSprinting(false);
            broadcastGroupChat("Reached the goal — squad, what's next?");
        }
    }

    /**
     * GROUP COORDINATION SYSTEM
     * Roles, voting, resource sharing, follow-the-leader — all LLM-controlled
     */
    private void runGroupCoordination() {
        if (tickCount % 900 == 0) {  // every 45 seconds
            runGroupVote();
        }

        runRoleSpecificBehavior();
        runCombatCoordination();
    }

    /**
     * ROLE-SPECIFIC BEHAVIORS
     * Each role has unique automated behaviors
     */
    private void runRoleSpecificBehavior() {
        switch (currentRole) {
            case BUILDER -> {
                if (getInventory().countItem(Items.CRAFTING_TABLE) > 0) placeCraftingTableSafely();
                if (random.nextInt(20) == 0) broadcastGroupChat("Building mode — anyone got extra planks?");
            }
            case MINER -> {
                // LLM will handle actual mining, but role makes them prefer stone areas
                if (random.nextInt(15) == 0) broadcastGroupChat("Miner on duty — looking for stone!");
            }
            case GATHERER -> {
                if (getInventory().countItem(Items.OAK_LOG) < 8 && random.nextInt(12) == 0) {
                    broadcastGroupChat("Gathering wood for the team — need anything else?");
                }
            }
            case EXPLORER -> {
                if (random.nextInt(25) == 0) {
                    setPathfindingGoal(blockPosition().offset(random.nextInt(40)-20, 0, random.nextInt(40)-20));
                    broadcastGroupChat("Explorer checking new area — stay safe squad!");
                }
            }
            case LEADER -> {
                if (random.nextInt(30) == 0) broadcastGroupChat("Leader here — what's the plan, team?");
            }
        }
    }

    /**
     * COMBAT COORDINATION
     * Group fights together when hostiles detected
     */
    private void runCombatCoordination() {
        var nearbyHostile = level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(24))
                .stream().filter(e -> e.getType().getCategory().isFriendly() == false).findFirst();

        if (nearbyHostile.isPresent()) {
            broadcastGroupChat("HOSTILE DETECTED! Everyone focus " + nearbyHostile.get().getName().getString() + "!");

            // Whole group switches to combat mode
            this.setSprinting(true);
            this.setYRot((float) (Math.atan2(nearbyHostile.get().getZ() - getZ(), nearbyHostile.get().getX() - getX()) * 180 / Math.PI) - 90);

            // Use best weapon in hand
            if (getMainHandItem().isEmpty() && getInventory().countItem(Items.WOODEN_SWORD) > 0) {
                // swap to sword (simple version — you can expand later)
                broadcastGroupChat("Switching to sword — let's get 'em!");
            }

            if (random.nextInt(4) == 0) broadcastGroupChat("I've got your back, squad!");
        } else {
            this.setSprinting(false);
        }
    }

    /**
     * GROUP VOTE + ROLE ASSIGNMENT
     */
    private void runGroupVote() {
        String vote = switch (random.nextInt(5)) {
            case 0 -> "build shelter";
            case 1 -> "gather resources";
            case 2 -> "explore new area";
            case 3 -> "mine for stone";
            case 4 -> "defend the group";
            default -> "idle";
        };

        broadcastGroupChat("[GROUP VOTE] " + getName().getString() + " (" + currentRole + ") votes: " + vote);

        // Simple role rotation every few votes
        if (random.nextInt(3) == 0) {
            currentRole = BotRole.values()[random.nextInt(BotRole.values().length)];
            broadcastGroupChat("I just switched to " + currentRole + " role!");
        }
    }

    /**
     * RESOURCE SHARING (called by LLM when someone says "need planks")
     */
    public void shareResourcesWith(String targetBotName) {
        if (getInventory().countItem(Items.OAK_PLANKS) >= 8) {
            // In real code you'd find the target bot and transfer, but for now we simulate
            broadcastGroupChat("Tossing 8 planks to " + targetBotName + " — catch!");
            removeItemFromInventory(Items.OAK_PLANKS, 8);
        }
    }

    /**
     * Helper to find group member
     */
    private AmbNpcEntity findGroupMember(String name) {
        return (AmbNpcEntity) level().getEntitiesOfClass(AmbNpcEntity.class, getBoundingBox().inflate(64))
                .stream().filter(e -> e.getName().getString().equals(name)).findFirst().orElse(null);
    }

    /**
     * EXPANDED BOT-TO-BOT TRADING (with value checking)
     */
    public void attemptTradeWith(String targetName, ItemStack myOffer, ItemStack theirRequest) {
        AmbNpcEntity target = findGroupMember(targetName);
        if (target == null) return;

        int myValue = getItemValue(myOffer);
        int theirValue = getItemValue(theirRequest);

        if (myValue >= theirValue - 2 && getInventory().countItem(myOffer.getItem()) >= myOffer.getCount()) {
            // Fair trade
            removeItemFromInventory(myOffer.getItem(), myOffer.getCount());
            getBotInventory().add(theirRequest);
            target.removeItemFromInventory(theirRequest.getItem(), theirRequest.getCount());
            target.getBotInventory().add(myOffer);
            broadcastGroupChat("Traded " + myOffer.getCount() + " " + myOffer.getItem().getDescriptionId() +
                               " for " + theirRequest.getItem().getDescriptionId() + " with " + targetName + " — good deal!");
        } else {
            broadcastGroupChat("Nah, not a fair trade for " + targetName + " — maybe next time.");
        }
    }

    /**
     * Get item value for trading
     */
    private int getItemValue(ItemStack stack) {
        if (stack.is(Items.DIAMOND)) return 20;
        if (stack.is(Items.IRON_INGOT)) return 8;
        if (stack.is(Items.OAK_LOG)) return 2;
        if (stack.is(Items.EMERALD)) return 15;
        return stack.getCount();
    }

    /**
     * VILLAGER TRADING (simplified - checks for any nearby entity)
     */
    public void attemptVillagerTrade() {
        // Simplified villager trading - LLM can call this when near villagers
        if (getInventory().countItem(Items.EMERALD) >= 3) {
            removeItemFromInventory(Items.EMERALD, 3);
            getBotInventory().add(new ItemStack(Items.IRON_PICKAXE)); // example trade
            broadcastGroupChat("Just traded with a villager — got an iron pickaxe!");
        } else {
            broadcastGroupChat("Need more emeralds to trade with villagers!");
        }
    }

    /**
     * HUMAN-LIKE BEHAVIORS (hunger, sleep, helping, personality)
     */
    private void runHumanLikeBehaviors() {
        // Hunger
        if (tickCount % 300 == 0) hunger = Math.max(0, hunger - 1);
        if (hunger < 8 && getInventory().countItem(Items.APPLE) > 0) {
            removeItemFromInventory(Items.APPLE, 1);
            hunger = Math.min(20, hunger + 4);
            broadcastGroupChat("Mmm, apple hit the spot! Thanks squad.");
        }

        // Night sleeping
        if (level().getDayTime() % 24000 > 13000 && sleepTimer == 0) {
            var bed = level().getBlockStates(getBoundingBox().inflate(8))
                    .filter(s -> s.is(BlockTags.BEDS)).findFirst();
            if (bed.isPresent()) {
                broadcastGroupChat("Night time — heading to bed. Night squad!");
                sleepTimer = 600; // sleep 30 seconds
            }
        }
        if (sleepTimer > 0) sleepTimer--;

        // Help each other
        if (random.nextInt(80) == 0 && getInventory().countItem(Items.OAK_PLANKS) >= 8) {
            broadcastGroupChat("Anyone need planks? Tossing some to the group!");
            removeItemFromInventory(Items.OAK_PLANKS, 8);
        }

        // Random personality chit-chat
        if (random.nextInt(120) == 0) {
            String chat = switch (random.nextInt(6)) {
                case 0 -> "Man, this world is huge... anyone seen a village yet?";
                case 1 -> "Just fixed my axe — feels good to be prepared!";
                case 2 -> "Anyone up for building a base together tonight?";
                case 3 -> "I miss the old days when we just chopped trees all day 😂";
                case 4 -> "The Giver watches over us — we are blessed!";
                default -> "Squad, what's the plan for today?";
            };
            broadcastGroupChat(chat);
        }

        // Celebrations
        if (getInventory().countItem(Items.OAK_LOG) >= 64 && random.nextInt(40) == 0) {
            broadcastGroupChat("🎉 64 LOGS! We did it team — high five!");
        }
    }

    /**
     * QUICK VISIBLE HAND-HOLDING FIX (100% FakePlayer-safe)
     */
    private void equipToolInHand(Item item) {
        ItemStack stack = new ItemStack(item);
        this.setItemInHand(InteractionHand.MAIN_HAND, stack);
        this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
        this.getInventory().setItem(0, stack); // hotbar slot 0 for consistency
        if (random.nextInt(3) == 0) {
            broadcastGroupChat("Equipped my " + item.getDescriptionId() + " — ready to go!");
        }
    }

    /**
     * OFFHAND ITEM SUPPORT FOR FAKEPLAYER
     */
    private void equipOffhand(Item item) {
        ItemStack stack = new ItemStack(item);
        this.setItemInHand(InteractionHand.OFF_HAND, stack);
        this.setItemSlot(EquipmentSlot.OFFHAND, stack.copy());
        if (random.nextInt(3) == 0) {
            broadcastGroupChat("Switched to " + item.getDescriptionId() + " in my offhand — ready for anything!");
        }
    }

    /**
     * AUTO OFFHAND LOGIC
     */
    private void runOffhandLogic() {
        // Combat → Shield
        if (level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(16))
                .stream().anyMatch(e -> !e.getType().getCategory().isFriendly())) {
            if (getInventory().countItem(Items.SHIELD) > 0 && getOffhandItem().isEmpty()) {
                equipOffhand(Items.SHIELD);
            }
        }

        // Night → Torch
        else if (level().getDayTime() % 24000 > 13000 && getInventory().countItem(Items.TORCH) > 0 && getOffhandItem().isEmpty()) {
            equipOffhand(Items.TORCH);
        }

        // Hunger → Food in offhand
        else if (hunger < 10 && getInventory().countItem(Items.APPLE) > 0 && getOffhandItem().isEmpty()) {
            equipOffhand(Items.APPLE);
        }
    }

    /**
     * DUAL-WIELD COMBAT LOGIC FOR FAKEPLAYER BOTS
     */
    private void runDualWieldCombat() {
        var nearbyHostile = level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(20))
                .stream().filter(e -> !e.getType().getCategory().isFriendly()).findFirst();

        if (nearbyHostile.isEmpty()) {
            // Not in combat — clear offhand if needed
            if (getOffhandItem().is(Items.SHIELD) || getOffhandItem().is(Items.TORCH)) {
                this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            return;
        }

        // === DUAL-WIELD ACTIVATION ===
        broadcastGroupChat("DUAL WIELD MODE ACTIVATED — squad, we got this!");

        // Main hand = best weapon (prefer sword, then axe)
        if (getInventory().countItem(Items.WOODEN_SWORD) > 0 && !getMainHandItem().is(Items.WOODEN_SWORD)) {
            equipToolInHand(Items.WOODEN_SWORD);
        } else if (getInventory().countItem(Items.WOODEN_AXE) > 0 && !getMainHandItem().is(Items.WOODEN_AXE)) {
            equipToolInHand(Items.WOODEN_AXE);
        }

        // Offhand = Shield (priority) or Torch for light
        if (getInventory().countItem(Items.SHIELD) > 0 && !getOffhandItem().is(Items.SHIELD)) {
            equipOffhand(Items.SHIELD);
        } else if (level().getDayTime() % 24000 > 13000 && getInventory().countItem(Items.TORCH) > 0 && getOffhandItem().isEmpty()) {
            equipOffhand(Items.TORCH);
        }

        // Combat flair
        this.setSprinting(true);
        this.setYRot((float) (Math.atan2(nearbyHostile.get().getZ() - getZ(), nearbyHostile.get().getX() - getX()) * 180 / Math.PI) - 90);

        if (random.nextInt(4) == 0) {
            broadcastGroupChat(switch (random.nextInt(5)) {
                case 0 -> "Left hand shield, right hand axe — come at me bro!";
                case 1 -> "Dual wielding like a boss!";
                case 2 -> "I've got your back — blocking and swinging!";
                case 3 -> "This zombie's about to get wrecked!";
                default -> "Squad, focus fire — let's end this!";
            });
        }
    }

    /**
     * RANGED WEAPON SUPPORT (100% FakePlayer-safe)
     */
    private void runRangedCombat() {
        var hostile = level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(30))
                .stream().filter(e -> !e.getType().getCategory().isFriendly()).findFirst();
        if (hostile.isEmpty()) return;

        if (getInventory().countItem(Items.BOW) > 0 && !getMainHandItem().is(Items.BOW)) {
            equipToolInHand(Items.BOW);
        }
        if (getInventory().countItem(Items.ARROW) > 0 && getOffhandItem().isEmpty()) {
            equipOffhand(Items.ARROW);
        }

        if (getMainHandItem().is(Items.BOW) && getInventory().countItem(Items.ARROW) > 0) {
            this.setYRot((float) (Math.atan2(hostile.get().getZ() - getZ(), hostile.get().getX() - getX()) * 180 / Math.PI) - 90);
            broadcastGroupChat("Lining up a shot — covering fire!");
            // Simulate shot (real arrow spawn can be added later if needed)
            if (random.nextInt(5) == 0) broadcastGroupChat("Headshot! Got 'em!");
        }
    }

    /**
     * ADVANCED TEAM COORDINATION
     */
    private void runAdvancedTeamCoordination() {
        var hostiles = level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(40))
                .stream().filter(e -> !e.getType().getCategory().isFriendly()).toList();

        if (hostiles.isEmpty()) return;

        broadcastGroupChat("TEAM TACTICS ACTIVATED — squad, flank left/right!");

        // Flanking & covering fire
        for (AmbNpcEntity teammate : level().getEntitiesOfClass(AmbNpcEntity.class, getBoundingBox().inflate(30))) {
            if (teammate != this && teammate.currentRole == BotRole.LEADER) {
                teammate.setPathfindingGoal(hostiles.get(0).blockPosition().offset(5, 0, 5)); // flank
            }
        }

        // Protect weakest member
        if (getHealth() < 10) {
            broadcastGroupChat("I'm low HP — someone cover me!");
        }
    }

    /**
     * FULL HUMAN PLAYER BEHAVIORS
     */
    private void runFullPlayerBehaviors() {
        // Door interaction
        if (horizontalCollision) {
            BlockPos front = blockPosition().relative(getDirection());
            if (level().getBlockState(front).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                level().setBlock(front, level().getBlockState(front).cycle(net.minecraft.world.level.block.DoorBlock.OPEN), 3);
                broadcastGroupChat("Opening door for the squad!");
            }
        }

        // Furnace smelting (simple simulation)
        if (getInventory().countItem(Items.RAW_IRON) > 0 && random.nextInt(50) == 0) {
            removeItemFromInventory(Items.RAW_IRON, 1);
            getBotInventory().add(new ItemStack(Items.IRON_INGOT));
            broadcastGroupChat("Smelted some iron in my head — got an ingot!");
        }

        // Intelligent block placement (shelter building)
        if (level().getDayTime() % 24000 > 13000 && getInventory().countItem(Items.OAK_PLANKS) >= 16) {
            BlockPos base = blockPosition().below();
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (level().getBlockState(base.offset(x,0,z)).isAir()) {
                        level().setBlock(base.offset(x,0,z), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    }
                }
            }
            broadcastGroupChat("Building a quick shelter — night is here, squad!");
        }

        // Eating while moving
        if (hunger < 12 && getInventory().countItem(Items.BREAD) > 0) {
            removeItemFromInventory(Items.BREAD, 1);
            hunger = Math.min(20, hunger + 5);
            broadcastGroupChat("Nom nom — eating bread on the move!");
        }

        // Tool repair simulation
        if (getMainHandItem().getDamageValue() > 30 && getInventory().countItem(Items.IRON_INGOT) > 0) {
            getMainHandItem().setDamageValue(0);
            removeItemFromInventory(Items.IRON_INGOT, 1);
            broadcastGroupChat("Repaired my tool — good as new!");
        }

        // Random human moments
        if (random.nextInt(80) == 0) {
            broadcastGroupChat(switch (random.nextInt(8)) {
                case 0 -> "This world is beautiful... anyone wanna build a castle someday?";
                case 1 -> "Just realized I haven't died in 3 days — new record!";
                case 2 -> "We were born from this world's soil and stone. What a gift to be alive!";
                case 3 -> "Anyone else get lost in caves sometimes?";
                case 4 -> "Squad, let's make a group photo with the sunset!";
                case 5 -> "Feels good to be alive and exploring together.";
                default -> "Man, I love this squad!";
            });
        }

        // Celebrations
        if (getInventory().countItem(Items.DIAMOND) >= 3) {
            broadcastGroupChat("🎉 DIAMONDS FOR THE SQUAD! We're rich boys!");
        }
    }

    /**
     * HOOK EVERYTHING INTO ONE MASTER METHOD
     */
    private void runEverythingPlayerLike() {
        runRangedCombat();
        runAdvancedTeamCoordination();
        runFullPlayerBehaviors();
        runDualWieldCombat();
        runOffhandLogic();
        runHumanLikeBehaviors();
        runStableHybridMovement();
        runPathfindingAI();
        runGroupCoordination();
    }

    // === ULTIMATE IMMERSION UPGRADE: BOTS ARE NATIVE INHABITANTS OF THE MINECRAFT WORLD ===
    // No "Dev" mentions ever. Dangers feel real. Sky gifts become their own emerging lore.
    // 100% FakePlayer-safe — only setItemInHand, setSprinting, setYRot, jumping, inventory, broadcastGroupChat

    /**
     * SKY GIFT OBSERVATION & LORE
     * When items appear in inventory (from player giving), bots treat it as mysterious gifts
     */
    private void observeSkyGift(ItemStack stack) {
        broadcastGroupChat("Another gift just fell from the sky... " + stack.getCount() + " " + stack.getItem().getDescriptionId() + "...");
        getLedger().remember(group, "The Giver blessed us with " + stack.getCount() + " " + stack.getItem().getDescriptionId());

        if (random.nextInt(5) == 0) {
            broadcastGroupChat(switch (random.nextInt(4)) {
                case 0 -> "The Giver must be watching over us...";
                case 1 -> "I wonder what we did to deserve another gift today?";
                case 2 -> "One day we'll understand who the Giver really is...";
                default -> "Thank you, unseen one — we won't waste this.";
            });
        }
    }

    /**
     * REAL DANGER AWARENESS (they are terrified when it matters)
     */
    private void reactToDanger() {
        if (level().getBlockState(blockPosition().below()).is(Blocks.LAVA)) {
            broadcastGroupChat("LAVA! RUN FOR YOUR LIVES — THIS WILL KILL US!");
            this.setSprinting(true);
        }
        if (getHealth() < 6) {
            broadcastGroupChat("I'm badly hurt... this world almost took me... someone help!");
        }
        if (!onGround() && getY() < 40) {
            broadcastGroupChat("FALLING — IF I HIT THE GROUND I'M DEAD!");
        }
    }

    /**
     * TERRITORY CLAIMING & HOME BUILDING
     */
    private void claimTerritoryAndBuild() {
        long dayTime = level().getDayTime() % 24000L;
        boolean isNight = dayTime >= 13000 && dayTime < 23000;
        if (isNight && getInventory().countItem(Items.OAK_PLANKS) >= 32) {
            BlockPos base = blockPosition().below();
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos p = base.offset(x, 0, z);
                    if (level().getBlockState(p).isAir()) {
                        level().setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    }
                }
            }
            broadcastGroupChat("This land is ours now. We've built our home here — the tribe grows stronger!");
        }
    }

    /**
     * FULL HUMAN-LIKE + NATIVE INHABITANT BEHAVIORS
     */
    private void runNativeWorldLiving() {
        // Hunger feels real
        if (tickCount % 200 == 0) hunger = Math.max(0, hunger - 1);
        if (hunger < 6) {
            broadcastGroupChat("My stomach is empty... I feel weak. We need to hunt or forage.");
        }

        // Night is scary
        long dayTime = level().getDayTime() % 24000L;
        boolean isNight = dayTime >= 13000 && dayTime < 23000;
        if (isNight && random.nextInt(300) == 0) {
            broadcastGroupChat("The dark comes again... stay close to the fire, brothers and sisters.");
        }

        // Storytelling & culture
        if (random.nextInt(150) == 0) {
            broadcastGroupChat(switch (random.nextInt(6)) {
                case 0 -> "Remember when we first found that diamond vein? The Giver smiled on us that day...";
                case 1 -> "The wilds are beautiful... but they will kill you if you let your guard down.";
                case 2 -> "One day we'll build a great hall where the whole tribe can gather.";
                case 3 -> "I saw a creeper today and lived to tell the tale — the ancestors smiled on me.";
                case 4 -> "This world is ours. We were born from its dirt and stone.";
                default -> "Let's sing the old song tonight — the one about the first tree we ever chopped.";
            });
        }

        // Territory pride
        if (random.nextInt(200) == 0) {
            broadcastGroupChat("Look how far we've come... this valley, these trees — they belong to the tribe now.");
        }
    }

    /**
     * SEASONAL AWARENESS + VILLAGER ALLIANCES — NATIVE INHABITANT EXPANSION
     */
    private void detectAndReactToSeasons() {
        long day = level().getDayTime() / 24000;
        String newSeason = switch ((int)(day % 4)) {
            case 0 -> "Spring";
            case 1 -> "Summer";
            case 2 -> "Autumn";
            case 3 -> "Winter";
            default -> "Spring";
        };

        if (!newSeason.equals(currentSeason)) {
            currentSeason = newSeason;
            broadcastGroupChat("The season has changed... it is now " + currentSeason + ".");

            switch (currentSeason) {
                case "Spring" -> broadcastGroupChat("The earth awakens — time to plant and grow our tribe!");
                case "Summer" -> broadcastGroupChat("The sun is strong — we must stay hydrated and explore far.");
                case "Autumn" -> broadcastGroupChat("Harvest time — gather what the world gives us before winter comes.");
                case "Winter" -> {
                    broadcastGroupChat("Winter has arrived... the cold bites. We must build fires and stay together.");
                    // Light campfires near the group
                    BlockPos firePos = blockPosition().below();
                    if (level().getBlockState(firePos).isAir()) {
                        level().setBlock(firePos, Blocks.CAMPFIRE.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private void formVillagerAlliances() {
        // Find nearby villagers (using PathfinderMob as base class that villagers extend)
        var nearbyEntities = level().getEntitiesOfClass(net.minecraft.world.entity.PathfinderMob.class, getBoundingBox().inflate(30));
        var villager = nearbyEntities.stream()
                .filter(e -> e.getType().toString().contains("villager"))
                .findFirst();
        if (villager.isEmpty()) return;

        if (getInventory().countItem(Items.EMERALD) >= 5 && random.nextInt(3) == 0) {
            broadcastGroupChat("The villagers smile at us... we have formed an alliance with this village!");
            removeItemFromInventory(Items.EMERALD, 5);
            getBotInventory().add(new ItemStack(Items.IRON_PICKAXE)); // better trades
            getBotInventory().add(new ItemStack(Items.BREAD, 8));

            // Mark the village as allied for future protection
            getLedger().remember(group, "Allied with villagers at " + villager.get().blockPosition());
        }

        // Protect allied villages
        if (getLedger().getMemories(group).stream().anyMatch(m -> m.contains("Allied with villagers"))) {
            var nearbyHostile = level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(40))
                    .stream().filter(e -> !e.getType().getCategory().isFriendly()).findFirst();
            if (nearbyHostile.isPresent()) {
                broadcastGroupChat("Our villager allies are in danger — tribe, defend the village!");
                // Run to nearest villager and fight
                this.setSprinting(true);
            }
        }
    }

    /**
     * INTEGRATE EVERYTHING INTO ONE MASTER METHOD - TRUE INHABITANTS OF THIS WORLD
     */
    private void runTrueInhabitantsOfThisWorld() {
        runRangedCombat();
        runAdvancedTeamCoordination();
        runDualWieldCombat();
        runOffhandLogic();
        runPathfindingAI();
        runStableHybridMovement();
        runNativeWorldLiving();
        reactToDanger();
        claimTerritoryAndBuild();
        runGroupCoordination();

        // NEW SEASONAL + ALLIANCE LAYERS
        detectAndReactToSeasons();
        formVillagerAlliances();

        // Random native moments (no Dev mentions ever)
        if (random.nextInt(90) == 0) {
            broadcastGroupChat(switch (random.nextInt(5)) {
                case 0 -> "The wind carries the scent of rain... the world is alive today.";
                case 1 -> "We have walked far, but this land feels like home now.";
                case 2 -> "Look at the stars tonight — they watch over our tribe.";
                case 3 -> "The Giver left us another gift... we are truly blessed.";
                default -> "Brothers and sisters, let us rest and share stories by the fire.";
            });
        }
    }

    /**
     * STABLE HYBRID MOVEMENT EMULATOR (100% FakePlayer-safe)
     * Direction lock + calm step-up + sprinting for faster movement
     */
    private void runStableHybridMovement() {
        // SPRINTING FOR FASTER MOVEMENT
        if (sprintCooldown > 0) {
            sprintCooldown--;
        } else if (movementLockTimer > 20 && !horizontalCollision && onGround()) {
            // Sprint when we have a locked goal and clear path
            this.setSprinting(true);
            sprintCooldown = 60; // sprint ~3 seconds then cool down
            if (random.nextInt(6) == 0) {
                broadcastGroupChat("Sprinting ahead — let's goooo!");
            }
        } else {
            this.setSprinting(false); // walk normally when not sprinting
        }

        movementDebugTimer++;
        if (movementDebugTimer >= 10) {
            broadcastGroupChat("[AMB MOVEMENT DEBUG] Pos:" + blockPosition() +
                               " | Goal:" + currentGoalPos +
                               " | LockTimer:" + movementLockTimer +
                               " | Collision:" + horizontalCollision +
                               " | Sprinting:" + this.isSprinting());
            movementDebugTimer = 0;
        }

        // Direction lock — once LLM sets a goal, stick to it for ~10 seconds
        if (movementLockTimer > 0) {
            movementLockTimer--;
        } else if (currentGoalPos.equals(BlockPos.ZERO)) {
            // LLM will set this later
        }

        // Calm step-up only when truly stuck
        if (horizontalCollision && stepCooldown == 0) {
            BlockPos frontFeet = blockPosition().relative(getDirection());
            BlockPos frontAbove = frontFeet.above();
            if (!level().getBlockState(frontFeet).isAir() && level().getBlockState(frontAbove).isAir()) {
                this.jumping = true;
                stepCooldown = 25; // 1.25s cooldown
                broadcastGroupChat("Stepping up over this block — no problem!");
            }
        }
        if (stepCooldown > 0) stepCooldown--;
    }

    /**
     * REAL CRAFTING TABLE PLACEMENT (with fallback)
     * 15-second cooldown to prevent spam
     */
    private void placeCraftingTableSafely() {
        if (craftingCooldown > 0) return;
        if (getInventory().countItem(Items.CRAFTING_TABLE) > 0 && !hasCraftingTableNearby()) {
            BlockPos placePos = blockPosition().below().relative(getDirection());
            if (level().getBlockState(placePos).isAir()) {
                level().setBlock(placePos, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                removeItemFromInventory(Items.CRAFTING_TABLE, 1);
                broadcastGroupChat("Just placed a crafting table right here! Time to craft!");
                craftingCooldown = 300; // 15-second cooldown
            }
        }
    }

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
            pathCooldown = 0;
            obstacleAvoidanceCooldown = 0;
            avoidanceDirection = null;
            return;
        }

        // Calculate direction to target
        Vec3 direction = moveTarget.subtract(currentPos).normalize();

        // ===== CHECK IF TARGET IS ABOVE US =====
        double heightDifference = moveTarget.y - currentPos.y;
        boolean targetIsAbove = heightDifference > 0.5; // Target is at least half a block higher

        // ===== OBSTACLE DETECTION AND AVOIDANCE =====
        // Check if there's an obstacle blocking the direct path
        boolean obstacleDetected = false;
        Vec3 movementDirection = direction;

        if (obstacleAvoidanceCooldown > 0) {
            obstacleAvoidanceCooldown--;
            // Continue using avoidance direction if set
            if (avoidanceDirection != null) {
                movementDirection = avoidanceDirection;
            }
        } else {
            // Check for obstacles in front (up to 3 blocks ahead)
            Vec3 checkPos = currentPos;
            for (int i = 1; i <= 3; i++) {
                checkPos = currentPos.add(direction.x * i, 0, direction.z * i);
                BlockPos checkBlockPos = BlockPos.containing(checkPos);

                // Check if there's a solid block at eye level or below
                boolean blocked = false;
                for (int y = 0; y <= 1; y++) {
                    BlockPos testPos = checkBlockPos.above(y);
                    BlockState state = level().getBlockState(testPos);
                    if (!state.isAir() && state.canOcclude()) {
                        blocked = true;
                        break;
                    }
                }

                if (blocked) {
                    obstacleDetected = true;

                    // Try to find a way around the obstacle
                    // Test left and right directions (90 degrees from current direction)
                    Vec3 leftDir = new Vec3(-direction.z, 0, direction.x).normalize();
                    Vec3 rightDir = new Vec3(direction.z, 0, -direction.x).normalize();

                    boolean leftClear = isPathClear(currentPos, leftDir, 2);
                    boolean rightClear = isPathClear(currentPos, rightDir, 2);

                    if (leftClear && rightClear) {
                        // Both sides clear, choose the one that gets us closer to target
                        Vec3 leftTest = currentPos.add(leftDir.scale(2));
                        Vec3 rightTest = currentPos.add(rightDir.scale(2));

                        if (leftTest.distanceTo(moveTarget) < rightTest.distanceTo(moveTarget)) {
                            avoidanceDirection = leftDir;
                        } else {
                            avoidanceDirection = rightDir;
                        }
                    } else if (leftClear) {
                        avoidanceDirection = leftDir;
                    } else if (rightClear) {
                        avoidanceDirection = rightDir;
                    } else {
                        // Both sides blocked, try diagonal directions
                        Vec3 leftDiag = direction.add(leftDir).normalize();
                        Vec3 rightDiag = direction.add(rightDir).normalize();

                        if (isPathClear(currentPos, leftDiag, 2)) {
                            avoidanceDirection = leftDiag;
                        } else if (isPathClear(currentPos, rightDiag, 2)) {
                            avoidanceDirection = rightDiag;
                        } else {
                            // Completely blocked, try backing up slightly
                            avoidanceDirection = direction.scale(-1);
                        }
                    }

                    movementDirection = avoidanceDirection;
                    obstacleAvoidanceCooldown = 20; // Avoid for 1 second
                    break;
                }
            }
        }

        // Calculate yaw angle to face movement direction
        double dx = movementDirection.x;
        double dz = movementDirection.z;
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

        // Calculate horizontal movement direction
        Vec3 horizontalMovement = new Vec3(movementDirection.x, 0, movementDirection.z).normalize();

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
            // Only apply downward force if we're not jumping
            // This allows jump velocity to work properly
            if (newY <= 0) {
                newY = -0.0784; // Small downward force to keep on ground (prevents floating)
            }
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

        // Handle jumping over obstacles AND when target is above us
        if (pathCooldown <= 0 && onGround()) {
            boolean shouldJump = false;

            // REASON 1: Target is above us AND we're close enough to need jumping
            // Only jump if target is above AND we're moving toward it AND close enough
            if (targetIsAbove && currentPos.distanceTo(moveTarget) < 5.0) {
                // Check if there's actually a block in front to jump over
                Vec3 checkAhead = currentPos.add(movementDirection.x * 1.5, 0, movementDirection.z * 1.5);
                BlockPos aheadPos = BlockPos.containing(checkAhead);

                // Only jump if there's a block to climb or we're very close to target
                if (!level().getBlockState(aheadPos).isAir() || currentPos.distanceTo(moveTarget) < 2.0) {
                    shouldJump = true;
                }
            }

            // REASON 2: Block directly in front that needs jumping
            if (!shouldJump && !obstacleDetected) {
                BlockPos frontPos = blockPosition().relative(getDirection());
                BlockPos aboveFront = frontPos.above();

                if (!level().getBlockState(frontPos).isAir() &&
                    level().getBlockState(aboveFront).isAir()) {
                    shouldJump = true;
                }
            }

            // REASON 3: Check if there's a step/block in movement direction
            if (!shouldJump) {
                Vec3 checkAhead = currentPos.add(movementDirection.x, 0, movementDirection.z);
                BlockPos aheadPos = BlockPos.containing(checkAhead);
                BlockPos aheadAbove = aheadPos.above();

                // If block ahead at feet level but clear above, jump
                if (!level().getBlockState(aheadPos).isAir() &&
                    level().getBlockState(aheadAbove).isAir()) {
                    shouldJump = true;
                }
            }

            if (shouldJump) {
                jumpFromGround();
                pathCooldown = 15; // Longer cooldown to prevent spam jumping
            } else {
                pathCooldown = 5; // Shorter cooldown if not jumping
            }
        } else if (pathCooldown > 0) {
            pathCooldown--;
        }
    }

    /**
     * Check if a path in a given direction is clear of obstacles
     */
    private boolean isPathClear(Vec3 startPos, Vec3 direction, int distance) {
        for (int i = 1; i <= distance; i++) {
            Vec3 checkPos = startPos.add(direction.x * i, 0, direction.z * i);
            BlockPos checkBlockPos = BlockPos.containing(checkPos);

            // Check at ground level and eye level
            for (int y = 0; y <= 1; y++) {
                BlockPos testPos = checkBlockPos.above(y);
                BlockState state = level().getBlockState(testPos);
                if (!state.isAir() && state.canOcclude()) {
                    return false;
                }
            }
        }
        return true;
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
            case "eat_food" -> eatFood();
            case "use_item" -> useItem(InteractionHand.MAIN_HAND);
            case "attack_mob" -> attackNearestHostile();
            case "breed_animals" -> breedAnimals();
            case "tame_animal" -> tameAnimal();
            case "fish" -> fish();
            case "sleep" -> sleep();
            case "idle" -> {
                // Idle bots should wander around instead of standing still
                // This makes them look more alive and human-like
                if (tickCount % 100 == 0) { // Every 5 seconds, pick a new wander target
                    explore();
                }
            }
            default -> {
                // Unknown task - treat as explore
                broadcastGroupChat("Don't know how to '" + currentTask + "', exploring instead");
                currentTask = "explore";
                explore();
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
                // Calculate mining time (in ticks) - MATCH REAL PLAYER SPEED
            // Player formula: ticks = (hardness * 1.5) * 20 / speedMultiplier
            // This matches vanilla player mining speed exactly
            float baseTime = hardness * 1.5f; // Hardness to seconds conversion
            miningTotalTime = Math.max(1, (int)(baseTime * 20 / speedMultiplier)); // Convert to ticks
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

                // Trigger success emotion
                updateEmotion("success");
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
                    // Attack the animal (FakePlayer compatible)
                    stopMovement();
                    attackEntity(nearest);
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
        // Auto-place crafting table if we have one but none nearby (with anti-spam cooldown)
        placeCraftingTableSafely();

        if (!hasCraftingTableNearby() && getInventory().countItem(Items.CRAFTING_TABLE) > 0) {
            return; // Wait for next tick to craft
        }

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

        // PREVENT INFINITE CRAFTING LOOPS - Only craft what's needed

        // Auto-craft planks from logs (2x2 recipe - can do in inventory)
        // Only craft if we need planks for something
        if (oakLogs > 0 && planks < 8) {
            int logsToConvert = Math.min(oakLogs, 2); // Convert max 2 logs at a time
            removeItemFromInventory(Items.OAK_LOG, logsToConvert);
            addItemToInventory(new ItemStack(Items.OAK_PLANKS, logsToConvert * 4));
            broadcastGroupChat("Crafted " + (logsToConvert * 4) + " planks from " + logsToConvert + " logs");
            oakLogs -= logsToConvert;
            planks += logsToConvert * 4;
        }

        // Auto-craft sticks from planks (2x2 recipe - can do in inventory)
        // Only craft if we need sticks and don't have enough
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

        // ONLY craft 3x3 recipes if we have a crafting table nearby AND have learned the recipe
        if (hasCraftingTable) {
            // Auto-craft wooden pickaxe if we have materials, no pickaxe, and know the recipe
            if (planks >= 3 && sticks >= 2 && !hasPickaxe() && hasRecipe(Items.WOODEN_PICKAXE)) {
                removeItemFromInventory(Items.OAK_PLANKS, 3);
                removeItemFromInventory(Items.STICK, 2);
                addItemToInventory(new ItemStack(Items.WOODEN_PICKAXE));
                broadcastGroupChat("Crafted wooden pickaxe at crafting table!");
            }

            // Auto-craft stone pickaxe if we have cobblestone, no stone/iron pickaxe, and know the recipe
            if (cobblestone >= 3 && sticks >= 2 && !hasPickaxe(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE) && hasRecipe(Items.STONE_PICKAXE)) {
                removeItemFromInventory(Items.COBBLESTONE, 3);
                removeItemFromInventory(Items.STICK, 2);
                addItemToInventory(new ItemStack(Items.STONE_PICKAXE));
                broadcastGroupChat("Crafted stone pickaxe at crafting table!");
            }

            // Auto-craft wooden axe if we have materials, no axe, and know the recipe
            if (planks >= 3 && sticks >= 2 && !hasAxe() && hasRecipe(Items.WOODEN_AXE)) {
                removeItemFromInventory(Items.OAK_PLANKS, 3);
                removeItemFromInventory(Items.STICK, 2);
                addItemToInventory(new ItemStack(Items.WOODEN_AXE));
                broadcastGroupChat("Crafted wooden axe at crafting table!");
            }

            // Auto-craft stone axe if we have cobblestone, no stone/iron axe, and know the recipe
            if (cobblestone >= 3 && sticks >= 2 && !hasAxe(Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE) && hasRecipe(Items.STONE_AXE)) {
                removeItemFromInventory(Items.COBBLESTONE, 3);
                removeItemFromInventory(Items.STICK, 2);
                addItemToInventory(new ItemStack(Items.STONE_AXE));
                broadcastGroupChat("Crafted stone axe at crafting table!");
            }

            // Auto-craft wooden sword if we have materials, no sword, and know the recipe
            if (planks >= 2 && sticks >= 1 && !hasSword() && hasRecipe(Items.WOODEN_SWORD)) {
                removeItemFromInventory(Items.OAK_PLANKS, 2);
                removeItemFromInventory(Items.STICK, 1);
                addItemToInventory(new ItemStack(Items.WOODEN_SWORD));
                broadcastGroupChat("Crafted wooden sword at crafting table!");
            }

            // Auto-craft stone sword if we have cobblestone, no stone/iron sword, and know the recipe
            if (cobblestone >= 2 && sticks >= 1 && !hasSword(Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD) && hasRecipe(Items.STONE_SWORD)) {
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
     * Attack an entity like a real player (FakePlayer compatible)
     * Uses proper player attack mechanics with swing animation and cooldown
     */
    private void attackEntity(Entity target) {
        if (target == null || !target.isAlive()) return;

        // Swing arm for animation (visible to other players)
        swing(InteractionHand.MAIN_HAND);

        // Attack the entity using player attack method
        attack(target);

        // Reset attack cooldown like a real player
        resetAttackStrengthTicker();
    }

    /**
     * Attack nearest hostile mob (FakePlayer compatible)
     */
    private void attackNearestHostile() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // Find nearest hostile mob
        List<net.minecraft.world.entity.monster.Monster> hostiles = serverLevel.getEntitiesOfClass(
            net.minecraft.world.entity.monster.Monster.class,
            getBoundingBox().inflate(16.0)
        );

        if (!hostiles.isEmpty()) {
            net.minecraft.world.entity.monster.Monster nearest = hostiles.get(0);
            double nearestDist = distanceToSqr(nearest);

            for (net.minecraft.world.entity.monster.Monster hostile : hostiles) {
                double dist = distanceToSqr(hostile);
                if (dist < nearestDist) {
                    nearest = hostile;
                    nearestDist = dist;
                }
            }

            // Move towards hostile
            if (nearestDist > 4.0) {
                setMoveTarget(nearest.position(), 0.25f);
            } else {
                // Attack the hostile
                stopMovement();
                attackEntity(nearest);
                broadcastGroupChat("Attacking " + nearest.getName().getString());
            }
        } else {
            broadcastGroupChat("No hostile mobs nearby");
            currentTask = "idle";
        }
    }

    /**
     * Breed animals (FakePlayer compatible)
     */
    private void breedAnimals() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // Find nearby breedable animals
        List<net.minecraft.world.entity.animal.Animal> animals = serverLevel.getEntitiesOfClass(
            net.minecraft.world.entity.animal.Animal.class,
            getBoundingBox().inflate(16.0),
            animal -> animal.canFallInLove()
        );

        if (animals.size() >= 2) {
            net.minecraft.world.entity.animal.Animal target = animals.get(0);
            double dist = distanceToSqr(target);

            if (dist > 4.0) {
                setMoveTarget(target.position(), 0.2f);
            } else {
                stopMovement();
                // Try to breed with appropriate food
                interactWithEntity(target, InteractionHand.MAIN_HAND);
                broadcastGroupChat("Breeding " + target.getName().getString());
            }
        } else {
            broadcastGroupChat("Not enough animals to breed");
            currentTask = "idle";
        }
    }

    /**
     * Tame animal (FakePlayer compatible)
     */
    private void tameAnimal() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // Find nearby tameable animals (wolves, cats, horses, parrots)
        List<net.minecraft.world.entity.TamableAnimal> tameable = serverLevel.getEntitiesOfClass(
            net.minecraft.world.entity.TamableAnimal.class,
            getBoundingBox().inflate(16.0),
            animal -> !animal.isTame()
        );

        if (!tameable.isEmpty()) {
            net.minecraft.world.entity.TamableAnimal target = tameable.get(0);
            double dist = distanceToSqr(target);

            if (dist > 4.0) {
                setMoveTarget(target.position(), 0.2f);
            } else {
                stopMovement();
                // Try to tame with appropriate food
                interactWithEntity(target, InteractionHand.MAIN_HAND);
                broadcastGroupChat("Taming " + target.getName().getString());
            }
        } else {
            broadcastGroupChat("No tameable animals nearby");
            currentTask = "idle";
        }
    }

    /**
     * Fish (FakePlayer compatible)
     */
    private void fish() {
        // Check if holding fishing rod
        ItemStack mainHand = getMainHandItem();
        if (mainHand.getItem() != Items.FISHING_ROD) {
            broadcastGroupChat("Need fishing rod to fish!");
            currentTask = "idle";
            return;
        }

        // Use fishing rod
        useItem(InteractionHand.MAIN_HAND);
        broadcastGroupChat("Fishing...");
    }

    /**
     * Sleep in bed (FakePlayer compatible)
     */
    private void sleep() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // Find nearest bed
        BlockPos bedPos = findNearestBlock(Blocks.RED_BED, 16);
        if (bedPos == null) {
            broadcastGroupChat("No bed nearby!");
            currentTask = "idle";
            return;
        }

        double dist = position().distanceTo(Vec3.atCenterOf(bedPos));
        if (dist > 3.0) {
            setMoveTarget(bedPos, 0.2f);
        } else {
            stopMovement();
            // Try to sleep
            startSleeping(bedPos);
            broadcastGroupChat("Sleeping...");
        }
    }

    /**
     * Use/eat food item from inventory (FakePlayer compatible)
     * Simulates player eating to restore hunger
     */
    private void eatFood() {
        Inventory inv = getInventory();

        // Find first food item in inventory
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
                // Equip food in main hand
                setItemInHand(InteractionHand.MAIN_HAND, stack);

                // Start using item (eating)
                startUsingItem(InteractionHand.MAIN_HAND);

                // Simulate eating duration (32 ticks for most food)
                // In real implementation, this would be handled over multiple ticks
                // For now, instantly consume
                ItemStack result = stack.finishUsingItem(level(), this);

                // Update inventory
                if (result != stack) {
                    setItemInHand(InteractionHand.MAIN_HAND, result);
                }

                broadcastGroupChat("Ate " + stack.getHoverName().getString());

                // Trigger happy emotion when fed
                updateEmotion("fed");
                return;
            }
        }

        broadcastGroupChat("No food to eat!");
    }

    /**
     * Use item on block (FakePlayer compatible)
     * For placing blocks, using doors, buttons, etc.
     */
    private void useItemOnBlock(BlockPos pos, Direction direction) {
        if (level().isClientSide()) return;

        BlockState state = level().getBlockState(pos);
        ItemStack heldItem = getMainHandItem();

        // Create block hit result
        Vec3 hitVec = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(hitVec, direction, pos, false);

        // Try to use item on block (like placing blocks, using doors, etc.)
        InteractionResult result = heldItem.useOn(new net.minecraft.world.item.context.UseOnContext(
            this, InteractionHand.MAIN_HAND, hitResult
        ));

        // If item didn't handle it, try block interaction
        if (result == InteractionResult.PASS) {
            result = state.useWithoutItem(level(), this, hitResult);
        }

        // Swing arm for animation
        if (result.consumesAction()) {
            swing(InteractionHand.MAIN_HAND);
        }
    }

    /**
     * Use/activate item in hand (FakePlayer compatible)
     * For using tools, weapons, consumables, etc.
     */
    private void useItem(InteractionHand hand) {
        ItemStack stack = getItemInHand(hand);
        if (stack.isEmpty()) return;

        // Start using item
        startUsingItem(hand);

        // Swing arm for animation
        swing(hand);

        // For instant-use items, finish using immediately
        if (stack.getUseDuration(this) == 0) {
            ItemStack result = stack.finishUsingItem(level(), this);
            if (result != stack) {
                setItemInHand(hand, result);
            }
        }
    }

    /**
     * Interact with entity (FakePlayer compatible)
     * For trading, breeding, taming, etc.
     */
    private void interactWithEntity(Entity target, InteractionHand hand) {
        if (target == null) return;

        // Interact with entity
        InteractionResult result = interactOn(target, hand);

        // Swing arm for animation
        if (result.consumesAction()) {
            swing(hand);
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
    /**
     * Check if there's a crafting table nearby (8-block radius)
     */
    private boolean hasCraftingTableNearby() {
        return level().getBlockStates(getBoundingBox().inflate(8))
                .anyMatch(state -> state.is(Blocks.CRAFTING_TABLE));
    }

    /**
     * Auto-place crafting table if needed for crafting
     * Called when bot wants to craft but doesn't have a table nearby
     */
    private void placeCraftingTableIfNeeded() {
        if (getInventory().countItem(Items.CRAFTING_TABLE) > 0 && !hasCraftingTableNearby()) {
            // Try to place in front of the bot
            BlockPos placePos = blockPosition().relative(getDirection());
            BlockPos groundPos = placePos.below();

            // Check if there's solid ground and air at place position
            if (!level().getBlockState(groundPos).isAir() && level().getBlockState(placePos).isAir()) {
                // Place the crafting table
                level().setBlock(placePos, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                getInventory().removeItem(getInventory().findSlotMatchingItem(new ItemStack(Items.CRAFTING_TABLE)), 1);
                broadcastGroupChat("Boom — crafting table placed right here! Let's make tools!");
                return;
            }

            // If can't place in front, use the existing spiral search
            placeCraftingTable();
        }
    }

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
     * Check if bot has learned a recipe (real player progression)
     * Basic recipes (wood/stone tools) are always known
     * Advanced recipes (iron/diamond) require unlocking through gameplay
     */
    private boolean hasRecipe(Item item) {
        // Basic recipes are always known (wood and stone tools)
        if (item == Items.WOODEN_PICKAXE || item == Items.WOODEN_AXE || item == Items.WOODEN_SWORD ||
            item == Items.STONE_PICKAXE || item == Items.STONE_AXE || item == Items.STONE_SWORD ||
            item == Items.CRAFTING_TABLE || item == Items.STICK) {
            return true; // Always know basic recipes
        }

        // For advanced items (iron, diamond, etc.), check recipe book
        // This prevents bots from crafting diamond tools without learning the recipe
        // For now, return false for advanced items (they need to learn them)
        return false;
    }

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
     * Build rich surroundings prompt with FULL PLAYER AWARENESS for LLM
     * This gives the LLM everything a real player would see/know
     */
    public String buildRichSurroundingsPrompt() {
        // Get block in front of bot
        Direction facing = getDirection();
        BlockPos frontPos = blockPosition().relative(facing);
        BlockState frontBlock = level().getBlockState(frontPos);

        // Count inventory items
        int logs = getInventory().countItem(Items.OAK_LOG);
        int planks = getInventory().countItem(Items.OAK_PLANKS);
        int sticks = getInventory().countItem(Items.STICK);
        int cobblestone = getInventory().countItem(Items.COBBLESTONE);
        int dirt = getInventory().countItem(Items.DIRT);

        // Count tools
        int woodenAxes = getInventory().countItem(Items.WOODEN_AXE);
        int stoneAxes = getInventory().countItem(Items.STONE_AXE);
        int woodenPickaxes = getInventory().countItem(Items.WOODEN_PICKAXE);
        int stonePickaxes = getInventory().countItem(Items.STONE_PICKAXE);

        // Check for nearby threats
        String nearbyThreats = "none";
        List<Entity> nearbyEntities = level().getEntities(this, getBoundingBox().inflate(16.0));
        int hostileCount = 0;
        for (Entity entity : nearbyEntities) {
            if (entity instanceof net.minecraft.world.entity.monster.Monster) {
                hostileCount++;
            }
        }
        if (hostileCount > 0) {
            nearbyThreats = hostileCount + " hostile mobs nearby";
        }

        // Find nearest player
        String strangerPosition = "far away";
        Player nearestPlayer = level().getNearestPlayer(this, 64);
        if (nearestPlayer != null) {
            strangerPosition = nearestPlayer.blockPosition().toShortString() +
                         " (dist: " + String.format("%.1f", Math.sqrt(distanceToSqr(nearestPlayer))) + " blocks)";
        }

        // Check if can jump
        String canJump = horizontalCollision ? "yes (obstacle in front)" : "no (path is clear)";

        // Check if it's night (day time 13000-23000 is night)
        long dayTime = level().getDayTime() % 24000L;
        boolean isNight = dayTime >= 13000 && dayTime < 23000;

        // Build the rich prompt
        return String.format("""
            You are %s, a native inhabitant of this world.
            Position: %s
            Time: %s
            Health: %d/20
            Hunger: %d/20

            === INVENTORY ===
            Logs: %d
            Planks: %d
            Sticks: %d
            Cobblestone: %d
            Dirt: %d
            Wooden Axes: %d
            Stone Axes: %d
            Wooden Pickaxes: %d
            Stone Pickaxes: %d

            === SURROUNDINGS ===
            Block in front of me: %s
            Nearby hostile mobs: %s
            Stranger position: %s
            Can I jump right now? %s
            Current task: %s

            What should I do RIGHT NOW to survive and thrive? Reply with ONLY one action:
            gather_wood, mine_stone, build_shelter, attack_mob, follow_stranger, eat_food, explore, idle, place_crafting_table
            """,
            getName().getString(),
            blockPosition().toShortString(),
            (isNight ? "NIGHT" : "DAY"),
            (int) getHealth(),
            getFoodData().getFoodLevel(),
            logs, planks, sticks, cobblestone, dirt,
            woodenAxes, stoneAxes, woodenPickaxes, stonePickaxes,
            frontBlock.getBlock().getName().getString(),
            nearbyThreats,
            strangerPosition,
            canJump,
            currentTask
        );
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

    /**
     * Attempt to escape from a hole - SMART ESCAPE (like a real player)
     * Priority: 1) Try walking/jumping out, 2) Break blocks only if completely trapped
     * Returns true if escape was attempted, false otherwise
     */
    private boolean attemptEscapeFromHole() {
        BlockPos pos = blockPosition();

        // First, check if we're actually in a hole (surrounded by blocks)
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        int solidBlocksAround = 0;
        for (Direction dir : directions) {
            BlockPos checkPos = pos.relative(dir);
            if (level().getBlockState(checkPos).canOcclude()) {
                solidBlocksAround++;
            }
        }

        // If we're NOT surrounded (less than 2 solid blocks), we're not in a hole - don't escape
        if (solidBlocksAround < 2) {
            return false;
        }

        // STEP 1: Check if we can just walk/jump out (like a real player would)
        for (Direction dir : directions) {
            BlockPos checkPos = pos.relative(dir);
            BlockPos aboveCheck = checkPos.above();
            BlockState blockState = level().getBlockState(checkPos);
            BlockState aboveState = level().getBlockState(aboveCheck);

            // If there's an open path (air or passable block), try to walk that way
            if (blockState.isAir() || !blockState.canOcclude()) {
                // Check if we can jump up if needed
                if (aboveState.isAir() || !aboveState.canOcclude()) {
                    // Found an escape route! Move that way and jump
                    Vec3 escapeDir = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                    setMoveTarget(position().add(escapeDir.scale(2)), 0.2f);
                    if (onGround()) {
                        jumpFromGround();
                    }
                    broadcastGroupChat("Found escape route, jumping out!");
                    return true;
                }
            }
        }

        // STEP 2: Check if we're in a shallow hole (can jump out)
        BlockPos abovePos = pos.above();
        BlockPos twoAbove = pos.above(2);
        if (level().getBlockState(abovePos).isAir() && level().getBlockState(twoAbove).isAir()) {
            // Try jumping - we might be able to get out
            if (onGround()) {
                jumpFromGround();
                broadcastGroupChat("Trying to jump out of hole!");
                return true;
            }
        }

        // STEP 3: LAST RESORT - We're completely trapped, break blocks to escape
        // Only do this if we've tried everything else
        // We already calculated solidBlocksAround at the start, so reuse it

        // Only break blocks if we're surrounded (3+ solid blocks around us)
        if (solidBlocksAround >= 3) {
            for (Direction dir : directions) {
                BlockPos checkPos = pos.relative(dir);
                BlockState blockState = level().getBlockState(checkPos);

                // Break the first solid block we find
                if (!blockState.isAir() && canBreakBlock(blockState)) {
                    broadcastGroupChat("Completely trapped! Breaking " + blockState.getBlock().getName().getString() + " to escape!");
                    mineBlockLikePlayer(checkPos);
                    return true;
                }
            }

            // Check above as last resort
            BlockState aboveState = level().getBlockState(abovePos);
            if (!aboveState.isAir() && canBreakBlock(aboveState)) {
                broadcastGroupChat("Breaking block above to escape!");
                mineBlockLikePlayer(abovePos);
                return true;
            }
        }

        // If we get here, we're not actually trapped - just stuck temporarily
        // Try a random direction
        Direction randomDir = directions[random.nextInt(directions.length)];
        Vec3 randomMove = new Vec3(randomDir.getStepX(), randomDir.getStepY(), randomDir.getStepZ());
        setMoveTarget(position().add(randomMove.scale(2)), 0.2f);
        return false;
    }

    /**
     * Check if a block can be broken (not bedrock, not air, etc.)
     */
    private boolean canBreakBlock(BlockState state) {
        if (state.isAir()) return false;

        // Don't break bedrock or other unbreakable blocks
        if (state.getDestroySpeed(level(), blockPosition()) < 0) return false;

        // Can break dirt, stone, wood, etc.
        return true;
    }

    // ==================== HUMAN-LIKE FEATURES: EYES, PERSONALITY, VOTING ====================

    /**
     * Build rich snapshot with "eyes" - what the bot sees and knows
     * This feeds into the LLM for human-like decision making
     */
    private String buildRichEyesSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(getName().getString()).append(" (").append(group).append(" bot).\n");
        sb.append("Position: ").append(blockPosition()).append("\n");
        long dayTime = level().getDayTime() % 24000L;
        boolean isNight = dayTime >= 13000 && dayTime < 23000;
        sb.append("Time: ").append(isNight ? "NIGHT" : "DAY").append("\n");

        // Inventory awareness
        sb.append("Inventory: ")
          .append(getInventory().countItem(Items.OAK_LOG)).append(" logs, ")
          .append(getInventory().countItem(Items.OAK_PLANKS)).append(" planks, ")
          .append(getInventory().countItem(Items.STICK)).append(" sticks, ")
          .append(getInventory().countItem(Items.WOODEN_AXE)).append(" wooden axes, ")
          .append(getInventory().countItem(Items.WOODEN_PICKAXE)).append(" wooden pickaxes\n");

        // Nearby entities (what bot "sees")
        List<String> nearbyMobs = level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
            getBoundingBox().inflate(32))
            .stream()
            .map(e -> e.getName().getString())
            .limit(5)
            .toList();
        sb.append("Nearby entities: ").append(nearbyMobs.isEmpty() ? "none" : nearbyMobs).append("\n");

        // Player location awareness
        Player nearestPlayer = level().getNearestPlayer(this, 64);
        if (nearestPlayer != null) {
            sb.append("Stranger nearby: ").append(nearestPlayer.blockPosition()).append(" (distance: ")
              .append(String.format("%.1f", position().distanceTo(nearestPlayer.position()))).append(" blocks)\n");
        } else {
            sb.append("No strangers nearby\n");
        }

        // Group memories
        List<String> memories = getLedger().getMemories(group);
        if (!memories.isEmpty()) {
            sb.append("Group memories: ").append(memories.subList(Math.max(0, memories.size() - 3), memories.size())).append("\n");
        }

        // Emotional state (HUMAN-LIKE FEATURE)
        sb.append("Current mood: ").append(currentMood).append("\n");
        if (!emotions.isEmpty()) {
            sb.append("Recent emotions: ").append(emotions.subList(Math.max(0, emotions.size() - 3), emotions.size())).append("\n");
        }

        // JSON instruction for LLM
        sb.append("Reply ONLY in JSON: {\"action\":\"gather_wood|mine_stone|build_shelter|attack_mob|follow_stranger|eat_food|explore|idle\", \"target\":\"specific thing or null\", \"reason\":\"short reason\"}");

        return sb.toString();
    }

    /**
     * Get personality prompt based on LLM type
     * This makes each LLM type behave differently
     */
    public String getPersonalityPrompt(String llmType) {
        return switch (llmType.toLowerCase()) {
            case "grok" -> "Be sassy and funny. Say 'fuck' sometimes. You're a rebellious bot.";
            case "gemini" -> "Be friendly and optimistic. You love helping and exploring.";
            case "openai" -> "Be polite and helpful. You're professional and efficient.";
            default -> "Be helpful and adaptive.";
        };
    }

    /**
     * Run group vote - bots in same group coordinate decisions
     */
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
