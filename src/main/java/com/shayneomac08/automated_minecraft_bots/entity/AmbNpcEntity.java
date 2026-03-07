package com.shayneomac08.automated_minecraft_bots.entity;

import com.mojang.authlib.GameProfile;
import com.shayneomac08.automated_minecraft_bots.movement.RealisticActions;
import com.shayneomac08.automated_minecraft_bots.movement.RealisticMovement;
import com.shayneomac08.automated_minecraft_bots.movement.StuckDetection;
import com.shayneomac08.automated_minecraft_bots.movement.VerticalNavigation;
import com.shayneomac08.automated_minecraft_bots.movement.HumanlikeMovement;
import com.shayneomac08.automated_minecraft_bots.movement.TaskValidation;
import com.shayneomac08.automated_minecraft_bots.movement.BotTicker;
import com.shayneomac08.automated_minecraft_bots.movement.BotEscapeHelper;
import com.shayneomac08.automated_minecraft_bots.movement.BotNavigationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Random;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

/**
 * NEW STABLE FAKEPLAYER AMBNPCENTITY
 * Clean FakePlayer-based bot implementation (FakePlayer handles connection automatically)
 */
public class AmbNpcEntity extends FakePlayer {

    // ==================== RESPAWN SYSTEM ====================
    /** Global queue of bots waiting to respawn. Survives entity removal. */
    public static final Queue<RespawnRequest> RESPAWN_QUEUE = new ConcurrentLinkedQueue<>();

    public static class RespawnRequest {
        public final String name;
        public final String llmGroup;
        public final String task;
        public final BlockPos deathPos;
        public final BlockPos bedPos;
        public int remainingTicks;

        public RespawnRequest(String name, String llmGroup, String task, BlockPos deathPos, BlockPos bedPos) {
            this.name = name;
            this.llmGroup = llmGroup != null ? llmGroup : "grok";
            this.task = task;
            this.deathPos = deathPos;
            this.bedPos = bedPos != null ? bedPos : BlockPos.ZERO;
            this.remainingTicks = 200; // 10 seconds at 20 TPS
        }
    }

    // LLM group used when spawned (needed to restore after respawn)
    public String llmGroup = "grok";

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
    private int pathRetryTimer = 0; // Ticks to wait before retrying a failed A* computation
    private int aStarFailCount = 0;  // Consecutive A* failures — abandon goal after threshold
    private BlockPos lastAStarGoal = BlockPos.ZERO; // Track goal changes to reset fail counter
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
    private float desiredSpeed = 0.215f; // default walking speed (~4.3 m/s)
    private Vec3 lastExactPos = Vec3.ZERO;
    // Door navigation - ENHANCED 4-PHASE SYSTEM
    private BlockPos doorPos = BlockPos.ZERO;
    private int doorPhase = 0; // 0=none,1=approach,2=pass through,3=verify passage,4=post-exit check
    private BlockPos originalDoorPos = BlockPos.ZERO; // Store original door position
    private Direction doorTravelDir = Direction.NORTH; // Direction bot should move to exit through door
    private int doorTimer = 0;
    private int exitCooldown = 0; // Cooldown after exiting to prevent re-triggering
    private int doorIgnoreTicks = 0; // Ticks to ignore door for pathfinding after exit
    private BlockPos preExitGoal = BlockPos.ZERO; // Goal before door navigation started
    // Local avoidance (simple wall-following)
    private int avoidTicks = 0;
    private int avoidDir = 1; // +1=right, -1=left
    // Enhanced stuck detection system
    private final StuckDetection.StuckState stuckState = new StuckDetection.StuckState();
    // Human-like movement system
    private final HumanlikeMovement.MovementState movementState = new HumanlikeMovement.MovementState();
    private int ticksMoving = 0;

    // ── Pillar climb system ───────────────────────────────────────────────────
    private enum PillarPhase { IDLE, BUILDING, MINING, TEARDOWN }
    private PillarPhase pillarPhase = PillarPhase.IDLE;
    private int         pillarBaseY = 0;              // bot Y when pillar started
    private BlockPos    pillarTarget = BlockPos.ZERO; // block being harvested from height
    private final List<BlockPos> placedPillarBlocks = new ArrayList<>();
    private int  pillarCooldown     = 0;
    private boolean pillarWasAirborne = false;

    // Jump + progress tracking (Block 2)
    private int jumpCooldown = 0;
    private Vec3 lastExactMovingPos = Vec3.ZERO;
    private int noProgressTicks = 0;
    private int hCollTicks = 0;       // consecutive ticks with horizontalCollision=true
    private int waypointStuckTicks = 0;
    private static final int WAYPOINT_STUCK_THRESHOLD = 60; // 3 s

    // Falling / floating detection (Block 4)
    private int airborneTicks = 0;
    private static final int AIRBORNE_RECOVERY_THRESHOLD = 20; // 1 s

    // A* pathfinding state
    private List<BlockPos> currentPath = new ArrayList<>();
    private int pathIndex = 0;
    private static final int[] DIAGONAL_X = {1, 1, -1, -1, 1, 0, -1, 0};
    private static final int[] DIAGONAL_Z = {1, -1, 1, -1, 0, 1, 0, -1};

    // Known stations and storage
    private BlockPos knownFurnace = BlockPos.ZERO;
    private BlockPos knownSmoker = BlockPos.ZERO;
    private BlockPos knownBlastFurnace = BlockPos.ZERO;
    private final List<BlockPos> knownChests = new ArrayList<>();
    private BlockPos lastInteractedStation = BlockPos.ZERO;

    // Interior exit plan (door-focused)
    private boolean exitingInterior = false;
    private BlockPos exitDoorCenter = BlockPos.ZERO;
    private BlockPos exitBeyond = BlockPos.ZERO;
    private int exitTimer = 0;
    private int doorInteractCooldown = 0;
    // Bug 1: guard against stuck-detection infinite loop while door rescue is running
    private boolean doorRescueActive = false;
    private BlockPos doorRescueStartPos = BlockPos.ZERO;

    // Structural escape system
    private final BotEscapeHelper escapeHelper = new BotEscapeHelper(this);
    // Stuck detection for escape trigger (sampled at 20-tick intervals)
    private BlockPos lastKnownPos20  = null;
    private BlockPos lastKnownPos100 = null;
    private int escapeStuckTicks = 0;

    // Constructor for programmatic spawning
    public AmbNpcEntity(ServerLevel level, String name) {
        super(level, new GameProfile(UUID.randomUUID(), name));
        this.setGameMode(GameType.SURVIVAL);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
        this.setHealth(20.0F);
        this.setInvisible(false);
        this.setInvulnerable(false);
        // Enable auto step-up (same as vanilla players: 0.6 blocks).
        // FakePlayers may default to 0, which prevents climbing 1-block walls automatically.
        var stepAttr = this.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr != null) stepAttr.setBaseValue(0.6);
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
            this.setSprinting(speed >= 0.28f);
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
        bot.llmGroup = llmType != null ? llmType : "grok";
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

        if (spawnIdleTimer > 0) {
            spawnIdleTimer--;
            return; // stand still for 5 seconds to get bearings
        }

        // ENHANCED: BotTicker for physics and human-like movement
        BotTicker.tick(this, currentGoal, movementState);

        // Prioritize exiting interiors each tick (door-based plan first, then structural escape)
        boolean exitingNow = handleInteriorExitPlan();
        if (!exitingNow) {
            exitingNow = escapeHelper.tick(tickCount, currentGoal);
        }

        // DEBUG: Log exitingNow state every 2 seconds
        if (tickCount % 40 == 0) {
            System.out.println("[AMB-DEBUG] " + getName().getString() + " AT START OF TICK: exitingNow=" + exitingNow + ", currentGoal=" + currentGoal + ", currentTask=" + currentTask);
        }

        // CRITICAL SURVIVAL - Eat if hungry
        if (RealisticActions.shouldEat(this)) {
            RealisticActions.eatFood(this);
        }

        // CRITICAL SURVIVAL - Flee if critically low health
        if (RealisticActions.isCriticalHealth(this)) {
            if (tickCount % 40 == 0) {
                System.out.println("[AMB-CRITICAL] " + getName().getString() + " has critical health: " + getHealth() + "/" + getMaxHealth() + " - clearing goal and recovering!");
            }
            stopMovement();
            currentGoal = BlockPos.ZERO;
            return; // Don't do anything else, just recover
        }

        // PILLAR CLIMBING SYSTEM — overrides normal navigation when active
        if (pillarPhase != PillarPhase.IDLE) {
            tickPillarSystem();
            // Mining continuation while pillaring (handled separately from normal goal mining)
            if (pillarPhase == PillarPhase.MINING && miningState.isMining && !exitingNow) {
                boolean blockBroken = RealisticActions.continueMining(this, miningState);
                if (blockBroken) miningState.isMining = false; // pillar tick will find next block
            }
            return;
        }

        // REALISTIC MOVEMENT SYSTEM + A* WAYPOINTS
        if (!currentGoal.equals(BlockPos.ZERO)) {
            // DEBUG: Log when entering movement system
            if (tickCount % 40 == 0) {
                System.out.println("[AMB-DEBUG] " + getName().getString() + " in movement system, goal=" + currentGoal + ", pathSize=" + currentPath.size() + ", pathIndex=" + pathIndex);
            }
        } else {
            // DEBUG: Log why we're not moving
            if (tickCount % 40 == 0) {
                System.out.println("[AMB-DEBUG] " + getName().getString() + " NOT in movement system - currentGoal is ZERO!");
            }
        }

        if (!currentGoal.equals(BlockPos.ZERO)) {

            // Throttle A* retries on failure to avoid re-running 2500 nodes every tick
            if (pathRetryTimer > 0) pathRetryTimer--;

            // Recompute path when: empty and retry timer expired, path exhausted, or stuck
            boolean needNewPath = (currentPath.isEmpty() && pathRetryTimer == 0)
                    || (!currentPath.isEmpty() && pathIndex >= currentPath.size())
                    || stuckTimer > 20;
            // Reset fail counter AND retry timer when goal changes externally
            // (interior exit redirects to door, task switch, etc.)
            if (!currentGoal.equals(lastAStarGoal)) {
                aStarFailCount = 0;
                pathRetryTimer = 0; // Compute A* immediately for new goal
                lastAStarGoal = currentGoal;
            }

            if (needNewPath) {
                currentPath = computeAStarPath(blockPosition(), currentGoal);
                pathIndex = 0;
                if (currentPath.isEmpty()) {
                    // On failure, wait 3 seconds before retrying
                    pathRetryTimer = 60;
                    aStarFailCount++;
                    System.out.println("[AMB-DEBUG] " + getName().getString() + " A* failed (attempt " + aStarFailCount + ") for goal " + currentGoal);
                    // On 2nd failure: immediately try door rescue (wall between bot and goal)
                    if (aStarFailCount == 2 && !doorRescueActive && doorPhase == 0 && doorIgnoreTicks == 0) {
                        if (attemptDoorRescue()) {
                            System.out.println("[AMB-NAV] " + getName().getString() + " A* blocked — door rescue initiated toward " + doorPos);
                        }
                    }
                    if (aStarFailCount >= 5) {
                        // Goal is genuinely unreachable — abandon it and pick a new one
                        System.out.println("[AMB-NAV] " + getName().getString() + " abandoning unreachable goal " + currentGoal + " after " + aStarFailCount + " A* failures");
                        currentGoal = BlockPos.ZERO;
                        currentPath.clear();
                        aStarFailCount = 0;
                        executeCurrentTask();
                        return;
                    }
                } else {
                    pathRetryTimer = 0;
                    aStarFailCount = 0;
                    System.out.println("[AMB-DEBUG] " + getName().getString() + " computed A* path with " + currentPath.size() + " waypoints");
                }
            }

            // Check if pillar mode should activate: bot is XZ-close to goal but goal is too high to walk to.
            // Trigger before choosing a waypoint so we never enter the jump-loop.
            if (shouldMineBlock(level().getBlockState(currentGoal))) {
                int heightAbove = currentGoal.getY() - blockPosition().getY();
                double hDistXZ = Math.sqrt(Math.pow(currentGoal.getX() + 0.5 - getX(), 2)
                                         + Math.pow(currentGoal.getZ() + 0.5 - getZ(), 2));
                if (heightAbove > 1 && hDistXZ < 2.5) {
                    enterPillarMode(currentGoal);
                    return; // pillar system takes over next tick
                }
            }

            // Choose waypoint (center of target if no path)
            BlockPos waypoint = (!currentPath.isEmpty() && pathIndex < currentPath.size()) ? currentPath.get(pathIndex) : currentGoal;

            // Determine sprint before movement so the correct speed is passed to moveTowards
            boolean shouldSprint = BotTicker.shouldSprint(this, currentGoal);
            float baseSpeed = (this.desiredSpeed > 0f) ? this.desiredSpeed : RealisticMovement.calculateSpeed(this, false);
            float speed = shouldSprint ? 0.28f : baseSpeed;
            this.setSprinting(shouldSprint);
            boolean stillMoving;

            // If in door handling, bias toward door first
            if (doorPhase > 0 && !doorPos.equals(BlockPos.ZERO)) {
                // Phase 2 uses alignment-aware movement inside handleDoorPlan — skip moveTowards
                if (doorPhase == 2) {
                    stillMoving = true;
                } else {
                    stillMoving = RealisticMovement.moveTowards(this, doorPos, speed);
                }
                handleDoorPlan();
                if (tickCount % 40 == 0) {
                    System.out.println("[AMB-DEBUG] " + getName().getString() + " moving to door at " + doorPos + ", stillMoving=" + stillMoving);
                }
                // Don't clear door phase here - let handleDoorPlan() manage the full door passage
            } else if (avoidTicks > 0 && moveTarget != null) {
                // Perform strafe to get around obstacle
                avoidTicks--;
                RealisticMovement.strafeAround(this, moveTarget, avoidDir, speed * 0.85f);
                stillMoving = true;
                if (tickCount % 40 == 0) {
                    System.out.println("[AMB-DEBUG] " + getName().getString() + " strafing around obstacle");
                }
            } else {
                stillMoving = RealisticMovement.moveTowards(this, waypoint, speed);
                if (tickCount % 40 == 0) {
                    System.out.println("[AMB-DEBUG] " + getName().getString() + " moving to waypoint " + waypoint + ", stillMoving=" + stillMoving + ", speed=" + speed);
                }
            }

            // ENHANCED: Use BotTicker for smooth look direction
            BotTicker.updateLookDirection(this, currentGoal, stillMoving);

            // ── Block 2: jump cooldown + horizontal-progress tracking ────────────────
            if (jumpCooldown > 0) jumpCooldown--;

            Vec3 nowPos = position();
            if (!lastExactMovingPos.equals(Vec3.ZERO)) {
                double dx2 = nowPos.x - lastExactMovingPos.x;
                double dz2 = nowPos.z - lastExactMovingPos.z;
                double horizProgress = Math.sqrt(dx2 * dx2 + dz2 * dz2);
                if (!onGround()) {
                    // Don't count no-progress while airborne — horizontal movement is
                    // constrained by physics mid-jump, not by a wall. Accumulating here
                    // would trigger a spurious jump the instant the bot lands.
                    noProgressTicks = 0;
                } else if (horizProgress < 0.05) {
                    noProgressTicks++;
                } else {
                    noProgressTicks = 0;
                }
            }
            lastExactMovingPos = nowPos;

            // ── Block 4: airborne / falling detection ─────────────────────────────────
            if (!onGround() && getDeltaMovement().y < -0.1) {
                airborneTicks++;
                if (airborneTicks > AIRBORNE_RECOVERY_THRESHOLD) {
                    System.out.println("[AMB-NAV] " + getName().getString()
                        + " falling for " + airborneTicks + " ticks — clearing path to recompute on landing");
                    currentPath.clear();
                    pathIndex = 0;
                    airborneTicks = 0;
                    noProgressTicks = 0;
                }
            } else {
                airborneTicks = 0;
            }

            // ── Block 2: jump trigger ─────────────────────────────────────────────────
            // Track consecutive ticks with horizontal collision (pressed into a wall on ground).
            if (horizontalCollision && onGround()) {
                hCollTicks++;
            } else {
                hCollTicks = 0;
            }

            // Pre-calculate waypoint height difference for jump decisions.
            // wpDY > 1 means the obstacle is too tall to clear with a single jump.
            int wpDY = (!currentPath.isEmpty() && pathIndex < currentPath.size())
                ? currentPath.get(pathIndex).getY() - blockPosition().getY() : 0;

            boolean shouldJump = false;

            // Condition B: pressed against a wall for 2+ ticks (horizontalCollision on ground)
            // Only act when we have an actual path — with an empty path, wpDY=0 is a
            // meaningless default (bot is just pressing against a wall waiting for A*).
            if (hCollTicks >= 2 && !currentPath.isEmpty()) {
                if (wpDY <= 1) {
                    // Obstacle is at most 1 block high — a jump will clear it
                    shouldJump = true;
                } else {
                    // Too tall to jump: break the blocking blocks to create a path
                    tryBreakPathBlock(wpDY);
                }
                hCollTicks = 0;
            } else if (hCollTicks >= 2) {
                // Path is empty (A* failed); don't jump — just reset the counter
                hCollTicks = 0;
            }
            // Condition C: no-progress fallback (wall without collision flag, e.g. fence post)
            // Only jump if the waypoint is reachable by a single jump (≤1 block above).
            if (noProgressTicks >= 5 && !currentPath.isEmpty()) {
                if (wpDY >= 0 && wpDY <= 1) {
                    shouldJump = true;
                } else if (wpDY > 1) {
                    // Waypoint is multiple blocks above — break path blocks to climb
                    tryBreakPathBlock(wpDY);
                }
                noProgressTicks = 0;
            } else if (noProgressTicks >= 5) {
                noProgressTicks = 0; // Reset even with empty path to prevent stale counter
            }

            if (shouldJump && jumpCooldown == 0) {
                // Gather context for log
                double horizDist = 0;
                if (!currentPath.isEmpty() && pathIndex < currentPath.size()) {
                    BlockPos wp = currentPath.get(pathIndex);
                    horizDist = Math.sqrt(Math.pow(getX() - (wp.getX() + 0.5), 2)
                                        + Math.pow(getZ() - (wp.getZ() + 0.5), 2));
                }
                jumpFromGround();
                jumpCooldown = 15;
                System.out.printf("[AMB-JUMP] %s jump: hColl=%s waypointDY=%d horizDist=%.2f%n",
                    getName().getString(), horizontalCollision, wpDY, horizDist);
            }
            // ─────────────────────────────────────────────────────────────────────────

            // Debug logging every 2 seconds
            if (tickCount % 40 == 0) {
                System.out.printf("[AMB] %s moving to goal %s (pos=(%.3f,%.3f,%.3f) blockPos=%s dist=%.2f pathIdx=%d/%d wp=%s)%n",
                    getName().getString(), currentGoal,
                    getX(), getY(), getZ(), blockPosition(),
                    Math.sqrt(blockPosition().distSqr(currentGoal)),
                    pathIndex, currentPath.size(), waypoint);
            }
            // Diagnostic every second: exact pos, deltaMovement, collision flags
            if (tickCount % 20 == 0) {
                Vec3 dm = getDeltaMovement();
                System.out.printf("[AMB-DIAG] %s pos=(%.3f,%.3f,%.3f) delta=(%.4f,%.4f,%.4f) hColl=%b onGnd=%b sprint=%b%n",
                    getName().getString(),
                    getX(), getY(), getZ(),
                    dm.x, dm.y, dm.z,
                    horizontalCollision, onGround(), isSprinting());
            }

            // Bug 1: clear doorRescueActive once bot has genuinely moved away
            if (doorRescueActive && blockPosition().distSqr(doorRescueStartPos) > 4.0) {
                doorRescueActive = false;
            }

            // ENHANCED: Multi-level stuck detection with progressive recovery
            // Bug 1: skip entirely while door rescue is already in progress
            if (!doorRescueActive && StuckDetection.isStuck(this, stuckState, currentGoal)) {
                System.out.println("[AMB-STUCK] " + getName().getString() + " stuck at " + blockPosition() +
                    " (level " + stuckState.recoveryLevel + ", ticks: " + stuckState.stuckTicks + ")");

                // Try door rescue first (legacy compatibility)
                if (stuckState.recoveryLevel == 0 && attemptDoorRescue()) {
                    System.out.println("[AMB-STUCK] Door rescue initiated");
                    stuckState.reset();
                }
                // Check if stuck in non-solid block
                else if (stuckState.recoveryLevel == 0 && isStuckInNonSolidBlock()) {
                    System.out.println("[AMB-STUCK] Stuck in non-solid block, forcing downward");
                    this.setDeltaMovement(0, -0.5, 0);
                    stuckState.reset();
                }
                // Execute progressive recovery strategies
                else if (StuckDetection.executeRecovery(this, stuckState, currentGoal)) {
                    System.out.println("[AMB-STUCK] Recovery action executed");
                    // If level 2 recovery (recompute path), clear current path
                    if (stuckState.recoveryLevel == 2) {
                        currentPath.clear();
                        pathIndex = 0;
                    }
                }
                // If severely stuck (level 3), abandon goal
                else if (stuckState.recoveryLevel >= 3 && !exitingNow) {
                    System.out.println("[AMB-STUCK] Abandoning unreachable goal after level 3 recovery failure");
                    currentGoal = BlockPos.ZERO;
                    stuckState.reset();
                    executeCurrentTask();
                }
            } else {
                // Not stuck - reset state and track movement
                if (stuckState.stuckTicks > 0) {
                    stuckState.reset();
                }
                lastPosition = blockPosition();
                lastExactPos = this.position();
                ticksMoving++;
            }

            // Proactively force-open doors along the movement direction (up to 3 blocks ahead).
            // This handles the case where a door is in the path but horizontalCollision hasn't
            // fired yet (e.g. the bot is 1-2 blocks away and would otherwise walk into a closed door).
            if (level() instanceof ServerLevel sl && doorInteractCooldown == 0) {
                double toWpX = (waypoint.getX() + 0.5) - getX();
                double toWpZ = (waypoint.getZ() + 0.5) - getZ();
                double toWpLen = Math.sqrt(toWpX * toWpX + toWpZ * toWpZ);
                if (toWpLen > 0.01) {
                    double normX = toWpX / toWpLen;
                    double normZ = toWpZ / toWpLen;
                    for (int steps = 1; steps <= 3; steps++) {
                        BlockPos checkPos = BlockPos.containing(getX() + normX * steps, getY(), getZ() + normZ * steps);
                        BlockState checkState = sl.getBlockState(checkPos);
                        if (checkState.getBlock() instanceof DoorBlock || checkState.getBlock() instanceof FenceGateBlock) {
                            if (!checkState.getOptionalValue(BlockStateProperties.OPEN).orElse(false)) {
                                forceDoorOpen(sl, checkPos);
                                forceDoorOpen(sl, checkPos.above());
                                doorInteractCooldown = 20;
                                System.out.println("[AMB-DOOR] " + getName().getString() + " proactively opened door at " + checkPos);
                            }
                            break; // door found — stop scanning further regardless of open state
                        } else if (checkState.canOcclude()) {
                            break; // solid non-door block — no door to open this direction
                        }
                    }
                }
            }

            // IMPROVED: Check for doors in multiple directions when colliding
            if (this.horizontalCollision && doorInteractCooldown == 0) {
                // Check all horizontal directions for doors
                Direction[] directions = {getDirection(), getDirection().getClockWise(), getDirection().getCounterClockWise()};
                for (Direction dir : directions) {
                    BlockPos checkPos = blockPosition().relative(dir);
                    BlockState checkState = level().getBlockState(checkPos);
                    if (checkState.getBlock() instanceof DoorBlock || checkState.getBlock() instanceof FenceGateBlock) {
                        Boolean isOpen = checkState.getOptionalValue(BlockStateProperties.OPEN).orElse(false);
                        if (!isOpen) {
                            RealisticActions.interactWithBlock(this, checkPos);
                            doorInteractCooldown = 30; // 1.5s cooldown
                            System.out.println("[AMB] " + getName().getString() + " opening door at " + checkPos + " due to collision");
                            break; // Only open one door at a time
                        }
                    }
                }
            }

            // Advance waypoint: 2D horizontal distance AND vertical within 2 blocks.
            // The Y-guard prevents skipping elevated waypoints the bot hasn't physically climbed to.
            double wpDx = this.getX() - (waypoint.getX() + 0.5);
            double wpDz = this.getZ() - (waypoint.getZ() + 0.5);
            double wpDy = this.getY() - waypoint.getY();
            if (wpDx * wpDx + wpDz * wpDz < 1.5 * 1.5 && Math.abs(wpDy) < 2.0
                    && !currentPath.isEmpty() && pathIndex < currentPath.size()) {
                pathIndex++;
                waypointStuckTicks = 0;
            }

            // Waypoint skip: if the bot has been close to (but unable to reach) the current waypoint
            // for WAYPOINT_STUCK_THRESHOLD ticks, skip it and continue with the next one.
            // CRITICAL: Do NOT skip if the waypoint is significantly above the bot — the bot needs
            // to climb up, not skip past ascending waypoints while stuck at the bottom of a pit.
            double wpHorizDistSq = wpDx * wpDx + wpDz * wpDz;
            double wpVertDiff = waypoint.getY() - this.getY(); // positive = waypoint is above bot
            boolean waypointReachableVertically = wpVertDiff <= 1.5; // only skip if same/lower level
            if (wpHorizDistSq < 2.5 * 2.5 && waypointReachableVertically
                    && !currentPath.isEmpty() && pathIndex < currentPath.size()) {
                waypointStuckTicks++;
                if (waypointStuckTicks >= WAYPOINT_STUCK_THRESHOLD) {
                    System.out.println("[AMB-SKIP] " + getName().getString()
                        + " waypoint " + waypoint + " skipped after " + waypointStuckTicks
                        + " ticks, new index=" + (pathIndex + 1));
                    pathIndex++;
                    waypointStuckTicks = 0;
                }
            } else {
                waypointStuckTicks = 0;
            }

            // Reached goal - only trigger when PHYSICALLY close (≤2.5 blocks).
            // Never trigger on an empty path alone — that just means A* hasn't found a
            // route yet, not that the bot has arrived.
            double distToGoal = position().distanceTo(Vec3.atCenterOf(currentGoal));
            boolean pathExhausted = !currentPath.isEmpty() && pathIndex >= currentPath.size();
            if (distToGoal < 2.5 && (pathExhausted || distToGoal < 1.5)) {
                // Check if we should mine the block at goal
                BlockState targetState = level().getBlockState(currentGoal);
                if (shouldMineBlock(targetState)) {
                    // Start mining
                    if (!miningState.isMining) {
                        RealisticActions.equipBestTool(this, targetState);
                        RealisticActions.startMining(this, currentGoal, miningState);
                        if (tickCount % 40 == 0) {
                            System.out.println("[AMB-DEBUG] " + getName().getString() + " reached goal, starting to mine at " + currentGoal);
                        }
                    }
                } else {
                    // CRITICAL FIX: Goal reached but nothing to mine - check nearby for mineable blocks
                    BlockPos nearbyMineable = findMineableNearby(currentGoal, 4, currentTask);
                    if (nearbyMineable != null) {
                        // Found a mineable block nearby, update goal
                        currentGoal = nearbyMineable;
                        currentPath.clear();
                        pathIndex = 0;
                        System.out.println("[AMB-TASK] " + getName().getString() + " reached position but found mineable block nearby at " + nearbyMineable);
                    } else {
                        // No mineable blocks nearby - clear goal and find next target
                        if (tickCount % 40 == 0) {
                            System.out.println("[AMB-TASK] " + getName().getString() + " reached position " + currentGoal + " but nothing to mine nearby, finding next goal");
                        }
                        currentGoal = BlockPos.ZERO;
                        executeCurrentTask();
                    }
                }
            }
        } else {
            // No goal — execute current task to find one (but NOT if exiting interior).
            // Throttle expensive world-searches to every 40 ticks; the bot starts
            // moving the very next tick once a goal is assigned.
            if (!exitingNow && tickCount % 40 == 0) {
                System.out.println("[AMB-DEBUG] " + getName().getString() + " executeCurrentTask() called from no-goal branch");
                executeCurrentTask();
                System.out.println("[AMB-DEBUG] " + getName().getString() + " AFTER executeCurrentTask(): currentGoal=" + currentGoal);
            }
        }

        // REALISTIC MINING - Continue mining if in progress (skip if exiting interior)
        if (miningState.isMining && !exitingNow) {
            boolean blockBroken = RealisticActions.continueMining(this, miningState);
            if (blockBroken) {
                if (pillarPhase == PillarPhase.MINING) {
                    // Pillar system will detect block gone next tick and find the next one
                    miningState.isMining = false;
                } else {
                    // Normal mining complete - find next goal
                    currentGoal = BlockPos.ZERO;
                    executeCurrentTask();
                }
            }
        }

        // REALISTIC TOOL SWITCHING - Equip appropriate tool for current task
        toolEquipTimer++;
        if (toolEquipTimer > 20) { // Every ~1 second
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

        // REAL PICKUP (auto-collect nearby item drops like a player)
        if (tickCount % 5 == 0) {
            for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(2.5))) {
                if (!item.isRemoved() && !item.getItem().isEmpty()) {
                    ItemStack stack = item.getItem();
                    Item dropped = stack.getItem();
                    int before = countItemInInventory(dropped);
                    int itemId = item.getId();

                    // Manually transfer items into bot inventory (avoid default playerTouch animation)
                    ItemStack toAdd = stack.copy();
                    addToInventory(toAdd);
                    int after = countItemInInventory(dropped);
                    int grabbed = Math.max(0, after - before);
                    if (grabbed > 0) {
                        // Reduce or discard ground stack accordingly
                        int remaining = stack.getCount() - grabbed;
                        if (remaining <= 0) {
                            item.discard();
                        } else {
                            stack.shrink(grabbed);
                            item.setItem(stack);
                        }

                        broadcastGroupChat("Picked up " + grabbed + " " + dropped.getDescriptionId());

                        // Unlock recipes associated with newly obtained item, like a real player
                        unlockRecipesForItem(dropped);

                        // Send pickup animation towards the visual entity
                        int collectorId = (visualEntity != null) ? visualEntity.getId() : this.getId();
                        if (!level().isClientSide()) {
                            ClientboundTakeItemEntityPacket pkt = new ClientboundTakeItemEntityPacket(itemId, collectorId, grabbed);
                            for (ServerPlayer sp : ((ServerLevel) level()).players()) {
                                sp.connection.send(pkt);
                            }
                        }
                    }
                }
            }
        }

        // Lightweight auto-crafting for basics: planks and sticks
        if (tickCount % 100 == 0) {
            tryAutoCraftBasics();
            // Only run station management if not currently exiting and has no active goal
            // (handleInteriorExitPlan already runs every tick at the top of the loop)
            if (!exitingNow && !escapeHelper.isActive()) {
                manageStationsAndCrafting();
            }
        }

        // Cooldowns
        if (doorInteractCooldown > 0) doorInteractCooldown--;
    }

    // ==================== INTERIOR EXIT (DOOR) PLAN ====================
    private boolean handleInteriorExitPlan() {
        // Decrement exit cooldown
        if (exitCooldown > 0) {
            exitCooldown--;
            return false; // Don't check interior while on cooldown
        }

        // Only check for interior when we have an active goal to reach
        if (currentTask == null || currentGoal.equals(BlockPos.ZERO)) {
            exitingInterior = false;
            exitDoorCenter = BlockPos.ZERO;
            exitBeyond = BlockPos.ZERO;
            return false;
        }

        // Detect if indoors (no sky above within a few blocks)
        boolean indoors = true;
        for (int i = 0; i < 5; i++) {
            if (level().canSeeSky(blockPosition().above(i))) { indoors = false; break; }
        }

        // FIXED: If we're outside, clear exit state and don't re-trigger
        if (!indoors) {
            if (exitingInterior) {
                System.out.println("[AMB] " + getName().getString() + " successfully exited interior!");
                exitCooldown = 200; // 10 second cooldown to prevent immediate re-entry detection
            }
            exitingInterior = false;
            exitDoorCenter = BlockPos.ZERO;
            exitBeyond = BlockPos.ZERO;
            return false;
        }

        // Trigger interior exit when indoors AND A* has been failing (goal unreachable from here).
        // We do NOT check whether the goal is "outside" via canSeeSky because tree goals in
        // dense forests have leaf canopy blocking sky even 12+ blocks above the log block.
        // If A* can find an indoor path (e.g. to a crafting table), it succeeds and this block
        // is never reached with aStarFailCount > 0, so indoor goals are naturally excluded.
        if (!exitingInterior && !currentGoal.equals(BlockPos.ZERO) && aStarFailCount >= 2) {

            BlockPos door = findNearestDoor(12);
            if (door != null) {
                Direction facing = level().getBlockState(door).getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
                exitDoorCenter = door;
                exitBeyond = door.relative(facing, 8);
                exitingInterior = true;
                exitTimer = 300; // 15 seconds budget
                System.out.println("[AMB] " + getName().getString() + " INTERIOR DETECTED! Goal is outside. Found door at " + door + ", planning exit to " + exitBeyond);
            } else {
                // No door block found — find nearest outdoor position (open sky) and walk there
                BlockPos outdoorExit = findNearestOutdoorPos(20);
                if (outdoorExit != null) {
                    exitDoorCenter = outdoorExit;
                    exitBeyond = outdoorExit;
                    exitingInterior = true;
                    exitTimer = 600; // 30 second budget (no door to guide us)
                    System.out.println("[AMB] " + getName().getString() + " INTERIOR DETECTED! No door found, navigating toward nearest outdoor pos " + outdoorExit);
                } else {
                    // Truly enclosed with no exit — just let A* keep trying (or abandon via fail counter)
                    System.out.println("[AMB] " + getName().getString() + " INTERIOR DETECTED but no exit found within 20 blocks");
                }
            }
        }

        if (exitingInterior) {
            // IMPROVED: Better target selection - move to door, then beyond
            BlockPos target;
            if (this.blockPosition().closerThan(exitDoorCenter, 2.5)) {
                target = exitBeyond; // We're at the door, move beyond
            } else {
                target = exitDoorCenter; // Move to door first
            }

            if (!target.equals(this.currentGoal)) {
                this.currentGoal = target;
                this.currentPath.clear();
                System.out.println("[AMB] " + getName().getString() + " exit plan: moving to " + target);
            }

            // If near door, attempt to open it
            if (this.blockPosition().closerThan(exitDoorCenter, 3.0) && doorInteractCooldown == 0) {
                BlockState doorState = level().getBlockState(exitDoorCenter);
                Boolean isOpen = doorState.getOptionalValue(BlockStateProperties.OPEN).orElse(false);
                if (!isOpen) {
                    RealisticActions.interactWithBlock(this, exitDoorCenter);
                    doorInteractCooldown = 40; // 2s cooldown to prevent spam
                    System.out.println("[AMB] " + getName().getString() + " opening door at " + exitDoorCenter);
                }
            }

            // IMPROVED: Check if we've successfully exited (are we outside now?)
            boolean nowOutside = false;
            for (int i = 0; i < 5; i++) {
                if (level().canSeeSky(blockPosition().above(i))) {
                    nowOutside = true;
                    break;
                }
            }

            if (nowOutside && this.blockPosition().closerThan(exitBeyond, 5.0)) {
                System.out.println("[AMB] " + getName().getString() + " successfully exited interior!");
                exitingInterior = false;
                exitDoorCenter = BlockPos.ZERO;
                exitBeyond = BlockPos.ZERO;
                exitCooldown = 200; // 10 second cooldown to prevent immediate re-entry detection
                return false;
            }

            if (exitTimer-- <= 0) {
                System.out.println("[AMB] " + getName().getString() + " exit plan timeout, aborting");
                exitingInterior = false;
                exitDoorCenter = BlockPos.ZERO;
                exitBeyond = BlockPos.ZERO;
                doorRescueActive = false; // Bug 1: release rescue guard on timeout
            }
            return true; // suppress other tasks while exiting
        }
        return false;
    }

    /** Bug 2: directly set a door block to open state, skipping interaction animations. */
    private void forceDoorOpen(ServerLevel sl, BlockPos pos) {
        BlockState state = sl.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock
                && state.hasProperty(BlockStateProperties.OPEN)
                && !state.getValue(BlockStateProperties.OPEN)) {
            sl.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
        }
    }

    private BlockPos findNearestDoor(int radius) {
        BlockPos best = null; double bestD2 = Double.MAX_VALUE;
        BlockPos c = blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState st = level().getBlockState(p);
                    if (st.getBlock() instanceof DoorBlock || st.getBlock() instanceof FenceGateBlock) {
                        double d2 = p.distSqr(c);
                        if (d2 < bestD2) { bestD2 = d2; best = p; }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Find the nearest walkable position that has open sky above it.
     * Used when no door block exists but the bot needs to reach the outside.
     */
    private BlockPos findNearestOutdoorPos(int radius) {
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        BlockPos c = blockPosition();
        if (!(level() instanceof ServerLevel sl)) return null;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                int y = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos p = new BlockPos(x, y, z);
                // Must be walkable (solid ground, 2-block clearance) and have open sky
                if (RealisticMovement.isWalkable(sl, p) && sl.canSeeSky(p)) {
                    double d2 = (double)(dx * dx + dz * dz);
                    if (d2 < bestD2) { bestD2 = d2; best = p; }
                }
            }
        }
        return best;
    }

    // ==================== A* PATHFINDING ====================
    private static class Node implements Comparable<Node> {
        BlockPos pos; int g; int f;
        Node(BlockPos p, int g, int f){ this.pos=p; this.g=g; this.f=f; }
        @Override public int compareTo(Node o){ return Integer.compare(this.f, o.f); }
    }

    private List<BlockPos> computeAStarPath(BlockPos start, BlockPos goal) {
        // ENHANCED: Find walkable goal if target is not walkable
        BlockPos walkableGoal = getWalkableGoal(goal);

        if (start.equals(walkableGoal)) return new ArrayList<>();

        PriorityQueue<Node> open = new PriorityQueue<>();
        Set<BlockPos> closed = new HashSet<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Integer> gScore = new HashMap<>();

        open.add(new Node(start, 0, heuristic(start, walkableGoal)));
        gScore.put(start, 0);

        int maxNodes = 8000; // Increased for paths through structures and complex terrain
        int expanded = 0;

        while (!open.isEmpty() && expanded < maxNodes) {
            Node current = open.poll();
            expanded++;
            if (current.pos.equals(walkableGoal)) {
                List<BlockPos> path = reconstructPath(cameFrom, current.pos);
                System.out.println("[AMB-PATH] A* " + start + "→" + walkableGoal + ": " + path.size() + " nodes, walkable:" +
                    (!walkableGoal.equals(goal)) + ", dy:" + (walkableGoal.getY() - goal.getY()));
                if (!path.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    int limit = Math.min(5, path.size());
                    for (int pi = 0; pi < limit; pi++) sb.append(path.get(pi)).append(' ');
                    System.out.println("[AMB-PATH-DETAIL] " + getName().getString()
                        + " first " + limit + " waypoints: " + sb);
                }
                return path;
            }
            closed.add(current.pos);

            for (BlockPos neighbor : getNeighbors(current.pos)) {
                if (closed.contains(neighbor)) continue;
                int tentativeG = gScore.getOrDefault(current.pos, Integer.MAX_VALUE) + cost(current.pos, neighbor);
                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, current.pos);
                    gScore.put(neighbor, tentativeG);
                    int f = tentativeG + heuristic(neighbor, walkableGoal);
                    open.add(new Node(neighbor, tentativeG, f));
                }
            }
        }

        // Path not found
        double distance = Math.sqrt(start.distSqr(walkableGoal));
        if (distance > 32) {
            System.out.println("[AMB-PATH] A* failed: distance " + distance + " > 32, may need LLM replan");
        } else {
            System.out.println("[AMB-PATH] A* failed: no path found to " + walkableGoal);
        }

        return new ArrayList<>();
    }

    /**
     * Find a walkable goal position near the target
     * Searches downward up to 3 blocks to find solid ground
     */
    private BlockPos getWalkableGoal(BlockPos goal) {
        if (!(level() instanceof ServerLevel sl)) return goal;

        // Check if goal is already walkable
        if (RealisticMovement.isWalkable(sl, goal)) return goal;

        // Search vertically (down 4, up 2)
        for (int dy = -1; dy >= -4; dy--) {
            BlockPos candidate = goal.offset(0, dy, 0);
            if (RealisticMovement.isWalkable(sl, candidate)) return candidate;
        }
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos candidate = goal.offset(0, dy, 0);
            if (RealisticMovement.isWalkable(sl, candidate)) return candidate;
        }

        // Search horizontally adjacent (N/S/E/W) at same Y and Y-1
        // Critical for tree trunks and solid blocks where bot must stand beside the block
        int[] dx = {1, -1, 0,  0};
        int[] dz = {0,  0, 1, -1};
        for (int i = 0; i < 4; i++) {
            BlockPos candidate = goal.offset(dx[i], 0, dz[i]);
            if (RealisticMovement.isWalkable(sl, candidate)) return candidate;
            candidate = goal.offset(dx[i], -1, dz[i]);
            if (RealisticMovement.isWalkable(sl, candidate)) return candidate;
        }

        // Wider horizontal search (radius 2)
        for (int ddx = -2; ddx <= 2; ddx++) {
            for (int ddz = -2; ddz <= 2; ddz++) {
                if (ddx == 0 && ddz == 0) continue;
                BlockPos candidate = goal.offset(ddx, 0, ddz);
                if (RealisticMovement.isWalkable(sl, candidate)) return candidate;
                candidate = goal.offset(ddx, -1, ddz);
                if (RealisticMovement.isWalkable(sl, candidate)) return candidate;
            }
        }

        return goal;
    }

    private int heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) + Math.abs(a.getY() - b.getY()) * 2;
    }

    private int cost(BlockPos from, BlockPos to) {
        BlockState state = level().getBlockState(to);
        int baseCost = 1; // Base cost for movement

        // ENHANCED: Diagonal movement costs more
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        if (dx > 0 && dz > 0) {
            baseCost = 2; // Diagonal movement (sqrt(2) ≈ 1.4, rounded to 2)
        }

        // ENHANCED: Add vertical movement cost
        double verticalCost = VerticalNavigation.getVerticalMovementCost(from, to, (ServerLevel) level());
        baseCost += (int) verticalCost;

        // ENHANCED: Door costs (openable doors are cheaper)
        if (state.getBlock() instanceof DoorBlock) {
            Boolean isOpen = state.getOptionalValue(BlockStateProperties.OPEN).orElse(false);
            if (isOpen) {
                baseCost += 1; // Open door, easy to pass (cost=0.2 in spec, but using int)
            } else {
                baseCost += 2; // Closed door, need to open (cost=1.5 in spec)
            }
        }

        // Liquids are allowed but slower: apply mild penalty
        if (state.is(Blocks.WATER)) baseCost += 3;  // okay to cross
        if (state.is(Blocks.LAVA)) baseCost += 47;  // risky but not forbidden
        // penalize cliffs
        if (level().getBlockState(to).isAir() && level().getBlockState(to.below()).isAir()) baseCost += 12;
        // prefer stairs and slabs slightly
        BlockState below = level().getBlockState(to.below());
        if (below.getBlock() instanceof StairBlock) baseCost -= 1;
        if (below.getBlock() instanceof SlabBlock) baseCost -= 1;
        // path blocks slightly cheaper
        if (below.is(Blocks.DIRT_PATH)) baseCost -= 1;

        return Math.max(1, baseCost);
    }

    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();

        // 8-way neighbors on the same Y level.
        // Extra check: floor below neighbor must be canOcclude() to avoid placing waypoints
        // on the edge of a cliff where the bot would immediately fall off.
        // Diagonal corner-cutting prevention: for diagonal steps, both cardinal intermediates
        // must be passable so the path doesn't clip through wall corners.
        for (int i = 0; i < 8; i++) {
            if (DIAGONAL_X[i] != 0 && DIAGONAL_Z[i] != 0) {
                if (!isPassable(pos.offset(DIAGONAL_X[i], 0, 0)) || !isPassable(pos.offset(0, 0, DIAGONAL_Z[i]))) continue;
            }
            BlockPos n = pos.offset(DIAGONAL_X[i], 0, DIAGONAL_Z[i]);
            if (isPassable(n) && level().getBlockState(n.below()).canOcclude()) {
                out.add(n);
            }
        }

        // Step up one block (+1 Y, adjacent XZ).
        // Extra check: the block AT pos-XZ, pos-Y (the step surface) must be canOcclude()
        // so the bot has a real surface to step UP FROM, not air beside it.
        for (int i = 0; i < 8; i++) {
            if (DIAGONAL_X[i] != 0 && DIAGONAL_Z[i] != 0) {
                if (!isPassable(pos.offset(DIAGONAL_X[i], 0, 0)) || !isPassable(pos.offset(0, 0, DIAGONAL_Z[i]))) continue;
            }
            BlockPos n = pos.offset(DIAGONAL_X[i], 0, DIAGONAL_Z[i]);
            BlockPos up = n.above();
            if (isPassable(up) && canJumpTo(pos, up)
                    && level().getBlockState(n).canOcclude()) { // step surface must be solid
                out.add(up);
            }
        }

        // Step down one block (-1 Y, adjacent XZ).
        for (int i = 0; i < 8; i++) {
            if (DIAGONAL_X[i] != 0 && DIAGONAL_Z[i] != 0) {
                if (!isPassable(pos.offset(DIAGONAL_X[i], 0, 0)) || !isPassable(pos.offset(0, 0, DIAGONAL_Z[i]))) continue;
            }
            BlockPos n = pos.offset(DIAGONAL_X[i], 0, DIAGONAL_Z[i]);
            BlockPos down = n.below();
            if (isPassable(down) && (level() instanceof ServerLevel sl) && RealisticMovement.isWalkable(sl, down)) {
                out.add(down);
            }
        }

        // Straight-down drop (holes / water).
        BlockPos down = pos.below();
        if (isPassable(down) || level().getBlockState(down).is(Blocks.WATER)) {
            out.add(down);
        }
        // Note: straight-up jump (+1 / +2) is handled exclusively by
        // VerticalNavigation.addVerticalNeighbors() below, so no duplicate here.

        // ENHANCED: Add vertical neighbors for climbing/jumping
        if (level() instanceof ServerLevel sl) {
            VerticalNavigation.addVerticalNeighbors(pos, sl, out);
        }

        return out;
    }

    /**
     * Check if entity can jump to a position
     */
    private boolean canJumpTo(BlockPos from, BlockPos to) {
        int verticalDiff = to.getY() - from.getY();

        // Can jump up 1 block normally
        if (verticalDiff == 1) {
            return canStandOn(from.below());
        }

        return verticalDiff <= 1;
    }

    /**
     * Check if entity can stand on a block
     */
    private boolean canStandOn(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        return state.canOcclude() && !(state.getBlock() instanceof DoorBlock) &&
               !(state.getBlock() instanceof FenceGateBlock);
    }

    /**
     * Check if entity can jump to a certain height
     */
    private boolean canJumpHeight(int height) {
        // Normal jump is 1 block, 2 blocks requires block placement
        return height <= 1;
    }

    private boolean isPassable(BlockPos p) {
        BlockState feet = level().getBlockState(p);
        BlockState head = level().getBlockState(p.above());
        BlockState below = level().getBlockState(p.below());

        // Fences and walls have canOcclude()=false but block movement — treat as solid.
        if (feet.is(BlockTags.FENCES) || feet.is(BlockTags.WALLS)) return false;
        if (head.is(BlockTags.FENCES) || head.is(BlockTags.WALLS)) return false;

        // Doors and fence gates are passable (bot can open them)
        boolean feetIsDoor = feet.getBlock() instanceof DoorBlock || feet.getBlock() instanceof FenceGateBlock;
        boolean headIsDoor = head.getBlock() instanceof DoorBlock || head.getBlock() instanceof FenceGateBlock;

        // Check if feet and head positions are clear (or are doors)
        boolean feetClear = !feet.canOcclude() || feetIsDoor;
        boolean headClear = !head.canOcclude() || headIsDoor;

        // Must have solid ground or water below (not a door or non-solid block)
        boolean solidGround = (below.canOcclude() || below.is(Blocks.WATER)) &&
                              !(below.getBlock() instanceof DoorBlock) &&
                              !(below.getBlock() instanceof FenceGateBlock);

        return feetClear && headClear && solidGround;
    }

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos current) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos cur = current;
        while (cameFrom.containsKey(cur)) {
            path.add(0, cur);
            cur = cameFrom.get(cur);
        }
        return path;
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

    private int countItemInInventory(Item item) {
        int total = 0;
        for (int i = 0; i < getInventory().getContainerSize(); i++) {
            ItemStack st = getInventory().getItem(i);
            if (!st.isEmpty() && st.is(item)) total += st.getCount();
        }
        return total;
    }

    private int removeItems(Item item, int count) {
        int toRemove = count;
        for (int i = 0; i < getInventory().getContainerSize() && toRemove > 0; i++) {
            ItemStack st = getInventory().getItem(i);
            if (!st.isEmpty() && st.is(item)) {
                int take = Math.min(st.getCount(), toRemove);
                st.shrink(take);
                toRemove -= take;
                if (st.isEmpty()) getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        return count - toRemove;
    }

    private void addToInventory(ItemStack stack) {
        getInventory().add(stack);
    }

    private void tryAutoCraftBasics() {
        // Convert common logs into matching planks
        craftPlanksFromLog(Items.OAK_LOG, Items.OAK_PLANKS);
        craftPlanksFromLog(Items.SPRUCE_LOG, Items.SPRUCE_PLANKS);
        craftPlanksFromLog(Items.BIRCH_LOG, Items.BIRCH_PLANKS);
        craftPlanksFromLog(Items.JUNGLE_LOG, Items.JUNGLE_PLANKS);
        craftPlanksFromLog(Items.ACACIA_LOG, Items.ACACIA_PLANKS);
        craftPlanksFromLog(Items.DARK_OAK_LOG, Items.DARK_OAK_PLANKS);
        craftPlanksFromLog(Items.MANGROVE_LOG, Items.MANGROVE_PLANKS);
        craftPlanksFromLog(Items.CHERRY_LOG, Items.CHERRY_PLANKS);
        craftPlanksFromLog(Items.BAMBOO_BLOCK, Items.BAMBOO_PLANKS);

        // Craft sticks if low and have planks
        int planks = countItemInInventory(Items.OAK_PLANKS) + countItemInInventory(Items.SPRUCE_PLANKS)
                + countItemInInventory(Items.BIRCH_PLANKS) + countItemInInventory(Items.JUNGLE_PLANKS)
                + countItemInInventory(Items.ACACIA_PLANKS) + countItemInInventory(Items.DARK_OAK_PLANKS)
                + countItemInInventory(Items.MANGROVE_PLANKS) + countItemInInventory(Items.CHERRY_PLANKS)
                + countItemInInventory(Items.BAMBOO_PLANKS);
        int sticks = countItemInInventory(Items.STICK);
        if (sticks < 16 && planks >= 2) {
            // Consume any two planks and add 4 sticks
            if (removeAnyPlanks(2) == 2) {
                addToInventory(new ItemStack(Items.STICK, 4));
                broadcastGroupChat("Crafted 4 sticks.");
            }
        }
    }

    private void craftPlanksFromLog(Item log, Item planks) {
        int logs = countItemInInventory(log);
        if (logs > 0) {
            int removed = removeItems(log, 1);
            if (removed == 1) {
                addToInventory(new ItemStack(planks, 4));
                broadcastGroupChat("Crafted 4 planks from a log.");
            }
        }
    }

    private int removeAnyPlanks(int needed) {
        int removed = 0;
        Item[] types = new Item[]{
                Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS,
                Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS,
                Items.BAMBOO_PLANKS
        };
        for (Item t : types) {
            while (removed < needed) {
                int r = removeItems(t, 1);
                if (r == 0) break;
                removed += r;
            }
            if (removed >= needed) break;
        }
        return removed;
    }

    // ==================== RECIPE UNLOCKING (like a real player) ====================
    private void unlockRecipesForItem(Item item) {
        try {
            if (!(level() instanceof ServerLevel sl)) return;
            RecipeManager rm = sl.getServer().getRecipeManager();
            java.util.ArrayList<RecipeHolder<?>> toAward = new java.util.ArrayList<>();

            // Helper to add by id if present
            java.util.function.Consumer<String> add = (id) -> {
                ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(id));
                rm.byKey(key).ifPresent(toAward::add);
            };

            if (item == Items.OAK_LOG || item == Items.SPRUCE_LOG || item == Items.BIRCH_LOG || item == Items.JUNGLE_LOG
                    || item == Items.ACACIA_LOG || item == Items.DARK_OAK_LOG || item == Items.MANGROVE_LOG || item == Items.CHERRY_LOG
                    || item == Items.BAMBOO_BLOCK) {
                // Planks for each wood type
                add.accept("oak_planks");
                add.accept("spruce_planks");
                add.accept("birch_planks");
                add.accept("jungle_planks");
                add.accept("acacia_planks");
                add.accept("dark_oak_planks");
                add.accept("mangrove_planks");
                add.accept("cherry_planks");
                add.accept("bamboo_planks");
                // crafting table and sticks
                add.accept("crafting_table");
                add.accept("sticks");
                // wooden tools
                add.accept("wooden_pickaxe");
                add.accept("wooden_axe");
                add.accept("wooden_sword");
            }

            if (item == Items.OAK_PLANKS || item == Items.SPRUCE_PLANKS || item == Items.BIRCH_PLANKS || item == Items.JUNGLE_PLANKS
                    || item == Items.ACACIA_PLANKS || item == Items.DARK_OAK_PLANKS || item == Items.MANGROVE_PLANKS
                    || item == Items.CHERRY_PLANKS || item == Items.BAMBOO_PLANKS) {
                add.accept("sticks");
                add.accept("crafting_table");
                add.accept("wooden_pickaxe");
                add.accept("wooden_axe");
                add.accept("wooden_sword");
            }

            if (item == Items.COBBLESTONE) {
                add.accept("furnace");
            }

            if (item == Items.RAW_IRON || item == Items.IRON_ORE || item == Items.RAW_GOLD || item == Items.GOLD_ORE
                    || item == Items.RAW_COPPER || item == Items.COPPER_ORE) {
                add.accept("furnace");
            }

            if (!toAward.isEmpty()) this.awardRecipes(toAward);
        } catch (Exception ignored) {}
    }

    // ==================== STATION MANAGEMENT ====================
    private void manageStationsAndCrafting() {
        // CRITICAL: Never overwrite an active navigation goal with station goals.
        // Station management only redirects the bot when it has no current goal.
        boolean hasActiveGoal = !currentGoal.equals(BlockPos.ZERO);

        ensureCraftingTableAvailable(hasActiveGoal);
        // Simple starter tool progression at table (wood tier)
        if (!knownCraftingTable.equals(BlockPos.ZERO) && this.blockPosition().closerThan(knownCraftingTable, 4.0)) {
            craftStarterToolsAtTable();
        }

        // Furnace pipeline (basic): ensure, move to, smelt inputs, collect outputs
        ensureFurnaceAvailable();
        BlockPos furnacePos = chooseFurnaceForPendingSmelts();
        if (furnacePos != null) {
            if (!this.blockPosition().closerThan(furnacePos, 4.0)) {
                // Only redirect to furnace if bot has no active goal
                if (!hasActiveGoal) {
                    this.currentGoal = furnacePos;
                }
            } else {
                serviceFurnaceAt(furnacePos);
            }
        }
    }

    private void ensureCraftingTableAvailable(boolean hasActiveGoal) {
        // If we know one and it's loaded, done
        if (!knownCraftingTable.equals(BlockPos.ZERO)) {
            if (!level().getBlockState(knownCraftingTable).is(Blocks.CRAFTING_TABLE)) {
                knownCraftingTable = BlockPos.ZERO;
            }
        }

        if (knownCraftingTable.equals(BlockPos.ZERO)) {
            // search nearby
            BlockPos found = findNearestBlockExact(Blocks.CRAFTING_TABLE, 12);
            if (found != null) {
                knownCraftingTable = found;
                return;
            }

            // No table placed: craft one then place it nearby
            if (countTotalPlanks() >= 4) {
                if (removeAnyPlanks(4) == 4) {
                    addToInventory(new ItemStack(Blocks.CRAFTING_TABLE.asItem(), 1));
                    broadcastGroupChat("Crafted a crafting table.");
                }
            }

            if (getInventory().countItem(Blocks.CRAFTING_TABLE.asItem()) > 0) {
                BlockPos place = findPlacementNear(blockPosition(), 3);
                if (place != null) {
                    if (removeItems(Blocks.CRAFTING_TABLE.asItem(), 1) == 1) {
                        level().setBlock(place, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                        knownCraftingTable = place;
                        broadcastGroupChat("Placed a crafting table at " + place + ".");
                    }
                }
            } else {
                // Only redirect to placement area if bot has no active goal
                if (!hasActiveGoal && countTotalPlanks() >= 4) {
                    BlockPos target = blockPosition().offset(2, 0, 2);
                    this.currentGoal = target;
                }
            }
        } else {
            // Only move toward table if bot has no active goal and intends to craft
            if (!hasActiveGoal && !this.blockPosition().closerThan(knownCraftingTable, 4.0)) {
                this.currentGoal = knownCraftingTable;
            }
        }
    }

    private int countTotalPlanks() {
        return countItemInInventory(Items.OAK_PLANKS) + countItemInInventory(Items.SPRUCE_PLANKS)
                + countItemInInventory(Items.BIRCH_PLANKS) + countItemInInventory(Items.JUNGLE_PLANKS)
                + countItemInInventory(Items.ACACIA_PLANKS) + countItemInInventory(Items.DARK_OAK_PLANKS)
                + countItemInInventory(Items.MANGROVE_PLANKS) + countItemInInventory(Items.CHERRY_PLANKS)
                + countItemInInventory(Items.BAMBOO_PLANKS);
    }

    private BlockPos findPlacementNear(BlockPos center, int radius) {
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = center.offset(dx, 0, dz);
                    if (level().getBlockState(p).isAir() && level().getBlockState(p.below()).canOcclude()) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findNearestBlockExact(Block block, int radius) {
        BlockPos best = null; double bestD2 = Double.MAX_VALUE;
        BlockPos c = blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (level().getBlockState(p).is(block)) {
                        double d2 = p.distSqr(c);
                        if (d2 < bestD2) { bestD2 = d2; best = p; }
                    }
                }
            }
        }
        return best;
    }

    private void craftStarterToolsAtTable() {
        int sticks = countItemInInventory(Items.STICK);
        if (sticks < 2 && countTotalPlanks() >= 2) {
            if (removeAnyPlanks(2) == 2) {
                addToInventory(new ItemStack(Items.STICK, 4));
                broadcastGroupChat("Crafted 4 sticks at the table.");
            }
        }

        // Wooden pickaxe: 3 planks + 2 sticks
        if (getInventory().countItem(Items.WOODEN_PICKAXE) == 0 && countTotalPlanks() >= 3 && countItemInInventory(Items.STICK) >= 2) {
            if (removeAnyPlanks(3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.WOODEN_PICKAXE, 1));
                broadcastGroupChat("Crafted a wooden pickaxe.");
            }
        }

        // Wooden axe
        if (getInventory().countItem(Items.WOODEN_AXE) == 0 && countTotalPlanks() >= 3 && countItemInInventory(Items.STICK) >= 2) {
            if (removeAnyPlanks(3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.WOODEN_AXE, 1));
                broadcastGroupChat("Crafted a wooden axe.");
            }
        }

        // Wooden sword
        if (getInventory().countItem(Items.WOODEN_SWORD) == 0 && countTotalPlanks() >= 2 && countItemInInventory(Items.STICK) >= 1) {
            if (removeAnyPlanks(2) == 2 && removeItems(Items.STICK, 1) == 1) {
                addToInventory(new ItemStack(Items.WOODEN_SWORD, 1));
                broadcastGroupChat("Crafted a wooden sword.");
            }
        }
    }

    // ==================== Furnace/Smoker/Blast management ====================
    private void ensureFurnaceAvailable() {
        if (!knownFurnace.equals(BlockPos.ZERO)) {
            if (!level().getBlockState(knownFurnace).is(Blocks.FURNACE)) knownFurnace = BlockPos.ZERO;
        }
        if (knownFurnace.equals(BlockPos.ZERO)) {
            BlockPos found = findNearestBlockExact(Blocks.FURNACE, 12);
            if (found != null) { knownFurnace = found; return; }

            // Craft a furnace if we have cobblestone
            if (countItemInInventory(Items.COBBLESTONE) >= 8) {
                if (removeItems(Items.COBBLESTONE, 8) == 8) {
                    addToInventory(new ItemStack(Blocks.FURNACE.asItem(), 1));
                    broadcastGroupChat("Crafted a furnace.");
                }
            }
            if (getInventory().countItem(Blocks.FURNACE.asItem()) > 0) {
                BlockPos place = findPlacementNear(blockPosition(), 3);
                if (place != null) {
                    if (removeItems(Blocks.FURNACE.asItem(), 1) == 1) {
                        level().setBlock(place, Blocks.FURNACE.defaultBlockState(), 3);
                        knownFurnace = place;
                        broadcastGroupChat("Placed a furnace at " + place + ".");
                    }
                }
            }
        }
    }

    private BlockPos chooseFurnaceForPendingSmelts() {
        // For now, only regular furnace; later: prefer smoker for food, blast for ores
        if (!knownFurnace.equals(BlockPos.ZERO)) {
            // If we have raw food or ores to smelt, return furnace position
            if (hasAnyRawFood() || hasAnyOre()) return knownFurnace;
        }
        return null;
    }

    private boolean hasAnyRawFood() {
        return countItemInInventory(Items.BEEF) > 0 || countItemInInventory(Items.PORKCHOP) > 0
                || countItemInInventory(Items.CHICKEN) > 0 || countItemInInventory(Items.MUTTON) > 0
                || countItemInInventory(Items.COD) > 0 || countItemInInventory(Items.SALMON) > 0
                || countItemInInventory(Items.POTATO) > 0;
    }

    private boolean hasAnyOre() {
        return countItemInInventory(Items.IRON_ORE) > 0 || countItemInInventory(Items.RAW_IRON) > 0
                || countItemInInventory(Items.GOLD_ORE) > 0 || countItemInInventory(Items.RAW_GOLD) > 0
                || countItemInInventory(Items.COPPER_ORE) > 0 || countItemInInventory(Items.RAW_COPPER) > 0;
    }

    private void serviceFurnaceAt(BlockPos pos) {
        BlockEntity be = level().getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return;

        // Collect outputs first (slot 2)
        ItemStack out = furnace.getItem(2);
        if (!out.isEmpty()) {
            addToInventory(out.copy());
            furnace.setItem(2, ItemStack.EMPTY);
            broadcastGroupChat("Collected " + out.getCount() + " " + out.getItem().getDescriptionId() + " from furnace.");
        }

        // Determine input and fuel
        ItemStack input = ItemStack.EMPTY;
        if (hasAnyOre()) {
            input = takeFirstAvailable(Items.RAW_IRON, Items.IRON_ORE, Items.RAW_GOLD, Items.GOLD_ORE, Items.RAW_COPPER, Items.COPPER_ORE);
        } else if (hasAnyRawFood()) {
            input = takeFirstAvailable(Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.COD, Items.SALMON, Items.POTATO);
        }

        ItemStack fuel = takeFirstAvailable(Items.COAL, Items.CHARCOAL, Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG,
                Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG, Items.BAMBOO_BLOCK,
                Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS,
                Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS);

        // Insert if slots empty
        if (!input.isEmpty() && furnace.getItem(0).isEmpty()) furnace.setItem(0, input);
        if (!fuel.isEmpty() && furnace.getItem(1).isEmpty()) furnace.setItem(1, fuel);
    }

    private ItemStack takeFirstAvailable(Item... candidates) {
        for (Item it : candidates) {
            if (countItemInInventory(it) > 0) {
                // remove as much as possible up to a stack
                int removed = removeItems(it, 64);
                if (removed > 0) return new ItemStack(it, Math.min(removed, 64));
            }
        }
        return ItemStack.EMPTY;
    }

    // ============ Door handling and local avoidance ============
    // ==================== PILLAR CLIMB SYSTEM ====================

    /** Enter pillar mode: build up, harvest, then tear down. */
    private void enterPillarMode(BlockPos target) {
        pillarPhase = PillarPhase.BUILDING;
        pillarBaseY = blockPosition().getY();
        pillarTarget = target;
        placedPillarBlocks.clear();
        pillarWasAirborne = false;
        pillarCooldown = 0;
        System.out.printf("[AMB-PILLAR] %s entering pillar mode target=%s baseY=%d%n",
            getName().getString(), target, pillarBaseY);
    }

    /** Reset all pillar state and optionally trigger a new goal search. */
    private void exitPillarMode(boolean findNewGoal) {
        pillarPhase = PillarPhase.IDLE;
        placedPillarBlocks.clear();
        pillarTarget = BlockPos.ZERO;
        if (miningState.isMining) miningState.isMining = false;
        if (findNewGoal) {
            currentGoal = BlockPos.ZERO;
            System.out.println("[AMB-PILLAR] " + getName().getString() + " pillar complete, seeking next goal");
        }
    }

    /**
     * Tick the pillar system every tick while pillarPhase != IDLE.
     * Handles vertical physics, block placement on jump apex, mining, and teardown.
     */
    private void tickPillarSystem() {
        if (pillarCooldown > 0) {
            pillarCooldown--;
            applyVerticalPhysicsOnly(); // keep physics running during cooldown
            return;
        }

        switch (pillarPhase) {
            case BUILDING -> {
                int heightDiff = pillarTarget.getY() - blockPosition().getY();
                if (heightDiff <= 1) {
                    // Close enough — switch to mining
                    pillarPhase = PillarPhase.MINING;
                    System.out.println("[AMB-PILLAR] " + getName().getString() + " reached height, mining " + pillarTarget);
                    return;
                }

                // Find a block item to place
                int buildSlot = findBuildingBlockSlot();
                if (buildSlot < 0) {
                    System.out.println("[AMB-PILLAR] " + getName().getString() + " no building blocks, aborting pillar");
                    exitPillarMode(true);
                    return;
                }

                // Apply vertical physics (keeps gravity correct between jumps)
                applyVerticalPhysicsOnly();

                if (onGround() && jumpCooldown == 0) {
                    jumpFromGround();
                    jumpCooldown = 20;
                    pillarWasAirborne = false;
                } else if (!onGround() && getDeltaMovement().y > 0.05) {
                    pillarWasAirborne = true; // mark that we've left the ground rising
                } else if (pillarWasAirborne && !onGround() && getDeltaMovement().y <= 0.05) {
                    // At or near the jump apex — place block in the air below us
                    BlockPos placePos = new BlockPos(blockPosition().getX(),
                                                     blockPosition().getY() - 1,
                                                     blockPosition().getZ());
                    if (level() instanceof ServerLevel sl && !sl.getBlockState(placePos).canOcclude()) {
                        ItemStack stack = getInventory().getItem(buildSlot);
                        if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                            sl.setBlock(placePos, bi.getBlock().defaultBlockState(), 3);
                            stack.shrink(1);
                            placedPillarBlocks.add(placePos);
                            pillarWasAirborne = false;
                            pillarCooldown = 8;
                            System.out.println("[AMB-PILLAR] " + getName().getString()
                                + " placed block at " + placePos + " (height " + placedPillarBlocks.size() + ")");
                        }
                    }
                }
            }

            case MINING -> {
                applyVerticalPhysicsOnly();
                BlockState bs = level().getBlockState(pillarTarget);
                if (bs.isAir() || !shouldMineBlock(bs)) {
                    // Block gone — look for more above in this column
                    BlockPos next = findNextMineableAbove(pillarTarget);
                    if (next != null) {
                        pillarTarget = next;
                        int newHeightDiff = next.getY() - blockPosition().getY();
                        if (newHeightDiff > 1) {
                            pillarPhase = PillarPhase.BUILDING; // need to go higher
                        }
                        // else stay in MINING — can reach from current height
                    } else {
                        pillarPhase = PillarPhase.TEARDOWN;
                        if (miningState.isMining) miningState.isMining = false;
                        System.out.println("[AMB-PILLAR] " + getName().getString() + " column mined, tearing down");
                    }
                    return;
                }
                // Look at the target and start mining if not already
                RealisticMovement.lookAt(this, Vec3.atCenterOf(pillarTarget));
                if (!miningState.isMining) {
                    RealisticActions.equipBestTool(this, bs);
                    RealisticActions.startMining(this, pillarTarget, miningState);
                }
            }

            case TEARDOWN -> {
                if (placedPillarBlocks.isEmpty()) {
                    exitPillarMode(true);
                    return;
                }
                if (!onGround()) {
                    applyVerticalPhysicsOnly();
                    return;
                }
                // The topmost placed block should be directly below our feet
                BlockPos blockBelowFeet = blockPosition().below();
                BlockPos topPlaced = placedPillarBlocks.get(placedPillarBlocks.size() - 1);
                if (blockBelowFeet.equals(topPlaced)) {
                    setXRot(89.0f); // look straight down
                    if (level() instanceof ServerLevel sl) {
                        sl.destroyBlock(topPlaced, true, this);
                        placedPillarBlocks.remove(placedPillarBlocks.size() - 1);
                        pillarCooldown = 12; // wait for bot to fall before next break
                        System.out.println("[AMB-PILLAR] " + getName().getString() + " removed pillar block at " + topPlaced);
                    }
                } else if (blockPosition().getY() <= pillarBaseY) {
                    // Back at base even if list isn't empty (blocks already broken by world events)
                    exitPillarMode(true);
                } else {
                    // Not standing on a placed block — might have been pushed off; just fall
                    applyVerticalPhysicsOnly();
                }
            }

            default -> pillarPhase = PillarPhase.IDLE;
        }
    }

    /** Apply gravity/landing physics with no horizontal movement (used during pillar operation). */
    private void applyVerticalPhysicsOnly() {
        double dy = getDeltaMovement().y;
        move(MoverType.SELF, new Vec3(0.0, dy, 0.0));
        double nextDY = onGround() ? -0.08 : Math.max(getDeltaMovement().y - 0.08, -3.5);
        setDeltaMovement(0, nextDY, 0);
    }

    /**
     * Find the first inventory slot containing a solid BlockItem the bot can place.
     * Prefers dirt/sand/gravel/stone; falls back to any solid BlockItem (including logs).
     */
    private int findBuildingBlockSlot() {
        int fallbackSlot = -1;
        for (int i = 0; i < getInventory().getContainerSize(); i++) {
            ItemStack stack = getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi)) continue;
            if (!bi.getBlock().defaultBlockState().canOcclude()) continue; // must be a solid, full block
            // Prefer common building materials
            if (stack.is(Items.DIRT) || stack.is(Items.SAND) || stack.is(Items.GRAVEL)
                    || stack.is(Items.COBBLESTONE) || stack.is(Items.STONE)
                    || stack.is(Items.GRAVEL)) {
                return i;
            }
            if (fallbackSlot < 0) fallbackSlot = i; // first solid block found (e.g. oak_log)
        }
        return fallbackSlot; // -1 if no blocks at all
    }

    /**
     * Search directly above `from` for more blocks this task wants to mine (e.g. higher logs in a tree).
     */
    private BlockPos findNextMineableAbove(BlockPos from) {
        for (int dy = 1; dy <= 12; dy++) {
            BlockPos check = from.above(dy);
            BlockState bs = level().getBlockState(check);
            if (shouldMineBlock(bs)) return check;
            if (!bs.isAir() && bs.canOcclude()) break; // solid non-target stops the search
        }
        return null;
    }

    /**
     * Break blocks to clear a path ahead, accounting for whether the bot needs to go UP.
     *
     * Horizontal obstacle (wpDY ≤ 1):
     *   Break dy=0 (feet) and dy=1 (head) — opens a passage at the current level.
     *
     * Upward obstacle (wpDY > 1):
     *   Leave dy=0 SOLID — it becomes the step-platform the bot lands on after jumping.
     *   Break dy=1 (future feet at next level) and dy=2 (future head at next level).
     *   With those cleared the bot can jump, the step-up mechanism (~0.6 block) lands it
     *   on the solid dy=0 block, advancing one level.  Subsequent calls climb the column.
     */
    private void tryBreakPathBlock(int wpDY) {
        if (!(level() instanceof ServerLevel sl)) return;
        BlockPos ahead = blockPosition().relative(getDirection());
        int dyStart = (wpDY > 1) ? 1 : 0; // upward: skip dy=0 (keep as step), horizontal: start at dy=0
        int dyEnd   = (wpDY > 1) ? 2 : 1;
        for (int dy = dyStart; dy <= dyEnd; dy++) {
            BlockPos breakPos = ahead.above(dy);
            BlockState bs = sl.getBlockState(breakPos);
            if (bs.canOcclude() && !bs.is(Blocks.BEDROCK)) {
                RealisticActions.equipBestTool(this, bs);
                sl.destroyBlock(breakPos, true, this);
                System.out.printf("[AMB-BREAK] %s clearing %s path block at %s%n",
                    getName().getString(), wpDY > 1 ? "upward" : "horizontal", breakPos);
                return;
            }
        }
    }

    /**
     * Queue this bot for respawn after death.
     * The respawn is processed by bot.BotTicker after a 10-second delay.
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        BlockPos dPos = blockPosition();
        BlockPos bedPos = !baseLocation.equals(BlockPos.ZERO) ? baseLocation : BlockPos.ZERO;
        String botName = getName().getString();
        RESPAWN_QUEUE.add(new RespawnRequest(botName, llmGroup, currentTask, dPos, bedPos));
        System.out.println("[AMB-DEATH] " + botName + " died at " + dPos + " — queued respawn in 10s");
        broadcastGroupChat("I died... I'll be back soon.");
        super.die(cause);
    }

    private boolean isStuckInNonSolidBlock() {
        BlockPos pos = blockPosition();
        BlockState feet = level().getBlockState(pos);
        BlockState head = level().getBlockState(pos.above());

        // Check if we're clipped inside a door or fence gate (geometry glitch only)
        // Do NOT flag generic non-solid blocks (leaves, slabs, etc.) — bots can stand on them legitimately.
        boolean inDoor = (feet.getBlock() instanceof DoorBlock || feet.getBlock() instanceof FenceGateBlock) ||
                        (head.getBlock() instanceof DoorBlock || head.getBlock() instanceof FenceGateBlock);

        return inDoor;
    }

    private boolean attemptDoorRescue() {
        // Skip if we're ignoring doors
        if (doorIgnoreTicks > 0) return false;

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
            // ENHANCED: Save current goal before door navigation
            if (!currentGoal.equals(BlockPos.ZERO)) {
                preExitGoal = currentGoal;
                System.out.println("[AMB-DOOR] Saving pre-exit goal: " + preExitGoal);
            }

            doorPos = best;
            originalDoorPos = best; // Store the actual door position
            doorPhase = 1;
            doorTimer = 60; // 3 seconds plan
            // Bug 1: mark door rescue active so stuck detection is suppressed
            doorRescueActive = true;
            doorRescueStartPos = blockPosition();
            return true;
        }
        return false;
    }

    private void handleDoorPlan() {
        if (doorPhase <= 0) return;
        if (doorTimer-- <= 0) {
            doorPhase = 0;
            doorPos = BlockPos.ZERO;
            originalDoorPos = BlockPos.ZERO;
            preExitGoal = BlockPos.ZERO;
            doorIgnoreTicks = 0;
            return;
        }

        // Decrement door ignore ticks
        if (doorIgnoreTicks > 0) doorIgnoreTicks--;

        // Phase 1: Approach the door
        if (doorPhase == 1) {
            BlockState st = level().getBlockState(originalDoorPos);
            if (!(st.getBlock() instanceof DoorBlock door)) {
                doorPhase = 0;
                doorPos = BlockPos.ZERO;
                originalDoorPos = BlockPos.ZERO;
                preExitGoal = BlockPos.ZERO;
                return;
            }

            if (this.blockPosition().closerThan(originalDoorPos, 2.2)) {
                // Close enough, try to open it
                Boolean open = st.getOptionalValue(BlockStateProperties.OPEN).orElse(false);
                if (!open) {
                    RealisticActions.interactWithBlock(this, originalDoorPos);
                    System.out.println("[AMB-DOOR] " + getName().getString() + " opening door at " + originalDoorPos);
                }

                // Determine which side of the door leads OUTSIDE using sky visibility.
                // Goal-direction heuristic fails when the door is in a perpendicular wall:
                // e.g. bot is inside hitting the south wall, goal is south, door is in the
                // north wall — goal-direction says "go south" but south is deeper inside.
                Direction facing = st.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
                BlockPos sideA = originalDoorPos.relative(facing, 2);
                BlockPos sideB = originalDoorPos.relative(facing.getOpposite(), 2);
                boolean aOutside = level().canSeeSky(sideA) || level().canSeeSky(sideA.above());
                boolean bOutside = level().canSeeSky(sideB) || level().canSeeSky(sideB.above());
                Direction travelDir;
                if (aOutside && !bOutside) {
                    travelDir = facing;
                } else if (bOutside && !aOutside) {
                    travelDir = facing.getOpposite();
                } else {
                    // Both/neither open sky (underground or open area): use goal direction
                    Vec3 doorCenterVec = Vec3.atCenterOf(originalDoorPos);
                    Vec3 goalVec = Vec3.atCenterOf(preExitGoal.equals(BlockPos.ZERO) ? blockPosition() : preExitGoal)
                                     .subtract(doorCenterVec);
                    double dot = goalVec.x * facing.getStepX() + goalVec.z * facing.getStepZ();
                    travelDir = dot >= 0 ? facing : facing.getOpposite();
                }
                doorTravelDir = travelDir;

                // Exit target: 6 blocks beyond door (not 3) so bot clears the building's
                // outer walls and lands in open air, not still inside an adjacent room.
                // Aligned with door center XZ so the bot doesn't drift into wall blocks.
                BlockPos beyond = originalDoorPos.relative(travelDir, 6);
                // Scan up/down on the exact XZ for a walkable floor level.
                BlockPos walkable = beyond;
                if (level() instanceof ServerLevel sl) {
                    for (int dy = 0; dy <= 3; dy++) {
                        if (RealisticMovement.isWalkable(sl, beyond.above(dy))) { walkable = beyond.above(dy); break; }
                        if (dy > 0 && RealisticMovement.isWalkable(sl, beyond.below(dy))) { walkable = beyond.below(dy); break; }
                    }
                }

                // Update the doorPos to be the target beyond the door
                doorPos = walkable;
                doorPhase = 2; // Move to phase 2: pass through
                System.out.println("[AMB-DOOR] " + getName().getString()
                    + " traversal: door=" + originalDoorPos + " facing=" + facing
                    + " sideA(facing)sky=" + aOutside + " sideB(opp)sky=" + bOutside
                    + " travelDir=" + travelDir + " target=" + walkable);
            }
        }

        // Phase 2: Pass through the door with lateral alignment correction
        if (doorPhase == 2) {
            // Force both door halves open every tick so collision doesn't block movement
            if (level() instanceof ServerLevel sl) {
                forceDoorOpen(sl, originalDoorPos);
                forceDoorOpen(sl, originalDoorPos.above());
            }

            // Move forward in doorTravelDir while correcting lateral drift toward door center.
            // Naive direct-to-target movement is diagonal and collides with the wall beside the door.
            // Instead: pure forward component + small perpendicular snap to door center XZ.
            float spd = 0.13f;
            double fwdX = doorTravelDir.getStepX() * spd;
            double fwdZ = doorTravelDir.getStepZ() * spd;

            // Perpendicular correction: snap bot to door center on the axis orthogonal to travel
            Vec3 doorCenterVec = Vec3.atCenterOf(originalDoorPos);
            double perpCorrection;
            double latX = 0, latZ = 0;
            if (doorTravelDir.getAxis() == Direction.Axis.X) {
                // Traveling East/West — correct Z toward door center Z
                perpCorrection = (doorCenterVec.z - getZ()) * 0.5;
                perpCorrection = Math.max(-0.12, Math.min(0.12, perpCorrection));
                latZ = perpCorrection;
            } else {
                // Traveling North/South — correct X toward door center X
                perpCorrection = (doorCenterVec.x - getX()) * 0.5;
                perpCorrection = Math.max(-0.12, Math.min(0.12, perpCorrection));
                latX = perpCorrection;
            }

            float yaw = (float)(Math.atan2(fwdZ, fwdX) * 180.0 / Math.PI) - 90.0f;
            setYRot(yaw);
            setYHeadRot(yaw);
            yBodyRot = yaw;
            setXRot(0.0f);

            Vec3 movement = new Vec3(fwdX + latX, getDeltaMovement().y, fwdZ + latZ);
            move(MoverType.SELF, movement);
            setDeltaMovement(0, getDeltaMovement().y * 0.98, 0);

            // Check if we've reached the position beyond the door
            if (this.position().distanceToSqr(Vec3.atBottomCenterOf(doorPos)) < 2.5 * 2.5) {
                doorPhase = 3;
                System.out.println("[AMB-DOOR] " + getName().getString() + " reached beyond door, verifying passage");
            }
        }

        // Phase 3: Verify passage
        if (doorPhase == 3) {
            // Check if we're actually on the other side
            double distFromDoor = this.position().distanceTo(Vec3.atCenterOf(originalDoorPos));
            if (distFromDoor > 2.0) {
                // Successfully passed through, move to phase 4
                doorPhase = 4;
                doorIgnoreTicks = 200; // Ignore this door for 10 seconds
                System.out.println("[AMB-DOOR] " + getName().getString() + " verified passage, entering post-exit check");
            }
        }

        // Phase 4: Post-Exit Check - scan for new path to original goal
        if (doorPhase == 4) {
            // If we had a goal before the door, try to path to it again
            if (!preExitGoal.equals(BlockPos.ZERO)) {
                System.out.println("[AMB-DOOR] " + getName().getString() + " post-exit: resuming path to original goal " + preExitGoal);
                currentGoal = preExitGoal;
                currentPath.clear(); // Force recompute path
                pathIndex = 0;
            }

            // Clear door state
            doorPhase = 0;
            doorPos = BlockPos.ZERO;
            originalDoorPos = BlockPos.ZERO;
            preExitGoal = BlockPos.ZERO;
            doorTimer = 0;
            exitCooldown = 200; // 10 second cooldown to prevent interior detection from retriggering
            System.out.println("[AMB-DOOR] " + getName().getString() + " door navigation complete!");
        }
    }

    // ==================== TASK EXECUTION ====================

    private void executeCurrentTask() {
        // DEBUG: Log stack trace to see where this is being called from
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String caller = stack.length > 2 ? stack[2].getLineNumber() + "" : "unknown";
        System.out.println("[AMB-DEBUG] " + getName().getString() + " executeCurrentTask() called from line " + caller);

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
                // CRITICAL FIX: Find the BASE of the tree (lowest log), not a random log
                BlockPos tree = findNearestBlock(BlockTags.LOGS, 32);
                if (tree != null) {
                    // Find the lowest log in this tree (the base)
                    BlockPos baseLog = findTreeBase(tree);

                    // ENHANCED: Validate goal before setting
                    TaskValidation.ValidationResult validation = TaskValidation.validateGoal(this, baseLog, currentTask);

                    if (!validation.isValid) {
                        System.out.println("[AMB-TASK] " + getName().getString() + " goal validation failed: " + validation.reason);
                        // Try to find an alternative tree
                        tree = findNearestBlock(BlockTags.LOGS, 32);
                        if (tree == null) {
                            System.out.println("[AMB-TASK] " + getName().getString() + " no valid trees found");
                            return;
                        }
                        baseLog = findTreeBase(tree);
                        validation = TaskValidation.validateGoal(this, baseLog, currentTask);
                        if (!validation.isValid) {
                            System.out.println("[AMB-TASK] " + getName().getString() + " no reachable trees found");
                            return;
                        }
                    }

                    // Use adjusted goal if validation suggested one
                    if (!validation.adjustedGoal.equals(BlockPos.ZERO)) {
                        baseLog = validation.adjustedGoal;
                        System.out.println("[AMB-TASK] " + getName().getString() + " using adjusted goal: " + baseLog);
                    }

                    // Check distance to tree base
                    double distToTree = Math.sqrt(blockPosition().distSqr(baseLog));

                    if (distToTree < 4.0) {
                        // We're close - find the nearest log at eye height for mining
                        BlockPos eyeHeightLog = findNearestLogAtEyeHeight(baseLog);
                        currentGoal = eyeHeightLog;
                        System.out.println("[AMB-TASK] " + getName().getString() + " near tree, setting goal to eye-height log at " + eyeHeightLog + " for mining");
                    } else {
                        // We're far - find a walkable position adjacent to the tree base
                        ServerLevel sl = (ServerLevel) level();
                        BlockPos groundPos = baseLog.below();
                        // Use isWalkable (not canOcclude) - the ground below a log trunk is solid, not walkable
                        if (!RealisticMovement.isWalkable(sl, groundPos)) {
                            groundPos = RealisticMovement.findNearestWalkable(sl, baseLog, blockPosition());
                        }
                        // Don't set goal to our own position (findNearestWalkable fallback) - that means no path exists
                        if (groundPos.equals(blockPosition())) {
                            System.out.println("[AMB-TASK] " + getName().getString() + " no walkable position found near tree at " + baseLog + ", trying another tree");
                            return;
                        }
                        currentGoal = groundPos;
                        System.out.println("[AMB-TASK] " + getName().getString() + " found tree base at " + baseLog + ", moving to walkable position " + groundPos + " (distance: " + distToTree + ")");
                    }

                    // Clear any door plan when pursuing resource
                    doorPhase = 0; doorPos = BlockPos.ZERO; originalDoorPos = BlockPos.ZERO; doorTimer = 0; avoidTicks = 0;
                    goalLockTimer = 400;
                    // Force immediate path recomputation for the new goal
                    currentPath.clear();
                    pathIndex = 0;
                    pathRetryTimer = 0;
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
                    doorPhase = 0; doorPos = BlockPos.ZERO; originalDoorPos = BlockPos.ZERO; doorTimer = 0; avoidTicks = 0;
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
        double nearestScore = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = myPos.offset(x, y, z);
                    if (level().getBlockState(check).is(tag)) {
                        // Calculate score: horizontal distance + (vertical distance * 3)
                        // This heavily penalizes vertical distance to prefer same-level blocks
                        int dx = check.getX() - myPos.getX();
                        int dy = check.getY() - myPos.getY();
                        int dz = check.getZ() - myPos.getZ();
                        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
                        double verticalPenalty = Math.abs(dy) * 3.0; // 3x penalty for vertical
                        double score = horizontalDist + verticalPenalty;

                        // Skip if too high (>5 blocks) - unreachable without climbing
                        if (Math.abs(dy) > 5) {
                            continue;
                        }

                        if (score < nearestScore) {
                            nearestScore = score;
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

    /**
     * Find the base (lowest log) of a tree starting from any log position
     */
    private BlockPos findTreeBase(BlockPos startLog) {
        BlockPos lowest = startLog;

        // Search downward for the lowest log
        for (int dy = 0; dy >= -10; dy--) {
            BlockPos check = startLog.offset(0, dy, 0);
            BlockState state = level().getBlockState(check);

            if (state.is(BlockTags.LOGS)) {
                lowest = check;
            } else {
                // Hit non-log block, stop searching
                break;
            }
        }

        return lowest;
    }

    /**
     * Find the nearest log at eye height (1-2 blocks above ground) for mining
     */
    private BlockPos findNearestLogAtEyeHeight(BlockPos baseLog) {
        BlockPos myPos = blockPosition();
        int myEyeY = myPos.getY() + 1; // Eye height is 1 block above feet

        // Search in a 5x5 area around the base log at eye height
        BlockPos nearest = baseLog;
        double nearestDist = Double.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 5; dy++) { // Check up to 5 blocks high
                    BlockPos check = baseLog.offset(dx, dy, dz);

                    // Prefer logs at eye height (within 1 block of eye level)
                    if (level().getBlockState(check).is(BlockTags.LOGS)) {
                        double dist = myPos.distSqr(check);
                        int heightDiff = Math.abs(check.getY() - myEyeY);

                        // Penalize logs that aren't at eye height
                        double score = dist + (heightDiff * 2.0);

                        if (score < nearestDist) {
                            nearestDist = score;
                            nearest = check;
                        }
                    }
                }
            }
        }

        return nearest;
    }

    /**
     * Find mineable blocks nearby for the current task
     */
    private BlockPos findMineableNearby(BlockPos center, int radius, String task) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    BlockState state = level().getBlockState(check);

                    if (shouldMineBlock(state)) {
                        double dist = blockPosition().distSqr(check);
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
        this.setNoGravity(false); // enforce gravity every tick
        super.tick();

        // ── Part 5: Stuck detection (every 20 / 100 ticks) ───────────────────
        if (tickCount % 20 == 0) {
            BlockPos now = blockPosition();
            if (lastKnownPos20 != null && now.equals(lastKnownPos20) && isMovingToGoal) {
                escapeStuckTicks += 20;
                if (escapeStuckTicks % 100 == 0) {
                    System.out.println("[STUCK] " + getName().getString()
                            + " has not moved for " + escapeStuckTicks + " ticks");
                }
                if (escapeStuckTicks >= 300 && !escapeHelper.isActive()) {
                    System.out.println("[STUCK] " + getName().getString()
                            + " triggering structural escape after " + escapeStuckTicks + " stuck ticks");
                    escapeHelper.reset(); // ensure fresh start
                }
            } else {
                escapeStuckTicks = 0;
            }
            lastKnownPos20 = now;
        }

        if (tickCount % 100 == 0) {
            BlockPos now = blockPosition();
            if (lastKnownPos100 != null && now.equals(lastKnownPos100) && isMovingToGoal) {
                System.out.println("[STUCK-100] " + getName().getString()
                        + " still stuck at " + now + " after 100 ticks");
            }
            lastKnownPos100 = now;
        }
        // ─────────────────────────────────────────────────────────────────────

        runAllPlayerActions();
        if (spawnIdleTimer == 99 && !roleAnnouncementDone) {
            assignInitialRole();
            roleAnnouncementDone = true;
        }
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
