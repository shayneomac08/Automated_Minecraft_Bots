package com.shayneomac08.automated_minecraft_bots.entity;

import com.mojang.authlib.GameProfile;
import com.shayneomac08.automated_minecraft_bots.movement.RealisticActions;
import com.shayneomac08.automated_minecraft_bots.movement.RealisticMovement;
import com.shayneomac08.automated_minecraft_bots.movement.StuckDetection;
import com.shayneomac08.automated_minecraft_bots.movement.VerticalNavigation;
import com.shayneomac08.automated_minecraft_bots.movement.HumanlikeMovement;

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
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

/**
 * NEW STABLE FAKEPLAYER AMBNPCENTITY
 * Clean FakePlayer-based bot implementation (FakePlayer handles connection automatically)
 */
public class AmbNpcEntity extends FakePlayer {

    // ==================== RESPAWN SYSTEM ====================
    /** Global queue of bots waiting to respawn. Survives entity removal. */
    public static final Queue<RespawnRequest> RESPAWN_QUEUE = new ConcurrentLinkedQueue<>();

    // ==================== MULTI-BOT TARGET CLAIMING ====================
    /**
     * Cross-bot resource claim registry.
     * Maps each claimed BlockPos to a long[2]: [0]=hashCode of owning bot name, [1]=expiry ms.
     * Bots skip positions claimed by another bot when selecting their next harvest target,
     * preventing multiple bots from dogpiling the same tree or stone outcrop.
     */
    private static final java.util.concurrent.ConcurrentHashMap<BlockPos, long[]> CLAIMED_TARGETS =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** How long a claim is valid before it auto-expires (milliseconds). */
    private static final long CLAIM_TTL_MS = 15_000L; // 15 seconds

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
    private boolean craftingTableSelfPlaced = false; // true if this bot placed the table (so it can pick it up)
    /** Consecutive "craft" task invocations that ended without a usable table found or placed. */
    private int craftStallCount = 0;

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
    private int  pillarPlaceFailCount = 0;  // abort pillar after repeated terrain-blocked apex

    // ── Foliage obstruction clearing ─────────────────────────────────────────
    // When a leaf block is found on the line of access to the log goal, mining is
    // temporarily redirected here.  currentGoal (the log) is never changed so that
    // after the leaf is broken, normal log-harvesting resumes automatically.
    private BlockPos foliageClearTarget = BlockPos.ZERO;

    // Item seeking
    private ItemEntity seekingItem = null;

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

    // ── Jump-loop prevention ─────────────────────────────────────────────────
    // When the bot jumps repeatedly from the same area without advancing toward
    // the goal, the obstacle is unbreakable (bedrock, player wall, etc.).
    // After JUMP_LOOP_LIMIT consecutive jumps without horizontal progress, the
    // current goal is blacklisted so target-finders skip it for ~60 seconds.
    private int jumpsSinceProgress = 0;
    private static final int JUMP_LOOP_LIMIT = 5; // 5 unproductive jumps → blacklist goal (was 8)
    private static final int GOAL_BLACKLIST_TICKS = 1200; // 60 s at 20 TPS
    // goal BlockPos → tick at which the blacklist entry expires
    private final java.util.Map<BlockPos, Integer> unreachableGoalBlacklist = new java.util.HashMap<>();

    // ── Emergency break / structure repair ───────────────────────────────────
    /**
     * Records a protected block that was broken during an emergency escape so it can
     * be restored afterward. Stored by BotEscapeHelper via recordEmergencyBreak().
     */
    public record EmergencyBreak(BlockPos pos, BlockState state, String reason) {}
    private final List<EmergencyBreak> repairQueue = new ArrayList<>();

    /**
     * Called by BotEscapeHelper when it must break a protected block as a last resort.
     * Records the block type/state so the bot can restore it after escaping.
     */
    public void recordEmergencyBreak(BlockPos pos, BlockState state, String reason) {
        repairQueue.add(new EmergencyBreak(pos, state, reason));
        System.out.printf("[AMB-REPAIR] %s emergency break queued: %s at %s — reason: %s (queue=%d)%n",
            getName().getString(), state.getBlock().getName().getString(), pos, reason, repairQueue.size());
    }

    // ── Multi-bot target claiming helpers ─────────────────────────────────────

    /**
     * Claims a resource target for this bot, preventing peer bots from selecting the same block.
     * Claims auto-expire after CLAIM_TTL_MS. Safe to call on every target selection.
     */
    private void claimTarget(BlockPos pos) {
        if (pos == null || pos.equals(BlockPos.ZERO)) return;
        long[] entry = new long[]{ (long) getName().getString().hashCode(), System.currentTimeMillis() + CLAIM_TTL_MS };
        CLAIMED_TARGETS.put(pos, entry);
        System.out.printf("[AMB-CLAIM] %s claimed target %s (active claims=%d)%n",
            getName().getString(), pos, CLAIMED_TARGETS.size());
    }

    /**
     * Releases any claim this bot holds on pos. Called when a goal is abandoned or mined.
     */
    private void releaseTarget(BlockPos pos) {
        if (pos == null || pos.equals(BlockPos.ZERO)) return;
        long[] entry = CLAIMED_TARGETS.get(pos);
        if (entry != null && entry[0] == (long) getName().getString().hashCode()) {
            CLAIMED_TARGETS.remove(pos);
        }
    }

    /**
     * Returns true if pos is currently claimed by a different bot (and the claim hasn't expired).
     */
    private boolean isClaimedByOther(BlockPos pos) {
        long[] entry = CLAIMED_TARGETS.get(pos);
        if (entry == null) return false;
        if (entry[1] < System.currentTimeMillis()) {
            CLAIMED_TARGETS.remove(pos); // expired — clean up
            return false;
        }
        return entry[0] != (long) getName().getString().hashCode();
    }

    // ── Recent-position breadcrumbs (backtrack / local escape) ───────────────
    /**
     * Rolling buffer of the last BREADCRUMB_MAX positions where the bot had ≥2 walkable
     * exits and was not stuck. Used to backtrack to an open space when trapped in a pocket.
     * Most-recent position is at the front of the deque.
     */
    private final java.util.ArrayDeque<BlockPos> recentOpenPositions = new java.util.ArrayDeque<>();
    private static final int BREADCRUMB_MAX = 12;

    // ── Self-placed navigation blocks ────────────────────────────────────────
    /**
     * Tracks positions where the bot placed blocks as temporary navigation aids
     * (via tryPlatformEscape). These must be excluded from harvest target searches
     * to prevent the bot from chasing its own placed blocks as mining targets.
     */
    private final Set<BlockPos> selfPlacedNavigationBlocks = new HashSet<>();

    // ── Progression self-advancement guard ──────────────────────────────────
    // Prevents re-entrant calls to evaluateProgressionTask() when it causes a
    // recursive call to executeCurrentTask().
    private boolean inProgressionEval = false;

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

    // ── Underground base construction system ──────────────────────────────────
    // Phase 0=find spot, 1=digging, 2=placing support, 3=complete/self-repair
    private int baseConstructionPhase = 0;
    private final Queue<BlockPos> baseDigQueue = new LinkedList<>();
    private final List<BlockPos> baseSupportQueue = new ArrayList<>();
    // Tracks blocks we placed (for self-repair): pos → Block
    private final Map<BlockPos, Block> knownStructureBlocks = new HashMap<>();
    private int selfRepairCooldown = 0; // ticks until next self-repair scan

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
    public AmbNpcVisualEntity visualEntity = null;

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

    /**
     * Override swing() to delegate the arm-swing animation to the visual entity.
     * FakePlayer.swing() sends ClientboundAnimatePacket with the FakePlayer's entity ID,
     * but clients only track AmbNpcVisualEntity — that packet is silently ignored.
     * Calling visualEntity.swing() triggers LivingEntity.swing() on the PathfinderMob,
     * which uses the vanilla sendToTrackingPlayers() mechanism to deliver the animate
     * packet to every client that has the visual entity loaded. This is the authoritative
     * path for swing animations in Minecraft — no manual packet construction needed.
     */
    /**
     * 1-arg swing: used by RealisticActions.continueMining() and most callers.
     * Always uses force=true on the visual entity so ClientboundAnimatePacket is
     * guaranteed to be sent every time — the 6-tick swing cycle vs. 4-tick call
     * interval means force=false would silently drop ~half the mining swings.
     */
    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand, false);
        if (visualEntity != null && !visualEntity.isRemoved()) {
            System.out.printf("[AMB-SWING] %s swing(force) → visualEntity id=%d hand=%s%n",
                getName().getString(), visualEntity.getId(), hand);
            visualEntity.swing(hand, true); // force=true: always broadcast animate packet
        }
    }

    @Override
    public void swing(InteractionHand hand, boolean force) {
        super.swing(hand, force);
        if (visualEntity != null && !visualEntity.isRemoved()) {
            visualEntity.swing(hand, force);
        }
    }

    /**
     * Broadcast current mainhand item to all players in this level via equipment packet.
     * Required for FakePlayer — its fake connection never sends equipment updates automatically.
     */
    private void broadcastEquipment() {
        if (!(level() instanceof ServerLevel sl)) return;
        // Fix F: send packet using the visual entity's ID — that is the entity clients actually track.
        // Sending with the FakePlayer's own ID does nothing because FakePlayers are server-only.
        int entityId = (visualEntity != null && !visualEntity.isRemoved())
                       ? visualEntity.getId() : this.getId();
        ItemStack held = getMainHandItem();
        var pkt = new ClientboundSetEquipmentPacket(entityId,
            List.of(Pair.of(EquipmentSlot.MAINHAND, held.isEmpty() ? ItemStack.EMPTY : held.copy())));
        for (ServerPlayer sp : sl.players()) sp.connection.send(pkt);
    }

    /**
     * Equip an item in the mainhand slot and broadcast to nearby players.
     * Uses setItemInHand so the item is visible server-side immediately.
     */
    public void equipToolInHand(net.minecraft.world.item.Item item) {
        // Fix E: Never create items from thin air — find from actual inventory and swap to selected slot.
        // FakePlayer's selected hotbar slot is always 0 (inventory.selected is private and never changed).
        for (int i = 0; i < getInventory().getContainerSize(); i++) {
            ItemStack st = getInventory().getItem(i);
            if (!st.isEmpty() && st.is(item)) {
                if (i != 0) {
                    // Swap: bring the tool to slot 0 (selected/mainhand), move old slot-0 item to slot i
                    ItemStack prev = getInventory().getItem(0);
                    getInventory().setItem(0, st);
                    getInventory().setItem(i, prev);
                }
                broadcastEquipment();
                return;
            }
        }
        // Item not in inventory — do not conjure it from thin air
    }

    /**
     * Equip the default tool for the current task so observers always see the bot
     * holding something appropriate. Called every 100 ticks and on task change.
     */
    public void updateTaskTool() {
        // Don't override if actively mining (equipBestTool will handle that)
        net.minecraft.world.item.Item tool = switch (currentTask == null ? "" : currentTask) {
            case "gather_wood", "chop_trees", "gather_logs" -> Items.WOODEN_AXE;
            case "mine_stone", "mine_ores", "gather_stone", "mine_ore" -> Items.WOODEN_PICKAXE;
            case "mine_dirt", "till_soil", "plant", "farm", "gather_food" -> Items.WOODEN_SHOVEL;
            case "build", "construct"                        -> Items.OAK_PLANKS;
            case "craft"                                     -> Items.CRAFTING_TABLE.asItem();
            case "cook", "smelt"                             -> Items.COAL.asItem();
            case "explore", "scout"                          -> Items.COMPASS.asItem();
            case "build_underground_base"                    -> Items.WOODEN_PICKAXE;
            default                                          -> Items.OAK_LOG;
        };
        // Only set if hand is currently empty — don't override an actively equipped mining tool
        if (getMainHandItem().isEmpty()) {
            equipToolInHand(tool);
        }
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
        if (!java.util.Objects.equals(task, this.currentTask)) {
            System.out.println("[AMB-TASK] " + getName().getString()
                + " task: " + this.currentTask + " → " + task);
        }
        this.currentTask = task;
    }

    public String getCurrentTask() {
        return this.currentTask;
    }

    public void setMoveTarget(net.minecraft.world.phys.Vec3 target, float speed) {
        this.moveTarget = target;
        if (target != null) {
            BlockPos newGoal = new BlockPos((int)target.x, (int)target.y, (int)target.z);
            if (!newGoal.equals(this.currentGoal)) {
                System.out.println("[AMB-NAV] " + getName().getString()
                    + " move target: " + this.currentGoal + " → " + newGoal
                    + " speed=" + String.format("%.2f", speed));
            }
            this.currentGoal = newGoal;
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

        // NOTE: Do NOT send ClientboundPlayerInfoUpdatePacket here.
        // AmbNpcVisualEntity (PathfinderMob) is the sole visible body. Sending a
        // PlayerInfoPacket makes the FakePlayer visible to clients as a second player
        // entity, producing two overlapping Steve bodies. The visual entity handles
        // all rendering: skin, held items (ItemInHandLayer), and swing animation
        // (via the swing() override delegate to visualEntity.swing()).
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

        // Assign randomized skin variant from UUID (UUID is stable after addFreshEntity)
        visual.initSkinFromUUID();

        System.out.printf("[AMB-SPAWN] %s spawned — logicId=%d visualId=%d skinVariant=%d%n",
            name, bot.getId(), visual.getId(), visual.getSkinVariant());

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

        // PASSIVE PICKUP — unconditional, runs regardless of task/mode/health.
        // Pickup intent (keep vs discard) is a post-pickup decision, not a pre-pickup gate.
        doPassivePickup();

        // ENHANCED: BotTicker for physics and human-like movement
        BotTicker.tick(this, currentGoal, movementState);

        // Prioritize exiting interiors each tick (door-based plan first, then structural escape)
        boolean exitingNow = handleInteriorExitPlan();
        if (!exitingNow) {
            exitingNow = escapeHelper.tick(tickCount, currentGoal);
        }

        // Keep the bot's hand visually populated with the task-appropriate tool
        if (tickCount % 100 == 0) {
            updateTaskTool();
        }

        // STALL DIAGNOSIS — compact state snapshot every 40 ticks so the console log
        // makes any post-harvest pause obvious without drowning in per-tick noise.
        if (tickCount % 40 == 0) {
            LocalAwareness awareness = captureLocalAwareness();
            String miningProgress = miningState.isMining
                ? miningState.miningTicks + "/" + miningState.requiredTicks + "@" + miningState.targetBlock
                : "none";
            System.out.printf("[AMB-STATE] %s task=%s substate=%s goal=%s mining=%s pillar=%s "
                + "exitNow=%b exitInterior=%b aStarFail=%d pathRetry=%d seeking=%s stuck=%d "
                + "path=%d/%d awareness=[%s]%n",
                getName().getString(), currentTask,
                exitingNow ? "EXIT" : miningState.isMining ? "MINING" : !currentGoal.equals(BlockPos.ZERO) ? "NAV" : "IDLE",
                currentGoal.equals(BlockPos.ZERO) ? "NONE" : currentGoal,
                miningProgress, pillarPhase,
                exitingNow, exitingInterior,
                aStarFailCount, pathRetryTimer,
                seekingItem != null ? seekingItem.getItem().getHoverName().getString() : "none",
                stuckState.stuckTicks,
                pathIndex, currentPath.size(),
                awareness.summary);

            // Breadcrumb recording: when the bot has ≥2 walkable exits and is not escaping,
            // record this position as a known-open space.  Used by jump-loop bail Option R
            // to retreat to an accessible position when the bot gets pocket-trapped.
            if (!exitingNow && awareness.walkableExitCount >= 2 && noProgressTicks < 20) {
                BlockPos here = blockPosition();
                // Only add if not already the most recent entry (dedup adjacent ticks)
                if (recentOpenPositions.isEmpty() || !recentOpenPositions.peekFirst().equals(here)) {
                    recentOpenPositions.addFirst(here);
                    if (recentOpenPositions.size() > BREADCRUMB_MAX) {
                        recentOpenPositions.pollLast();
                    }
                }
            }
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

        // OPPORTUNISTIC ITEM PICKUP — check for nearby items every second even during active tasks.
        // Auto-pickup handles collection at 4 blocks; this navigates the bot toward items within 12 blocks.
        // Runs even while mining so the bot queues the item — it finishes the current block then detours.
        if (!exitingNow && tickCount % 20 == 0) {
            if (seekingItem != null && seekingItem.isRemoved()) seekingItem = null;
            if (seekingItem == null) seekingItem = findNearestItem(12.0);
            if (seekingItem != null) {
                double itemDist = distanceTo(seekingItem);
                if (itemDist <= 12.0) {
                    // Only redirect navigation when not actively mid-swing on a block
                    if (!miningState.isMining) {
                        BlockPos itemPos = seekingItem.blockPosition();
                        if (!currentGoal.equals(itemPos)) {
                            currentGoal = itemPos;
                            currentPath.clear();
                            pathIndex = 0;
                        }
                    }
                } else {
                    seekingItem = null;
                }
            }
        }

        // REALISTIC MOVEMENT SYSTEM + A* WAYPOINTS
        if (!currentGoal.equals(BlockPos.ZERO)) {

            // Throttle A* retries on failure to avoid re-running 2500 nodes every tick
            if (pathRetryTimer > 0) pathRetryTimer--;

            // Recompute path when: empty and retry timer expired, path exhausted, or stuck.
            // Exception: never trigger a recompute while actively mining within reach — A* cannot
            // path into a solid block, so the path will always appear exhausted during mining.
            boolean activeMiningInRange = miningState.isMining
                    && position().distanceTo(Vec3.atCenterOf(miningState.targetBlock)) < 4.5;
            boolean needNewPath = !activeMiningInRange && (
                    (currentPath.isEmpty() && pathRetryTimer == 0)
                    || (!currentPath.isEmpty() && pathIndex >= currentPath.size())
                    || stuckTimer > 20);
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
                    pathRetryTimer = 60;
                    aStarFailCount++;
                    if (aStarFailCount == 2 && !doorRescueActive && doorPhase == 0 && doorIgnoreTicks == 0) {
                        if (attemptDoorRescue()) {
                            System.out.println("[AMB-NAV] " + getName().getString() + " A* blocked — door rescue initiated toward " + doorPos);
                        }
                    }
                    // At 3rd consecutive A* failure, try scanning for a lateral wall gap as a detour waypoint
                    if (aStarFailCount == 3 && doorPhase == 0) {
                        BlockPos gap = findLateralGap(currentGoal, 8);
                        if (gap != null) {
                            System.out.printf("[AMB-NAV] %s A* blocked — lateral gap found at %s, routing through it%n",
                                getName().getString(), gap);
                            // Route through the gap as a temporary intermediate waypoint
                            currentPath = computeAStarPath(blockPosition(), gap);
                            pathIndex = 0;
                            if (!currentPath.isEmpty()) {
                                pathRetryTimer = 0;
                                // Don't increment aStarFailCount further this attempt
                            }
                        }
                    }
                    if (aStarFailCount >= 5) {
                        // Don't abandon goal if we're actively mining the target (A* can't path to a solid block)
                        if (miningState.isMining &&
                                position().distanceTo(Vec3.atCenterOf(miningState.targetBlock)) < 4.5) {
                            System.out.printf("[AMB-NAV] %s A* blocked but mining in range — keeping goal%n",
                                getName().getString());
                            aStarFailCount = 0;
                        } else {
                            System.out.println("[AMB-NAV] " + getName().getString() + " abandoning unreachable goal " + currentGoal + " after 5 A* failures");
                            releaseTarget(currentGoal);
                            currentGoal = BlockPos.ZERO;
                            currentPath.clear();
                            aStarFailCount = 0;
                            executeCurrentTask();
                            return;
                        }
                    }
                } else {
                    pathRetryTimer = 0;
                    aStarFailCount = 0;
                }
            }

            // Check if pillar mode should activate: bot is XZ-close but goal is above mining reach.
            // ONLY activate when outside the 4.5-block mining range — if we can reach it, just mine.
            if (shouldMineBlock(level().getBlockState(currentGoal))) {
                double distToGoalNow = position().distanceTo(Vec3.atCenterOf(currentGoal));
                if (distToGoalNow >= 4.5) {
                    int heightAbove = currentGoal.getY() - blockPosition().getY();
                    double hDistXZ = Math.sqrt(Math.pow(currentGoal.getX() + 0.5 - getX(), 2)
                                             + Math.pow(currentGoal.getZ() + 0.5 - getZ(), 2));
                    if (heightAbove > 1 && hDistXZ < 2.5 && currentPath.isEmpty()) {
                        // Only pillar when A* has no path — if a navigable route exists, follow it normally.
                        // Fix B: before pillaring, check if there are reachable logs at a lower Y that
                        // A* can navigate to. Pillar is a last resort — only use it when the current
                        // goal is the only remaining log and it genuinely requires vertical climbing.
                        boolean otherLogs = level().getBlockState(currentGoal).is(BlockTags.LOGS) && hasOtherReachableLogs(16);
                        LocalAwareness aw = captureLocalAwareness();
                        System.out.printf("[AMB-PILLAR] %s decision: heightAbove=%d hDist=%.1f pathEmpty=true otherLogs=%b awareness=[%s]%n",
                            getName().getString(), heightAbove, hDistXZ, otherLogs, aw.summary);
                        if (otherLogs) {
                            // Better option exists — re-run task to pick a closer/lower log
                            executeCurrentTask();
                            return;
                        }
                        // If headroom above is blocked, clear it before entering pillar mode.
                        // Leaves and natural terrain: use timed mining (not instant destroyBlock).
                        // Bedrock / non-natural hard block: cannot pillar here — re-target.
                        if (!aw.headroomClear) {
                            BlockPos obs = aw.headroomObstructor;
                            BlockState obsState = level().getBlockState(obs);
                            if (obsState.is(Blocks.BEDROCK)
                                    || (!aw.headroomIsSoft && !isNaturalTerrainBlock(obsState))) {
                                System.out.printf("[AMB-PILLAR] %s headroom blocked by %s (unbreakable) — blacklisting goal, re-targeting%n",
                                    getName().getString(), obsState.getBlock());
                                unreachableGoalBlacklist.put(currentGoal, tickCount + 400);
                                currentGoal = BlockPos.ZERO;
                                executeCurrentTask();
                                return;
                            }
                            // Breakable — mine with proper timing instead of instant destroy
                            if (navBreakCooldown == 0 && !miningState.isMining) {
                                int approxTicks = Math.max(1, (int) Math.ceil(
                                    obsState.getDestroySpeed(level(), obs) * 30.0f));
                                System.out.printf("[AMB-PILLAR] %s clearing headroom %s at %s via timed mining (~%d ticks)%n",
                                    getName().getString(), aw.headroomIsSoft ? "leaf" : "block", obs, approxTicks);
                                RealisticActions.equipBestTool(this, obsState);
                                RealisticActions.startMining(this, obs, miningState);
                                navBreakCooldown = miningState.requiredTicks + 2;
                            }
                            return; // wait for headroom to clear before entering pillar
                        }
                        enterPillarMode(currentGoal);
                        return; // pillar system takes over next tick
                    }
                }
            }

            // MINING REACH CHECK — runs before path-empty return so the bot can mine
            // a block it's already adjacent to even when A* returns an empty path
            // (happens when getWalkableGoal() == bot's current position).
            {
                double distMine = position().distanceTo(Vec3.atCenterOf(currentGoal));
                BlockState mineCheck = level().getBlockState(currentGoal);
                if (shouldMineBlock(mineCheck) && distMine < 4.5) {
                    if (!miningState.isMining) {
                        // Obstruction check: cannot mine through intervening solid blocks.
                        // Find the first solid block on the ray from eye to target.
                        BlockPos startTarget = currentGoal;
                        BlockState startState = mineCheck;
                        BlockPos obs = findSolidObstruction(currentGoal);
                        if (obs != null) {
                            BlockState obsState = level().getBlockState(obs);
                            // ── Obstruction legality — only leaves are auto-cleared. ────────────
                            // Everything else (stone, dirt, player-placed, etc.) requires the bot
                            // to reposition or find a different target. This prevents mining
                            // through walls of any material, including natural stone walls.
                            if (obsState.getDestroySpeed(level(), obs) < 0) {
                                System.out.printf("[AMB-ILLEGAL] %s target %s blocked by unbreakable %s at %s — blacklisting%n",
                                    getName().getString(), currentGoal, obsState.getBlock().getName().getString(), obs);
                                unreachableGoalBlacklist.put(currentGoal, tickCount + 400);
                                currentGoal = BlockPos.ZERO;
                                executeCurrentTask();
                                return;
                            }
                            if (obsState.is(BlockTags.LEAVES)) {
                                // Leaf foliage blocking access — safe to clear (tree canopy)
                                System.out.printf("[AMB-OBSTRUCT] %s target %s blocked by leaves %s at %s — clearing foliage%n",
                                    getName().getString(), currentGoal, obsState.getBlock().getName().getString(), obs);
                                foliageClearTarget = obs;
                                startTarget = obs;
                                startState = obsState;
                            } else if (obsState.is(BlockTags.LOGS) && "gather_wood".equals(currentTask)) {
                                // Log obstructing another log during wood gathering — redirect to
                                // the nearer log (trunk traversal) rather than trying to reach through
                                System.out.printf("[AMB-OBSTRUCT] %s target log %s blocked by log at %s — redirecting to nearer log%n",
                                    getName().getString(), currentGoal, obs);
                                currentGoal = obs;
                                currentPath.clear();
                                pathIndex = 0;
                                foliageClearTarget = BlockPos.ZERO;
                                return;
                            } else {
                                // All other obstructions: natural stone/dirt/gravel or player-placed.
                                // Never auto-clear — this would mine through walls, floors, and structures.
                                System.out.printf("[AMB-ILLEGAL] %s target %s obstructed by %s at %s (%s) — blacklisting, finding new target%n",
                                    getName().getString(), currentGoal,
                                    obsState.getBlock().getName().getString(), obs,
                                    isNaturalTerrainBlock(obsState) ? "natural-wall" : "player-placed");
                                unreachableGoalBlacklist.put(currentGoal, tickCount + 400);
                                currentGoal = BlockPos.ZERO;
                                executeCurrentTask();
                                return;
                            }
                        } else {
                            foliageClearTarget = BlockPos.ZERO;
                        }
                        RealisticActions.equipBestTool(this, startState);
                        RealisticActions.startMining(this, startTarget, miningState);
                    }
                    // Apply gravity but don't navigate — let mining run this tick
                    if (currentPath.isEmpty()) {
                        double dy = getDeltaMovement().y;
                        move(MoverType.SELF, new Vec3(0, dy, 0));
                        double nextDY = onGround() ? -0.08 : Math.max(getDeltaMovement().y - 0.08, -3.5);
                        setDeltaMovement(0, nextDY, 0);
                        return;
                    }
                }
            }

            // Choose waypoint. When A* returned a partial path, follow it.
            // When path is completely empty (start enclosed, no partial either), do NOT fall
            // back to direct movement toward the goal — that sends the bot into walls.
            // Instead, stay still and wait for pathRetryTimer to expire.
            if (currentPath.isEmpty()) {
                // Apply gravity only — stay in place until pathRetryTimer fires.
                double dy = getDeltaMovement().y;
                move(MoverType.SELF, new Vec3(0, dy, 0));
                double nextDY = onGround() ? -0.08 : Math.max(getDeltaMovement().y - 0.08, -3.5);
                setDeltaMovement(0, nextDY, 0);
                handleInteriorExitPlan();
                return;
            }
            BlockPos waypoint = (pathIndex < currentPath.size()) ? currentPath.get(pathIndex) : currentGoal;

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
                // Don't clear door phase here - let handleDoorPlan() manage the full door passage
            } else if (avoidTicks > 0 && moveTarget != null) {
                // Perform strafe to get around obstacle
                avoidTicks--;
                RealisticMovement.strafeAround(this, moveTarget, avoidDir, speed * 0.85f);
                stillMoving = true;
            } else {
                stillMoving = RealisticMovement.moveTowards(this, waypoint, speed);
            }

            // ENHANCED: Use BotTicker for smooth look direction
            BotTicker.updateLookDirection(this, currentGoal, stillMoving);

            // ── Block 2: jump cooldown + horizontal-progress tracking ────────────────
            if (jumpCooldown > 0) jumpCooldown--;
            if (navBreakCooldown > 0) navBreakCooldown--;

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
                } else if (miningState.isMining) {
                    // Bot is intentionally stationary while breaking a block.
                    // Letting noProgressTicks accumulate here would fire a spurious jump
                    // every ~5 ticks even when no navigation obstacle exists.
                    noProgressTicks = 0;
                } else if (horizProgress < 0.05) {
                    noProgressTicks++;
                } else if (horizProgress >= 0.15 && !horizontalCollision) {
                    // Genuine forward movement (not wall-bounce) — reset both stuck counters
                    noProgressTicks = 0;
                    jumpsSinceProgress = 0;
                } else {
                    // Small movement (0.05–0.14) or movement-with-collision (wall bounce).
                    // Reset no-progress so we don't spam jumps, but keep jumpsSinceProgress
                    // so the jump-loop bail fires after repeated unproductive jumps.
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
                if (wpDY >= 1 && wpDY <= 2) {
                    // Obstacle is 1-2 blocks high — a jump + step-up will clear it.
                    // wpDY=2 is common on stairs; always try jumping before breaking anything.
                    shouldJump = true;
                } else {
                    // wpDY=0: horizontal block at same level — break it (jumping won't help).
                    // wpDY>=3: too tall to jump — break the blocking block.
                    tryBreakPathBlock(wpDY);
                }
                hCollTicks = 0;
            } else if (hCollTicks >= 2) {
                // Path is empty (A* failed); don't jump — just reset the counter
                hCollTicks = 0;
            }
            // Condition C: no-progress fallback (wall without collision flag, e.g. fence post)
            if (noProgressTicks >= 5 && !currentPath.isEmpty()) {
                if (wpDY >= 0 && wpDY <= 2) {
                    shouldJump = true;
                } else if (wpDY > 2) {
                    // Waypoint is 3+ blocks above — break path blocks to climb
                    tryBreakPathBlock(wpDY);
                }
                noProgressTicks = 0;
            } else if (noProgressTicks >= 5) {
                noProgressTicks = 0; // Reset even with empty path to prevent stale counter
            }

            if (shouldJump && jumpCooldown == 0) {
                // Before jumping: verify the headroom column (Y+1..Y+3 above bot) and the
                // space above the landing waypoint are free of leaf blocks. Leaves physically
                // block the jump arc even though A* marks them as passable (clearable).
                // Breaking the leaf takes priority over the jump; retry jump next tick.
                BlockPos headLeaf = findLeafInColumnAbove(3);
                if (headLeaf == null && !currentPath.isEmpty() && pathIndex < currentPath.size()) {
                    // Also check above the target landing waypoint (head clearance on arrival)
                    BlockPos landingWp = currentPath.get(pathIndex);
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos chk = landingWp.above(dy);
                        if (level().getBlockState(chk).is(BlockTags.LEAVES)) { headLeaf = chk; break; }
                    }
                }
                if (headLeaf != null && navBreakCooldown == 0 && !miningState.isMining) {
                    // Mine the blocking leaf with proper timing — no instant destroy.
                    // navBreakCooldown = requiredTicks+2 suppresses re-triggering while in progress.
                    BlockState leafState = level().getBlockState(headLeaf);
                    int leafTicks = Math.max(1, (int) Math.ceil(
                        leafState.getDestroySpeed(level(), headLeaf) * 30.0f));
                    System.out.printf("[AMB-LEAF] %s mining jump-path leaf at %s (~%d ticks)%n",
                        getName().getString(), headLeaf, leafTicks);
                    RealisticActions.equipBestTool(this, leafState);
                    RealisticActions.startMining(this, headLeaf, miningState);
                    navBreakCooldown = miningState.requiredTicks + 2;
                } else if (headLeaf == null) {
                    // Headroom is clear — safe to jump
                    double horizDist = 0;
                    if (!currentPath.isEmpty() && pathIndex < currentPath.size()) {
                        BlockPos logWp = currentPath.get(pathIndex);
                        horizDist = Math.sqrt(Math.pow(getX() - (logWp.getX() + 0.5), 2)
                                            + Math.pow(getZ() - (logWp.getZ() + 0.5), 2));
                    }
                    jumpFromGround();
                    jumpCooldown = 15;
                    jumpsSinceProgress++;
                    System.out.printf("[AMB-JUMP] %s jump: hColl=%s waypointDY=%d horizDist=%.2f loopCount=%d%n",
                        getName().getString(), horizontalCollision, wpDY, horizDist, jumpsSinceProgress);
                }
                // else: leaf found but navBreakCooldown > 0 — wait for previous break to clear
            }
            // ─────────────────────────────────────────────────────────────────────────

            // ── Jump-loop bail ────────────────────────────────────────────────────────
            // After JUMP_LOOP_LIMIT unproductive jumps, try practical escape options before
            // blacklisting — a human player would try to walk around or place a block first.
            // Guard: skip when BotEscapeHelper is in control — it manages its own movement.
            if (!exitingNow && jumpsSinceProgress >= JUMP_LOOP_LIMIT && !currentGoal.equals(BlockPos.ZERO)) {
                LocalAwareness baleAw = captureLocalAwareness();
                System.out.printf("[AMB-STUCK] %s jump-loop bail: jumps=%d goal=%s terrain=[%s]%n",
                    getName().getString(), jumpsSinceProgress, currentGoal, baleAw.summary);

                // Option A: safe drop — ground or water within 3 blocks below → just walk forward
                if (baleAw.safeDropBelow && baleAw.dropDepth > 0) {
                    System.out.printf("[AMB-STUCK] %s escape A: safe drop (depth=%d water=%b) — stepping forward%n",
                        getName().getString(), baleAw.dropDepth, baleAw.waterBelow);
                    jumpsSinceProgress = 0;
                    noProgressTicks = 0;
                    // Don't blacklist — let forward motion drop the bot naturally
                } else if (tryPlatformEscape()) {
                    // Option B: placed a block to create a step — reset counter and retry
                    System.out.printf("[AMB-STUCK] %s escape B: platform block placed — resetting jump counter%n",
                        getName().getString());
                    jumpsSinceProgress = 0;
                    noProgressTicks = 0;
                } else if (avoidTicks <= 0) {
                    // Option C: lateral strafe to try a different approach angle
                    avoidTicks = 25;
                    avoidDir = (random.nextBoolean()) ? 1 : -1;
                    moveTarget = Vec3.atCenterOf(currentGoal);
                    jumpsSinceProgress = 0;
                    System.out.printf("[AMB-STUCK] %s escape C: lateral strafe %s for 25 ticks%n",
                        getName().getString(), avoidDir > 0 ? "right" : "left");
                } else {
                    // Option L: pocket leaf — if exits are limited and adjacent leaves exist,
                    // clear one to open a passage.  Common fix when the bot is surrounded by
                    // canopy on all sides and can't jump out.
                    BlockPos adjLeaf = findAdjacentLeaf();
                    if (adjLeaf != null && navBreakCooldown == 0 && baleAw.walkableExitCount < 2) {
                        System.out.printf("[AMB-STUCK] %s escape L: clearing adjacent leaf at %s (exits=%d)%n",
                            getName().getString(), adjLeaf, baleAw.walkableExitCount);
                        RealisticActions.equipBestTool(this, level().getBlockState(adjLeaf));
                        this.gameMode.destroyBlock(adjLeaf);
                        navBreakCooldown = 10;
                        jumpsSinceProgress = 0;
                        noProgressTicks = 0;
                    } else if (!recentOpenPositions.isEmpty()) {
                        // Option R: retreat to the most recent breadcrumb where the bot had
                        // open space.  This un-traps the bot from a pocket by backtracking
                        // along the path it used to enter.
                        BlockPos crumb = recentOpenPositions.peekFirst();
                        System.out.printf("[AMB-STUCK] %s escape R: retreating to breadcrumb %s (crumbs=%d)%n",
                            getName().getString(), crumb, recentOpenPositions.size());
                        // Set the crumb as the new goal rather than overwriting currentGoal directly,
                        // so normal A* navigation drives the retreat.
                        BlockPos originalGoal = currentGoal;
                        currentGoal = crumb;
                        currentPath = computeAStarPath(blockPosition(), crumb);
                        pathIndex = 0;
                        jumpsSinceProgress = 0;
                        noProgressTicks = 0;
                        // After reaching the crumb, executeCurrentTask() will re-choose the real goal.
                        // Blacklist the original goal briefly so it's not immediately re-selected.
                        unreachableGoalBlacklist.put(originalGoal, tickCount + 200);
                    } else {
                        // Option D: nothing worked — blacklist this goal
                        System.out.printf("[AMB-STUCK] %s escape D: all options failed — blacklisting %s for 60s%n",
                            getName().getString(), currentGoal);
                        unreachableGoalBlacklist.put(currentGoal, tickCount + GOAL_BLACKLIST_TICKS);
                        currentGoal = BlockPos.ZERO;
                        currentPath.clear();
                        jumpsSinceProgress = 0;
                        stopMovement();
                        executeCurrentTask();
                        return;
                    }
                }
            }
            // Expire old blacklist entries every 20 seconds
            if (tickCount % 400 == 0 && !unreachableGoalBlacklist.isEmpty()) {
                int now = tickCount;
                unreachableGoalBlacklist.entrySet().removeIf(e -> now >= e.getValue());
            }
            // Clean up self-placed navigation blocks that have been mined/broken already
            if (tickCount % 200 == 0 && !selfPlacedNavigationBlocks.isEmpty()) {
                selfPlacedNavigationBlocks.removeIf(pos -> level().getBlockState(pos).isAir());
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
            // Diagnostic every 5 seconds: exact pos, deltaMovement, collision flags
            if (tickCount % 100 == 0) {
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
            // FIX C: skip while actively mining — position is intentionally static during a break.
            //        Stuck recovery (jump + strafe) would interrupt mining and cancel progress.
            //        stuckState.reset() is called on successful block break above.
            if (!doorRescueActive && !miningState.isMining && StuckDetection.isStuck(this, stuckState, currentGoal)) {
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

            // Advance waypoint: 2D horizontal distance AND vertical within range.
            // wpDy = botY - waypointY. Positive = bot above waypoint (ok). Negative = bot below.
            // Allow at most 0.5 blocks BELOW the waypoint — this prevents prematurely skipping
            // uphill waypoints the bot hasn't physically climbed to yet.  The old limit of -2.0
            // caused chain-skipping (e.g. Y=72 skipped while bot was still at Y=71) which left
            // the bot facing a waypoint 3+ blocks up and triggering block-breaking / head-bobbing.
            double wpDx = this.getX() - (waypoint.getX() + 0.5);
            double wpDz = this.getZ() - (waypoint.getZ() + 0.5);
            double wpDy = this.getY() - waypoint.getY();
            if (wpDx * wpDx + wpDz * wpDz < 1.5 * 1.5 && wpDy > -0.5 && wpDy < 2.0
                    && !currentPath.isEmpty() && pathIndex < currentPath.size()) {
                pathIndex++;
                waypointStuckTicks = 0;
            }

            // Waypoint skip: if the bot has been close to (but unable to reach) the current waypoint
            // for WAYPOINT_STUCK_THRESHOLD ticks, skip it and continue with the next one.
            // CRITICAL: Never skip uphill waypoints (wpVertDiff > 0). The bot must physically climb
            // to each Y level before advancing. Skipping Y=74 while at Y=73 moves the index to Y=75
            // which is 2 blocks up — unreachable with a single jump, causing a permanent freeze.
            double wpHorizDistSq = wpDx * wpDx + wpDz * wpDz;
            double wpVertDiff = waypoint.getY() - this.getY(); // positive = waypoint is above bot
            boolean waypointSkippable = wpVertDiff <= 0.0; // only skip if waypoint is at same level or BELOW bot
            if (wpHorizDistSq < 2.5 * 2.5 && waypointSkippable
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

            // Reached goal check.
            // For mineable targets use 4.5 blocks (player reach distance) so logs directly
            // above or adjacent don't require pillar-climbing — just mine from wherever we stand.
            // For non-mineable navigation goals use 2.5 blocks (arrival at position).
            double distToGoal = position().distanceTo(Vec3.atCenterOf(currentGoal));
            BlockState _preCheck = level().getBlockState(currentGoal);
            double arrivalDist = shouldMineBlock(_preCheck) ? 4.5 : 2.5;
            if (distToGoal < arrivalDist) {
                // REPAIR QUEUE: if the current goal is a repair site, restore the block now
                if (!repairQueue.isEmpty() && currentGoal.equals(repairQueue.get(0).pos)) {
                    EmergencyBreak repair = repairQueue.get(0);
                    BlockState atSite = level().getBlockState(repair.pos);
                    if (atSite.isAir()) {
                        level().setBlock(repair.pos, repair.state, 3);
                        System.out.printf("[AMB-REPAIR] %s restored %s at %s — original reason: %s (remaining=%d)%n",
                            getName().getString(), repair.state.getBlock().getName().getString(),
                            repair.pos, repair.reason, repairQueue.size() - 1);
                    } else {
                        System.out.printf("[AMB-REPAIR] %s site %s already filled — skipping repair%n",
                            getName().getString(), repair.pos);
                    }
                    repairQueue.remove(0);
                    currentGoal = BlockPos.ZERO;
                    if (repairQueue.isEmpty()) {
                        broadcastGroupChat("Done restoring what I broke escaping. Sorry about that!");
                    }
                    return;
                }

                // Check if we should mine the block at goal
                BlockState targetState = level().getBlockState(currentGoal);
                if (shouldMineBlock(targetState)) {
                    // Start mining — with general solid-block obstruction check
                    if (!miningState.isMining) {
                        BlockPos reachTarget = currentGoal;
                        BlockState reachState = targetState;
                        BlockPos obs = findSolidObstruction(currentGoal);
                        if (obs != null) {
                            BlockState obsState = level().getBlockState(obs);
                            // Same leaf-only auto-clear rule as the primary reach check.
                            if (obsState.getDestroySpeed(level(), obs) < 0) {
                                System.out.printf("[AMB-ILLEGAL] %s target %s blocked by unbreakable %s at %s — blacklisting%n",
                                    getName().getString(), currentGoal, obsState.getBlock().getName().getString(), obs);
                                unreachableGoalBlacklist.put(currentGoal, tickCount + 400);
                                currentGoal = BlockPos.ZERO;
                                executeCurrentTask();
                                return;
                            }
                            if (obsState.is(BlockTags.LEAVES)) {
                                System.out.printf("[AMB-OBSTRUCT] %s target %s blocked by leaves at %s — clearing foliage%n",
                                    getName().getString(), currentGoal, obs);
                                foliageClearTarget = obs;
                                reachTarget = obs;
                                reachState = obsState;
                            } else if (obsState.is(BlockTags.LOGS) && "gather_wood".equals(currentTask)) {
                                System.out.printf("[AMB-OBSTRUCT] %s target log %s blocked by log at %s — redirecting to nearer log%n",
                                    getName().getString(), currentGoal, obs);
                                currentGoal = obs;
                                currentPath.clear();
                                pathIndex = 0;
                                foliageClearTarget = BlockPos.ZERO;
                                return;
                            } else {
                                System.out.printf("[AMB-ILLEGAL] %s target %s obstructed by %s at %s (%s) — blacklisting%n",
                                    getName().getString(), currentGoal,
                                    obsState.getBlock().getName().getString(), obs,
                                    isNaturalTerrainBlock(obsState) ? "natural-wall" : "player-placed");
                                unreachableGoalBlacklist.put(currentGoal, tickCount + 400);
                                currentGoal = BlockPos.ZERO;
                                executeCurrentTask();
                                return;
                            }
                        } else {
                            foliageClearTarget = BlockPos.ZERO;
                        }
                        RealisticActions.equipBestTool(this, reachState);
                        RealisticActions.startMining(this, reachTarget, miningState);
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
                    } else if ("craft".equals(currentTask) && !knownCraftingTable.equals(BlockPos.ZERO)
                            && level().getBlockState(knownCraftingTable).is(Blocks.CRAFTING_TABLE)
                            && blockPosition().closerThan(knownCraftingTable, 3.0)) {
                        // Arrived at crafting table — craft immediately (don't wait for 40-tick timer)
                        System.out.printf("[AMB-CRAFT] %s arrived at table — crafting immediately%n",
                            getName().getString());
                        RealisticMovement.lookAt(this, Vec3.atCenterOf(knownCraftingTable));
                        swing(InteractionHand.MAIN_HAND);
                        craftStarterToolsAtTable();
                        currentGoal = BlockPos.ZERO;
                        // Re-evaluate progression now that tools may have been crafted
                        if (!inProgressionEval) {
                            inProgressionEval = true;
                            String nextTask = evaluateProgressionTask();
                            inProgressionEval = false;
                            if (nextTask != null && !"craft".equals(nextTask)) {
                                System.out.printf("[AMB-PROGRESS] %s tools crafted → switching to %s%n",
                                    getName().getString(), nextTask);
                                currentTask = nextTask;
                                executeCurrentTask();
                            }
                        }
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
            // No goal — seek nearby items first, then run task logic.
            if (!exitingNow) {
                // Clear stale seekingItem immediately
                if (seekingItem != null && seekingItem.isRemoved()) seekingItem = null;

                // REPAIR QUEUE takes priority — navigate to restore any emergency-broken blocks
                if (!repairQueue.isEmpty()) {
                    EmergencyBreak nextRepair = repairQueue.get(0);
                    System.out.printf("[AMB-REPAIR] %s navigating to repair site at %s (%d block(s) to restore)%n",
                        getName().getString(), nextRepair.pos, repairQueue.size());
                    currentGoal = nextRepair.pos;
                    currentPath.clear();
                    pathIndex = 0;
                } else if (seekingItem != null) {
                    // Still seeking — keep navigating to the item
                    currentGoal = seekingItem.blockPosition();
                    currentPath.clear();
                    pathIndex = 0;
                } else if (tickCount % 40 == 0) {
                    executeCurrentTask();
                }
            }
        }

        // REALISTIC MINING - Continue mining if in progress.
        // Allow mining even when exitingNow if we're within reach of the target — interior
        // detection can falsely fire inside a tree canopy and must not suppress active harvest.
        boolean miningInRange = miningState.isMining &&
            position().distanceTo(Vec3.atCenterOf(miningState.targetBlock)) < 4.5;
        if (miningState.isMining && exitingNow && !miningInRange) {
            // Interior-exit logic fired while we were out of reach of the target block.
            // Cancel mining and clear the client-side break overlay so it doesn't freeze
            // at whatever stage it had reached when exiting triggered.
            RealisticActions.stopMining(this, miningState);
        } else if (miningState.isMining && (!exitingNow || miningInRange)) {
            BlockPos minedPos = miningState.targetBlock; // save before continueMining resets it
            boolean blockBroken = RealisticActions.continueMining(this, miningState);
            if (blockBroken && !minedPos.equals(BlockPos.ZERO)) {
                // FIX A: Do NOT call collectDropsNear() here.
                // gameMode.destroyBlock() already spawned item entities in the world with a
                // 10-tick pickup delay.  Bypassing that delay made items disappear the same
                // tick they were created — clients never saw them on the ground.
                // doPassivePickup() (called unconditionally at the top of every tick) will
                // collect these items naturally once the delay expires, producing a visible
                // world-drop → fly-to-bot animation.
                System.out.printf("[AMB-HARVEST] %s broke block at %s — drops are world-spawned, awaiting natural pickup (10-tick delay)%n",
                    getName().getString(), minedPos);
                // Release claim so peer bots can now target blocks in this same cluster
                releaseTarget(minedPos);
                // Also clear self-placed navigation tracking if we mined one of our own blocks
                selfPlacedNavigationBlocks.remove(minedPos);
                // Clear stuck counters: a successful break is progress, not a stuck state.
                stuckState.reset();
            }
            if (blockBroken) {
                // If we were clearing an obstructing leaf, do NOT run trunk-following or
                // goal-clearing — currentGoal is still the log.  Just reset foliage state;
                // the mining-start check re-fires next tick and either finds another leaf or
                // starts mining the (now unobstructed) log.
                if (!foliageClearTarget.equals(BlockPos.ZERO)) {
                    System.out.printf("[AMB-OBSTRUCT] %s cleared obstruction at %s — reevaluating access to %s%n",
                        getName().getString(), foliageClearTarget, currentGoal);
                    foliageClearTarget = BlockPos.ZERO;
                    if (pillarPhase == PillarPhase.MINING) {
                        miningState.isMining = false; // let pillar system continue
                    }
                    // else: leave miningState reset (done by continueMining); next tick restarts
                } else if (pillarPhase == PillarPhase.MINING) {
                    // Pillar system will detect block gone next tick and find the next one
                    miningState.isMining = false;
                } else {
                    // For underground base: pop the dug block from the dig queue
                    if ("build_underground_base".equals(currentTask)
                            && !baseDigQueue.isEmpty()
                            && currentGoal.equals(baseDigQueue.peek())) {
                        baseDigQueue.poll();
                        if (baseDigQueue.isEmpty() && baseConstructionPhase == 1) {
                            baseConstructionPhase = 2;
                            broadcastGroupChat("Digging done! Placing supports...");
                        }
                    }
                    // For gather_wood: continue up the same trunk before searching globally.
                    // Check 1-5 blocks above the mined log for another log in the same tree.
                    if ("gather_wood".equals(currentTask)) {
                        BlockPos nextLog = null;
                        for (int up = 1; up <= 5; up++) {
                            BlockPos above = currentGoal.above(up);
                            if (level().getBlockState(above).is(BlockTags.LOGS)) {
                                nextLog = above;
                                break;
                            }
                            // Stop searching if we hit a non-log, non-air block (different tree)
                            if (level().getBlockState(above).canOcclude()) break;
                        }
                        if (nextLog != null) {
                            // Height limit: don't follow trunk if the next log is more than 4 blocks
                            // above the bot's current feet position.  A log that high requires pillar
                            // climbing and the bot may already be in a canopy pocket — better to search
                            // for a reachable ground-level log instead.
                            int nextLogAboveBot = nextLog.getY() - blockPosition().getY();
                            if (nextLogAboveBot > 4) {
                                System.out.printf("[AMB-WOOD] %s trunk follow: next log at %s is %d blocks up — height limit, searching new tree%n",
                                    getName().getString(), nextLog, nextLogAboveBot);
                                currentGoal = BlockPos.ZERO;
                                executeCurrentTask();
                            } else {
                                System.out.printf("[AMB-WOOD] %s trunk follow: next log at %s (was %s, +%d Y)%n",
                                    getName().getString(), nextLog, currentGoal, nextLogAboveBot);
                                currentGoal = nextLog;
                                currentPath.clear();
                                pathIndex = 0;
                            }
                        } else {
                            System.out.printf("[AMB-WOOD] %s trunk exhausted at %s — searching for new tree%n",
                                getName().getString(), currentGoal);
                            currentGoal = BlockPos.ZERO;
                            executeCurrentTask();
                        }
                    } else {
                        // Normal mining complete - find next goal
                        currentGoal = BlockPos.ZERO;
                        executeCurrentTask();
                    }
                }
            }
        }

        // SELF-REPAIR - Periodically scan for damaged structure blocks
        if (selfRepairCooldown > 0) {
            selfRepairCooldown--;
        } else if (!knownStructureBlocks.isEmpty()) {
            selfRepairCooldown = 400;
            int damaged = 0;
            for (Map.Entry<BlockPos, Block> entry : knownStructureBlocks.entrySet()) {
                BlockState actual = level().getBlockState(entry.getKey());
                if (actual.isAir() && !baseSupportQueue.contains(entry.getKey())) {
                    baseSupportQueue.add(0, entry.getKey()); // high priority: front of queue
                    damaged++;
                }
            }
            if (damaged > 0) {
                broadcastGroupChat("Detected " + damaged + " damaged block(s) — repairing!");
                if (!"build_underground_base".equals(currentTask)) {
                    setTask("build_underground_base");
                    baseConstructionPhase = 2; // jump straight to placement phase
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

        // (Passive pickup moved to doPassivePickup() — called unconditionally at top of runAllPlayerActions)

        // 2x2 auto-crafting (planks from logs, sticks) — run every 20 ticks so materials
        // are ready quickly when the bot switches to the craft task.
        if (tickCount % 20 == 0) {
            tryAutoCraftBasics();
        }
        // Station management (table placement, tool crafting) ONLY during craft tasks.
        // Running this during gather_wood/mine_stone sets knownCraftingTable as currentGoal on
        // the brief zero-goal tick between log harvests, hijacking the gathering task.
        // 3x3 tool recipes require a crafting table — enforce that rule here by only running
        // station logic when the task explicitly calls for it.
        if (tickCount % 40 == 0 && !exitingNow && !escapeHelper.isActive()
                && ("craft".equals(currentTask) || "place_crafting_table".equals(currentTask))) {
            manageStationsAndCrafting();
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

        // Detect if truly indoors — must have BOTH no sky above AND solid walls around us.
        // A simple canSeeSky check gives false positives under leaf canopies (outdoors under a tree).
        // We only consider it "indoors" if there are solid (structure) blocks in 3+ cardinal directions.
        boolean noSkyAbove = true;
        for (int i = 0; i < 5; i++) {
            if (level().canSeeSky(blockPosition().above(i))) { noSkyAbove = false; break; }
        }
        // Count solid walls within 3 blocks in cardinal directions (leaves/logs don't count — only opaque non-natural blocks)
        int wallCount = 0;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            for (int dist = 1; dist <= 3; dist++) {
                BlockState bs = level().getBlockState(blockPosition().relative(d, dist));
                if (bs.canOcclude() && !isNaturalTerrainBlock(bs)) { wallCount++; break; }
            }
        }
        boolean indoors = noSkyAbove && wallCount >= 2;

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
                doorRescueActive = false;
                exitCooldown = 600; // 30s cooldown so we don't immediately re-trigger
                aStarFailCount = 0; // Reset fail count so interior detection doesn't instantly re-fire
                currentGoal = BlockPos.ZERO; // Clear blocked goal so bot picks a fresh one
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

    // ==================== LOCAL AWARENESS SNAPSHOT ====================
    /**
     * Lightweight snapshot of nearby world state consumed by movement, mining, and recovery.
     * Generated on-demand; shared across all major decision points so each subsystem
     * does not build its own independent local scan.
     */
    static class LocalAwareness {
        final boolean headroomClear;       // Y+1..Y+3 above bot are all passable
        final BlockPos headroomObstructor; // first blocking block in Y+1..Y+3, or null
        final boolean headroomIsSoft;      // obstructor is leaves (breakable, no tool needed)
        final int nearbyLogCount;          // logs within 8-block radius
        final int nearbyLeafCount;         // leaf blocks within 4-block radius
        final boolean toolSuitable;        // mainhand tool matches current task
        final boolean craftingTableNear;   // crafting table within 12 blocks
        final BlockPos nearestTable;       // position of nearest table, or null
        final boolean chestNear;           // chest within 12 blocks
        final int droppedItemCount;        // dropped item entities within 8 blocks
        final String miningProgress;       // "ticks/required@pos" or "none"
        // ── Terrain / risk fields ────────────────────────────────────────────────
        final boolean safeDropBelow;  // solid or water landing within 3 blocks below
        final boolean waterBelow;     // water detected within drop zone
        final int dropDepth;          // air blocks below bot before landing (0 = on ground)
        final int walkableExitCount;  // how many of 4 cardinal directions are walkable at same Y
        final String summary;

        LocalAwareness(boolean headroomClear, BlockPos headroomObstructor, boolean headroomIsSoft,
                       int nearbyLogCount, int nearbyLeafCount, boolean toolSuitable,
                       boolean craftingTableNear, BlockPos nearestTable, boolean chestNear,
                       int droppedItemCount, String miningProgress,
                       boolean safeDropBelow, boolean waterBelow, int dropDepth, int walkableExitCount) {
            this.headroomClear      = headroomClear;
            this.headroomObstructor = headroomObstructor;
            this.headroomIsSoft     = headroomIsSoft;
            this.nearbyLogCount     = nearbyLogCount;
            this.nearbyLeafCount    = nearbyLeafCount;
            this.toolSuitable       = toolSuitable;
            this.craftingTableNear  = craftingTableNear;
            this.nearestTable       = nearestTable;
            this.chestNear          = chestNear;
            this.droppedItemCount   = droppedItemCount;
            this.miningProgress     = miningProgress;
            this.safeDropBelow      = safeDropBelow;
            this.waterBelow         = waterBelow;
            this.dropDepth          = dropDepth;
            this.walkableExitCount  = walkableExitCount;
            this.summary = String.format(
                "headClear=%b obstructor=%s soft=%b logs=%d leaves=%d toolOK=%b table=%s chest=%b drops=%d mining=%s drop=%d water=%b exits=%d",
                headroomClear, headroomObstructor, headroomIsSoft,
                nearbyLogCount, nearbyLeafCount, toolSuitable,
                nearestTable != null ? nearestTable.toString() : "none",
                chestNear, droppedItemCount, miningProgress,
                dropDepth, waterBelow, walkableExitCount);
        }
    }

    /**
     * Capture a LocalAwareness snapshot. Cheap: only checks 4-12 block radius.
     * Called every 40 ticks and at decision branch points (pillar entry, stuck recovery).
     */
    private LocalAwareness captureLocalAwareness() {
        // ── Headroom ──────────────────────────────────────────────────────────
        BlockPos obstructor = null;
        boolean isSoft = false;
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos check = blockPosition().above(dy);
            BlockState bs = level().getBlockState(check);
            boolean physSolid = bs.canOcclude() || bs.is(BlockTags.LEAVES);
            if (!bs.isAir() && physSolid) {
                obstructor = check;
                isSoft = bs.is(BlockTags.LEAVES);
                break;
            }
        }

        // ── Nearby block counts + interactables ───────────────────────────────
        int logs = 0, leaves = 0;
        BlockPos nearestTable = null;
        double nearestTableDist = Double.MAX_VALUE;
        boolean chestNear = false;
        for (int dx = -12; dx <= 12; dx++) {
            for (int dy = -2; dy <= 8; dy++) {
                for (int dz = -12; dz <= 12; dz++) {
                    BlockPos p = blockPosition().offset(dx, dy, dz);
                    BlockState bs = level().getBlockState(p);
                    if (bs.is(BlockTags.LOGS) && Math.abs(dx) <= 8 && Math.abs(dz) <= 8) logs++;
                    else if (bs.is(BlockTags.LEAVES) && Math.abs(dx) <= 4 && Math.abs(dz) <= 4) leaves++;
                    else if (bs.is(Blocks.CRAFTING_TABLE)) {
                        double d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 < nearestTableDist) { nearestTableDist = d2; nearestTable = p; }
                    } else if (bs.getBlock() instanceof ChestBlock) {
                        if (Math.abs(dx) <= 12 && Math.abs(dz) <= 12) chestNear = true;
                    }
                }
            }
        }

        // ── Dropped item entities in 8-block radius ───────────────────────────
        int drops = level().getEntitiesOfClass(ItemEntity.class,
            new AABB(blockPosition()).inflate(8)).size();

        // ── Tool suitability ─────────────────────────────────────────────────
        ItemStack held = getMainHandItem();
        boolean toolOK;
        if (held.isEmpty()) {
            toolOK = false;
        } else {
            String itemName = held.getItem().toString();
            toolOK = switch (currentTask == null ? "" : currentTask) {
                case "gather_wood", "chop_trees" -> itemName.contains("axe");
                case "mine_stone", "mine_ore"   -> itemName.contains("pickaxe");
                case "mine_dirt"                 -> itemName.contains("shovel");
                default                          -> true;
            };
        }

        // ── Mining progress string ─────────────────────────────────────────────
        String miningProg = miningState.isMining
            ? miningState.miningTicks + "/" + miningState.requiredTicks + "@" + miningState.targetBlock
            : "none";

        // ── Safe-drop / water detection ────────────────────────────────────────
        // Scan downward from the bot's feet to find the first solid landing.
        int dropDepth = 0;
        boolean waterBelow = false;
        for (int d = 1; d <= 10; d++) {
            BlockPos dCheck = blockPosition().below(d);
            BlockState dState = level().getBlockState(dCheck);
            if (dState.is(Blocks.WATER)) { waterBelow = true; dropDepth = d; break; }
            if (dState.canOcclude()) { dropDepth = d - 1; break; } // landed at d-1 air blocks
            dropDepth = d;
        }
        boolean safeDropBelow = waterBelow || dropDepth <= 3;

        // ── Walkable exits in the 4 cardinal directions ────────────────────────
        int walkExits = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adj = blockPosition().relative(dir);
            BlockState adjFeet = level().getBlockState(adj);
            BlockState adjHead = level().getBlockState(adj.above());
            BlockState adjFloor = level().getBlockState(adj.below());
            boolean adjPassable = !adjFeet.canOcclude() && !adjHead.canOcclude()
                    && (adjFloor.canOcclude() || adjFloor.is(Blocks.WATER));
            if (adjPassable) walkExits++;
        }

        return new LocalAwareness(obstructor == null, obstructor, isSoft, logs, leaves, toolOK,
            nearestTable != null, nearestTable, chestNear, drops, miningProg,
            safeDropBelow, waterBelow, dropDepth, walkExits);
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

        // Increase budget for complex terrain / structures requiring detour
        int maxNodes = 16000;
        int expanded = 0;

        // Best partial path tracking: record the explored node closest to the goal.
        // When the full path cannot be found (obstacle, node budget exhausted),
        // return the partial path to that node. The bot follows it toward the goal,
        // then re-runs A* from the new position — same approach used by Baritone / Mineflayer.
        BlockPos bestPartialNode = start;
        int bestPartialH = heuristic(start, walkableGoal);

        while (!open.isEmpty() && expanded < maxNodes) {
            Node current = open.poll();
            expanded++;

            // Track the closest-to-goal explored node for partial path fallback
            int h = heuristic(current.pos, walkableGoal);
            if (h < bestPartialH) {
                bestPartialH = h;
                bestPartialNode = current.pos;
            }

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

        // Full path not found — try partial path to best explored node so far.
        // This ensures the bot always makes progress toward the goal even when the
        // complete path can't be computed in one shot (large detour, complex terrain).
        if (!bestPartialNode.equals(start)) {
            List<BlockPos> partial = reconstructPath(cameFrom, bestPartialNode);
            if (!partial.isEmpty()) {
                System.out.println("[AMB-PATH] A* partial path (" + expanded + " nodes): "
                    + partial.size() + " waypoints, reached " + bestPartialNode
                    + " (heuristic " + bestPartialH + " from goal " + walkableGoal + ")");
                return partial;
            }
        }

        // Truly no path (start is enclosed or completely unreachable)
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
                    && isWalkableFloor(level().getBlockState(n))) { // stairs/slabs are climbable too
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

    /**
     * Returns true if the block can serve as a walkable floor (bot can stand on top of it).
     * canOcclude() is false for stairs/slabs/etc. but they are perfectly walkable surfaces.
     * isSolid() is deprecated in 1.21 but has no single-argument replacement; suppress the warning.
     */
    @SuppressWarnings("deprecation")
    private boolean isWalkableFloor(BlockState state) {
        if (state.isAir()) return false;
        if (state.getBlock() instanceof DoorBlock) return false;
        if (state.getBlock() instanceof FenceGateBlock) return false;
        if (state.is(Blocks.WATER)) return true;
        // Full opaque blocks
        if (state.canOcclude()) return true;
        // Partial-solid surfaces the player can stand on
        return state.getBlock() instanceof StairBlock
            || state.getBlock() instanceof SlabBlock
            || state.isSolid();
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

        // Must have walkable floor below — includes stairs, slabs, and other partial surfaces
        boolean solidGround = isWalkableFloor(below);

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
                // Logs are the primary target; leaves are cleared to reach logs (instant by hand)
                return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
            case "mine_stone":
                return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) ||
                       state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE);
            case "mine_ore":
                return state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE) ||
                       state.is(Blocks.COPPER_ORE) || state.is(Blocks.GOLD_ORE) ||
                       state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.EMERALD_ORE) ||
                       state.is(Blocks.DEEPSLATE_COAL_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE) ||
                       state.is(Blocks.DEEPSLATE_COPPER_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) ||
                       state.is(Blocks.DEEPSLATE_DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE);
            // FIX D: mine_dirt — newly implemented
            case "mine_dirt":
                return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) ||
                       state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) ||
                       state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) ||
                       state.is(Blocks.RED_SAND);
            case "build_underground_base":
                // Mine any solid non-bedrock block that is the current dig target
                if (!baseDigQueue.isEmpty() && state.canOcclude() && !state.is(Blocks.BEDROCK)) {
                    return currentGoal.equals(baseDigQueue.peek());
                }
                return false;
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
            case "mine_dirt":
                // Shovel is fastest for dirt/gravel/sand, but hand also works
                if (getInventory().countItem(Items.DIAMOND_SHOVEL) > 0) {
                    equipToolInHand(Items.DIAMOND_SHOVEL);
                } else if (getInventory().countItem(Items.IRON_SHOVEL) > 0) {
                    equipToolInHand(Items.IRON_SHOVEL);
                } else if (getInventory().countItem(Items.STONE_SHOVEL) > 0) {
                    equipToolInHand(Items.STONE_SHOVEL);
                } else if (getInventory().countItem(Items.WOODEN_SHOVEL) > 0) {
                    equipToolInHand(Items.WOODEN_SHOVEL);
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
        // All recipes below are 2x2 (inventory grid) — no crafting table required.
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
        // Reserve at least 10 planks for crafting table (4) + tools (3+3).
        // Only make sticks from surplus planks to avoid depleting the crafting supply.
        if (sticks < 16 && planks > 10) {
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
            // Process ALL logs of this type at once — one-at-a-time was too slow (100-tick timer)
            int removed = removeItems(log, logs);
            if (removed > 0) {
                int produced = 4 * removed;
                addToInventory(new ItemStack(planks, produced));
                System.out.printf("[AMB-CRAFT] %s 2x2 craft: %dx%s → %dx%s (no table needed)%n",
                    getName().getString(), removed,
                    log.getDescriptionId(), produced, planks.getDescriptionId());
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
        // Track whether the table was already known before ensureCraftingTableAvailable runs.
        // If the table is placed THIS call, the bot hasn't navigated to it yet — skip crafting.
        boolean tablePreviouslyKnown = !knownCraftingTable.equals(BlockPos.ZERO);

        System.out.printf("[AMB-STATION] %s manageStations: task=%s hasGoal=%b tableKnown=%b tablePos=%s%n",
            getName().getString(), currentTask, hasActiveGoal, tablePreviouslyKnown, knownCraftingTable);

        ensureCraftingTableAvailable(hasActiveGoal);
        boolean tableJustPlaced = !tablePreviouslyKnown && !knownCraftingTable.equals(BlockPos.ZERO);
        // Simple starter tool progression at table (wood tier).
        // Guard: skip if table was just placed this tick (bot hasn't walked there yet).
        if (!knownCraftingTable.equals(BlockPos.ZERO) && !tableJustPlaced
                && this.blockPosition().closerThan(knownCraftingTable, 2.0)) {
            System.out.printf("[AMB-STATION] %s AT crafting table %s — interacting and crafting tools%n",
                getName().getString(), knownCraftingTable);
            // Look at and "right-click" the table so the interaction is visible to players
            RealisticMovement.lookAt(this, Vec3.atCenterOf(knownCraftingTable));
            swing(InteractionHand.MAIN_HAND);
            craftStarterToolsAtTable();
            // Do NOT pick up the table immediately after crafting — leave it in place so
            // the player can see the bot used a physical crafting table.
        } else if (!knownCraftingTable.equals(BlockPos.ZERO) && !tableJustPlaced) {
            double tableDist = blockPosition().distSqr(knownCraftingTable);
            System.out.printf("[AMB-STATION] %s table at %s, distance=%.1f — navigating (hasGoal=%b)%n",
                getName().getString(), knownCraftingTable, Math.sqrt(tableDist), hasActiveGoal);
        }

        // Chest lifecycle: place if in inventory, deposit excess if near one
        manageCraftedChest();

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

    // ==================== CHEST / STORAGE ====================

    /**
     * Chest lifecycle manager — called from manageStationsAndCrafting() every 100 ticks.
     *
     * Phase A: If we have a chest item in inventory and no known chest location, place it.
     * Phase B: Purge known chest list of entries whose block was removed by other means.
     * Phase C: If bot is adjacent to a known chest, deposit excess materials.
     */
    private void manageCraftedChest() {
        // Phase A: Place a chest we're carrying
        if (knownChests.isEmpty() && getInventory().countItem(Items.CHEST) > 0) {
            BlockPos place = findPlacementNear(blockPosition(), 4);
            if (place != null && removeItems(Items.CHEST, 1) == 1) {
                level().setBlock(place, Blocks.CHEST.defaultBlockState(), 3);
                knownChests.add(place);
                broadcastGroupChat("Placed a chest for storage at " + place + ".");
                System.out.printf("[AMB-STORAGE] %s placed chest at %s%n", getName().getString(), place);
            }
            return;
        }

        // Phase B: Validate known chest positions
        knownChests.removeIf(pos -> !level().getBlockState(pos).is(Blocks.CHEST));

        // Phase C: Deposit excess items when adjacent to a chest
        if (!knownChests.isEmpty()) {
            BlockPos chestPos = knownChests.get(0);
            if (blockPosition().closerThan(chestPos, 3.0)) {
                depositExcessItems(chestPos);
            }
        }
    }

    /**
     * Deposit items that exceed the bot's "keep" threshold into the given chest.
     * Keeps a working supply of wood and stone; stores surpluses.
     */
    private void depositExcessItems(BlockPos chestPos) {
        BlockEntity be = level().getBlockEntity(chestPos);
        if (!(be instanceof ChestBlockEntity chest)) return;

        // Deposit excess logs (keep 16 for planks + crafting)
        Item[] logTypes = {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG,
            Items.ACACIA_LOG, Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG, Items.BAMBOO_BLOCK};
        for (Item log : logTypes) depositItemExcess(chest, log, 16);

        // Deposit excess cobblestone (keep 32 for crafting)
        depositItemExcess(chest, Items.COBBLESTONE, 32);

        // Deposit extra tools (keep 1 of each kind)
        depositItemExcess(chest, Items.WOODEN_PICKAXE, 1);
        depositItemExcess(chest, Items.WOODEN_AXE, 1);
        depositItemExcess(chest, Items.STONE_PICKAXE, 1);
        depositItemExcess(chest, Items.STONE_AXE, 1);

        System.out.printf("[AMB-STORAGE] %s deposited excess items into chest at %s%n",
            getName().getString(), chestPos);
    }

    /** Move items in excess of keepCount from bot inventory into the given chest. */
    private void depositItemExcess(ChestBlockEntity chest, Item item, int keepCount) {
        int have = countItemInInventory(item);
        if (have <= keepCount) return;
        int toDeposit = have - keepCount;
        for (int i = 0; i < chest.getContainerSize() && toDeposit > 0; i++) {
            ItemStack slot = chest.getItem(i);
            if (slot.isEmpty()) {
                int amount = Math.min(toDeposit, item.getDefaultMaxStackSize());
                chest.setItem(i, new ItemStack(item, amount));
                removeItems(item, amount);
                toDeposit -= amount;
            } else if (slot.is(item) && slot.getCount() < slot.getMaxStackSize()) {
                int space = slot.getMaxStackSize() - slot.getCount();
                int amount = Math.min(toDeposit, space);
                slot.grow(amount);
                chest.setItem(i, slot);
                removeItems(item, amount);
                toDeposit -= amount;
            }
        }
    }

    private void ensureCraftingTableAvailable(boolean hasActiveGoal) {
        // If we know one and it's loaded, done
        if (!knownCraftingTable.equals(BlockPos.ZERO)) {
            if (!level().getBlockState(knownCraftingTable).is(Blocks.CRAFTING_TABLE)) {
                System.out.printf("[AMB-STATION] %s known table at %s is GONE — clearing%n",
                    getName().getString(), knownCraftingTable);
                knownCraftingTable = BlockPos.ZERO;
            }
        }

        if (knownCraftingTable.equals(BlockPos.ZERO)) {
            // Search up to 24 blocks horizontally, ±5 vertically — catches tables placed by
            // players anywhere on the same floor or on nearby elevated surfaces.
            System.out.printf("[AMB-STATION] %s searching for crafting table (radius=24, dy=-5..+8)...%n",
                getName().getString());
            BlockPos found = findNearestTableWide();
            if (found != null) {
                knownCraftingTable = found;
                System.out.printf("[AMB-STATION] %s found existing crafting table at %s (dist=%.1f)%n",
                    getName().getString(), found,
                    Math.sqrt(blockPosition().distSqr(found)));
                craftStallCount = 0;
                return;
            }
            System.out.printf("[AMB-STATION] %s no crafting table found in range%n", getName().getString());

            int planks = countTotalPlanks();
            int tableInInv = getInventory().countItem(Blocks.CRAFTING_TABLE.asItem());
            System.out.printf("[AMB-STATION] %s no table in world — planks=%d tableInInv=%d%n",
                getName().getString(), planks, tableInInv);

            // No table placed: craft one then place it nearby.
            // Require 16 planks before crafting the table — ensures enough material remains
            // for wooden tools (pick=3, axe=3, sticks=2 planks) after the table takes 4.
            if (planks >= 16) {
                if (removeAnyPlanks(4) == 4) {
                    addToInventory(new ItemStack(Blocks.CRAFTING_TABLE.asItem(), 1));
                    System.out.printf("[AMB-STATION] %s crafted 1 crafting table from planks (had %d planks)%n",
                        getName().getString(), planks);
                    broadcastGroupChat("Crafted a crafting table.");
                }
            } else {
                System.out.printf("[AMB-STATION] %s waiting for more planks to craft table — have %d, need 16%n",
                    getName().getString(), planks);
            }

            if (getInventory().countItem(Blocks.CRAFTING_TABLE.asItem()) > 0) {
                // Place table 5-8 blocks away so the bot must visibly walk to it before crafting.
                // The craft threshold is closerThan(2.0), so 5+ blocks guarantees movement.
                BlockPos place = findPlacementAway(5, 8);
                // Fallback 1: within 6 blocks (cramped terrain)
                if (place == null) place = findPlacementNear(blockPosition(), 6);
                // Fallback 2: directly at feet+1 in the facing direction (last resort, guaranteed placement)
                if (place == null) {
                    BlockPos adj = blockPosition().relative(getDirection());
                    if (level().getBlockState(adj).isAir() && level().getBlockState(adj.below()).canOcclude()) {
                        place = adj;
                    }
                }
                if (place != null) {
                    if (removeItems(Blocks.CRAFTING_TABLE.asItem(), 1) == 1) {
                        level().setBlock(place, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
                        knownCraftingTable = place;
                        craftingTableSelfPlaced = true; // remember we placed this so we can pick it up later
                        craftStallCount = 0;
                        System.out.printf("[AMB-STATION] %s placed crafting table at %s (stall reset)%n",
                            getName().getString(), place);
                        broadcastGroupChat("Placed a crafting table at " + place + ".");
                    }
                } else {
                    System.out.printf("[AMB-STATION] %s has table in inv but found no valid placement spot — stall=%d%n",
                        getName().getString(), craftStallCount);
                }
            } else {
                // Only redirect to placement area if bot has no active goal
                if (!hasActiveGoal && planks >= 4) {
                    BlockPos target = blockPosition().offset(2, 0, 2);
                    this.currentGoal = target;
                } else {
                    System.out.printf("[AMB-STATION] %s BLOCKED — planks=%d tableInInv=%d hasGoal=%b (need 4 planks)%n",
                        getName().getString(), planks, tableInInv, hasActiveGoal);
                }
            }
        } else {
            // Only move toward table if bot has no active goal and intends to craft
            if (!hasActiveGoal && !this.blockPosition().closerThan(knownCraftingTable, 1.5)) {
                this.currentGoal = knownCraftingTable;
                System.out.printf("[AMB-STATION] %s navigating to table at %s%n",
                    getName().getString(), knownCraftingTable);
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

    /** Find a flat, open spot at least minDist blocks away (up to maxDist) so the bot must walk there. */
    private BlockPos findPlacementAway(int minDist, int maxDist) {
        BlockPos c = blockPosition();
        for (int r = minDist; r <= maxDist; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) < r - 1 && Math.abs(dz) < r - 1) continue; // Only the ring at distance r
                    BlockPos p = c.offset(dx, 0, dz);
                    if (level().getBlockState(p).isAir() && level().getBlockState(p.below()).canOcclude()) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Wide-radius crafting-table search: scans 24 blocks horizontally and -5..+8 vertically.
     * Used by ensureCraftingTableAvailable() so player-placed tables on elevated surfaces or
     * slightly different floor levels are always detected before the bot crafts its own.
     */
    private BlockPos findNearestTableWide() {
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;
        BlockPos c = blockPosition();
        for (int dx = -24; dx <= 24; dx++) {
            for (int dy = -5; dy <= 8; dy++) {
                for (int dz = -24; dz <= 24; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (level().getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
                        double d2 = p.distSqr(c);
                        if (d2 < bestD2) { bestD2 = d2; best = p; }
                    }
                }
            }
        }
        return best;
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
        // Guard: must be adjacent to a real crafting table in the world (enforced by caller)
        String botName = getName().getString();
        System.out.printf("[AMB-CRAFT] %s table craft attempt at %s — planks=%d sticks=%d cobble=%d woodPick=%d stonePick=%d%n",
            botName, knownCraftingTable, countTotalPlanks(), countItemInInventory(Items.STICK),
            countItemInInventory(Items.COBBLESTONE),
            getInventory().countItem(Items.WOODEN_PICKAXE),
            getInventory().countItem(Items.STONE_PICKAXE));

        int sticks = countItemInInventory(Items.STICK);
        // Sticks are a 2x2 recipe but also fine to craft at table for convenience
        if (sticks < 2 && countTotalPlanks() >= 2) {
            if (removeAnyPlanks(2) == 2) {
                addToInventory(new ItemStack(Items.STICK, 4));
                broadcastGroupChat("Crafted 4 sticks at the table.");
                System.out.printf("[AMB-CRAFT] %s table craft: 2 planks → 4 sticks%n", botName);
            }
        }

        // Wooden pickaxe: 3 planks + 2 sticks (3x3 recipe — table required) ✓
        if (getInventory().countItem(Items.WOODEN_PICKAXE) == 0 && countTotalPlanks() >= 3 && countItemInInventory(Items.STICK) >= 2) {
            System.out.printf("[AMB-CRAFT] %s table craft: 3x3 wooden_pickaxe (planks=%d sticks=%d)%n",
                botName, countTotalPlanks(), countItemInInventory(Items.STICK));
            if (removeAnyPlanks(3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.WOODEN_PICKAXE, 1));
                broadcastGroupChat("Crafted a wooden pickaxe.");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: wooden_pickaxe%n", botName);
            }
        }

        // Wooden axe (3x3 recipe — table required) ✓
        if (getInventory().countItem(Items.WOODEN_AXE) == 0 && countTotalPlanks() >= 3 && countItemInInventory(Items.STICK) >= 2) {
            System.out.printf("[AMB-CRAFT] %s table craft: 3x3 wooden_axe%n", botName);
            if (removeAnyPlanks(3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.WOODEN_AXE, 1));
                broadcastGroupChat("Crafted a wooden axe.");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: wooden_axe%n", botName);
            }
        }

        // Wooden sword (3x3 recipe — table required) ✓
        if (getInventory().countItem(Items.WOODEN_SWORD) == 0 && countTotalPlanks() >= 2 && countItemInInventory(Items.STICK) >= 1) {
            System.out.printf("[AMB-CRAFT] %s table craft: 3x3 wooden_sword%n", botName);
            if (removeAnyPlanks(2) == 2 && removeItems(Items.STICK, 1) == 1) {
                addToInventory(new ItemStack(Items.WOODEN_SWORD, 1));
                broadcastGroupChat("Crafted a wooden sword.");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: wooden_sword%n", botName);
            }
        }

        // === STONE TIER — upgrade tools when cobblestone is available ===
        int cobble = countItemInInventory(Items.COBBLESTONE);
        sticks = countItemInInventory(Items.STICK); // re-read — may have just been crafted

        // Extra sticks for stone tools if needed
        if (sticks < 4 && countTotalPlanks() >= 2) {
            if (removeAnyPlanks(2) == 2) {
                addToInventory(new ItemStack(Items.STICK, 4));
                sticks += 4;
                System.out.printf("[AMB-CRAFT] %s table craft: extra sticks for stone tools%n", botName);
            }
        }

        // Stone pickaxe: 3 cobble + 2 sticks (3x3 — table required)
        if (getInventory().countItem(Items.STONE_PICKAXE) == 0 && cobble >= 3 && sticks >= 2) {
            System.out.printf("[AMB-CRAFT] %s table craft: stone_pickaxe (cobble=%d sticks=%d)%n",
                botName, cobble, sticks);
            if (removeItems(Items.COBBLESTONE, 3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.STONE_PICKAXE, 1));
                broadcastGroupChat("Crafted a stone pickaxe! Upgrading from wood tier.");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: stone_pickaxe%n", botName);
                cobble -= 3; sticks -= 2;
            }
        }

        // Stone axe: 3 cobble + 2 sticks
        if (getInventory().countItem(Items.STONE_AXE) == 0 && cobble >= 3 && sticks >= 2) {
            if (removeItems(Items.COBBLESTONE, 3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.STONE_AXE, 1));
                broadcastGroupChat("Crafted a stone axe!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: stone_axe%n", botName);
                cobble -= 3; sticks -= 2;
            }
        }

        // === IRON TIER — upgrade tools when iron ingots are available ===
        int iron = countItemInInventory(Items.IRON_INGOT);
        sticks = countItemInInventory(Items.STICK);
        if (sticks < 2 && countTotalPlanks() >= 2) {
            if (removeAnyPlanks(2) == 2) { addToInventory(new ItemStack(Items.STICK, 4)); sticks += 4; }
        }
        if (getInventory().countItem(Items.IRON_PICKAXE) == 0 && iron >= 3 && sticks >= 2) {
            System.out.printf("[AMB-CRAFT] %s table craft: iron_pickaxe (iron=%d sticks=%d)%n", botName, iron, sticks);
            if (removeItems(Items.IRON_INGOT, 3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.IRON_PICKAXE, 1));
                broadcastGroupChat("Crafted an iron pickaxe!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: iron_pickaxe%n", botName);
                iron -= 3; sticks -= 2;
            }
        }
        if (getInventory().countItem(Items.IRON_AXE) == 0 && iron >= 3 && sticks >= 2) {
            System.out.printf("[AMB-CRAFT] %s table craft: iron_axe%n", botName);
            if (removeItems(Items.IRON_INGOT, 3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.IRON_AXE, 1));
                broadcastGroupChat("Crafted an iron axe!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: iron_axe%n", botName);
                iron -= 3; sticks -= 2;
            }
        }
        if (getInventory().countItem(Items.IRON_SWORD) == 0 && iron >= 2 && sticks >= 1) {
            System.out.printf("[AMB-CRAFT] %s table craft: iron_sword%n", botName);
            if (removeItems(Items.IRON_INGOT, 2) == 2 && removeItems(Items.STICK, 1) == 1) {
                addToInventory(new ItemStack(Items.IRON_SWORD, 1));
                broadcastGroupChat("Crafted an iron sword!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: iron_sword%n", botName);
                iron -= 2; sticks -= 1;
            }
        }

        // === DIAMOND TIER — upgrade to diamond gear when diamonds are available ===
        int diamonds = countItemInInventory(Items.DIAMOND);
        sticks = countItemInInventory(Items.STICK);
        if (sticks < 2 && countTotalPlanks() >= 2) {
            if (removeAnyPlanks(2) == 2) { addToInventory(new ItemStack(Items.STICK, 4)); sticks += 4; }
        }
        if (getInventory().countItem(Items.DIAMOND_PICKAXE) == 0 && diamonds >= 3 && sticks >= 2) {
            System.out.printf("[AMB-CRAFT] %s table craft: diamond_pickaxe (diamonds=%d sticks=%d)%n", botName, diamonds, sticks);
            if (removeItems(Items.DIAMOND, 3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.DIAMOND_PICKAXE, 1));
                broadcastGroupChat("Crafted a diamond pickaxe!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: diamond_pickaxe%n", botName);
                diamonds -= 3; sticks -= 2;
            }
        }
        if (getInventory().countItem(Items.DIAMOND_AXE) == 0 && diamonds >= 3 && sticks >= 2) {
            System.out.printf("[AMB-CRAFT] %s table craft: diamond_axe%n", botName);
            if (removeItems(Items.DIAMOND, 3) == 3 && removeItems(Items.STICK, 2) == 2) {
                addToInventory(new ItemStack(Items.DIAMOND_AXE, 1));
                broadcastGroupChat("Crafted a diamond axe!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: diamond_axe%n", botName);
                diamonds -= 3; sticks -= 2;
            }
        }
        if (getInventory().countItem(Items.DIAMOND_SWORD) == 0 && diamonds >= 2 && sticks >= 1) {
            System.out.printf("[AMB-CRAFT] %s table craft: diamond_sword%n", botName);
            if (removeItems(Items.DIAMOND, 2) == 2 && removeItems(Items.STICK, 1) == 1) {
                addToInventory(new ItemStack(Items.DIAMOND_SWORD, 1));
                broadcastGroupChat("Crafted a diamond sword!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: diamond_sword%n", botName);
                diamonds -= 2; sticks -= 1;
            }
        }
        if (getInventory().countItem(Items.DIAMOND_HELMET) == 0 && diamonds >= 5) {
            System.out.printf("[AMB-CRAFT] %s table craft: diamond_helmet (diamonds=%d)%n", botName, diamonds);
            if (removeItems(Items.DIAMOND, 5) == 5) {
                addToInventory(new ItemStack(Items.DIAMOND_HELMET, 1));
                broadcastGroupChat("Crafted a diamond helmet!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: diamond_helmet%n", botName);
                diamonds -= 5;
            }
        }
        if (getInventory().countItem(Items.DIAMOND_CHESTPLATE) == 0 && diamonds >= 8) {
            System.out.printf("[AMB-CRAFT] %s table craft: diamond_chestplate (diamonds=%d)%n", botName, diamonds);
            if (removeItems(Items.DIAMOND, 8) == 8) {
                addToInventory(new ItemStack(Items.DIAMOND_CHESTPLATE, 1));
                broadcastGroupChat("Crafted a diamond chestplate!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: diamond_chestplate%n", botName);
                diamonds -= 8;
            }
        }
        if (getInventory().countItem(Items.DIAMOND_LEGGINGS) == 0 && diamonds >= 7) {
            System.out.printf("[AMB-CRAFT] %s table craft: diamond_leggings (diamonds=%d)%n", botName, diamonds);
            if (removeItems(Items.DIAMOND, 7) == 7) {
                addToInventory(new ItemStack(Items.DIAMOND_LEGGINGS, 1));
                broadcastGroupChat("Crafted diamond leggings!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: diamond_leggings%n", botName);
                diamonds -= 7;
            }
        }
        if (getInventory().countItem(Items.DIAMOND_BOOTS) == 0 && diamonds >= 4) {
            System.out.printf("[AMB-CRAFT] %s table craft: diamond_boots (diamonds=%d)%n", botName, diamonds);
            if (removeItems(Items.DIAMOND, 4) == 4) {
                addToInventory(new ItemStack(Items.DIAMOND_BOOTS, 1));
                broadcastGroupChat("Crafted diamond boots!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: diamond_boots%n", botName);
                diamonds -= 4;
            }
        }

        // === STORAGE — chest (8 planks in 3x3, table required) ===
        // Only craft if we have no chest and no known chest location.
        if (knownChests.isEmpty() && getInventory().countItem(Items.CHEST) == 0 && countTotalPlanks() >= 8) {
            System.out.printf("[AMB-CRAFT] %s table craft: chest (8 planks)%n", botName);
            if (removeAnyPlanks(8) == 8) {
                addToInventory(new ItemStack(Items.CHEST, 1));
                broadcastGroupChat("Crafted a chest for storage!");
                System.out.printf("[AMB-CRAFT] %s table craft SUCCESS: chest%n", botName);
            }
        }
    }

    /**
     * Break the self-placed crafting table and let the dropped item be picked up.
     * Only called when craftingTableSelfPlaced=true and no base is established.
     */
    private void pickUpCraftingTable() {
        if (knownCraftingTable.equals(BlockPos.ZERO)) return;
        if (!level().getBlockState(knownCraftingTable).is(Blocks.CRAFTING_TABLE)) {
            knownCraftingTable = BlockPos.ZERO;
            craftingTableSelfPlaced = false;
            return;
        }
        // Break the block so it drops as an item; the item pickup loop will collect it next tick
        level().destroyBlock(knownCraftingTable, true);
        knownCraftingTable = BlockPos.ZERO;
        craftingTableSelfPlaced = false;
        broadcastGroupChat("Packed up my crafting table.");
        System.out.println("[AMB] " + getName().getString() + " picked up crafting table");
    }

    // ==================== Furnace/Smoker/Blast management ====================
    private void ensureFurnaceAvailable() {
        if (!knownFurnace.equals(BlockPos.ZERO)) {
            if (!level().getBlockState(knownFurnace).is(Blocks.FURNACE)) knownFurnace = BlockPos.ZERO;
        }
        if (knownFurnace.equals(BlockPos.ZERO)) {
            BlockPos found = findNearestBlockExact(Blocks.FURNACE, 12);
            if (found != null) { knownFurnace = found; return; }

            // Craft a furnace — requires 3x3 grid (crafting table).
            // Only craft if adjacent to a known crafting table; otherwise wait.
            if (countItemInInventory(Items.COBBLESTONE) >= 8) {
                boolean hasTable = !knownCraftingTable.equals(BlockPos.ZERO)
                    && level().getBlockState(knownCraftingTable).is(Blocks.CRAFTING_TABLE)
                    && this.blockPosition().closerThan(knownCraftingTable, 2.0);
                if (hasTable) {
                    if (removeItems(Items.COBBLESTONE, 8) == 8) {
                        addToInventory(new ItemStack(Blocks.FURNACE.asItem(), 1));
                        broadcastGroupChat("Crafted a furnace at the crafting table.");
                        System.out.printf("[AMB-CRAFT] %s crafted furnace at table %s (3x3 via table)%n",
                            getName().getString(), knownCraftingTable);
                    }
                } else {
                    System.out.printf("[AMB-CRAFT] %s wants furnace but no adjacent table — skipping%n",
                        getName().getString());
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
        pillarPlaceFailCount = 0;
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
        // jumpCooldown is only decremented in the normal navigation block which is skipped
        // during pillar mode. Decrement here so the bot can jump on each pillar step.
        if (jumpCooldown > 0) jumpCooldown--;

        if (pillarCooldown > 0) {
            pillarCooldown--;
            applyVerticalPhysicsOnly(); // keep physics running during cooldown
            return;
        }

        switch (pillarPhase) {
            case BUILDING -> {
                // Fix C: Clear leaf blocks directly above the bot before jumping through them.
                // Leaves are instant-break by hand and would otherwise collide with the bot mid-jump,
                // capping the arc and preventing the bot from reaching the apex for block placement.
                if (!miningState.isMining) {
                    BlockPos leafAbove = findLeafInColumnAbove(4);
                    if (leafAbove != null) {
                        System.out.printf("[AMB-PILLAR] %s clearing leaf at %s before pillar jump%n",
                            getName().getString(), leafAbove);
                        RealisticActions.equipBestTool(this, level().getBlockState(leafAbove));
                        RealisticActions.startMining(this, leafAbove, miningState);
                        applyVerticalPhysicsOnly();
                        return;
                    }
                } else {
                    // Continue leaf mining in progress
                    boolean leafDone = RealisticActions.continueMining(this, miningState);
                    applyVerticalPhysicsOnly();
                    if (!leafDone) return; // still mining the leaf
                    // Leaf done — re-equip building block display next tick
                }

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

                // Show the building block in hand so players can see what the bot is holding.
                // Use the stack from the real inventory slot so shrink() on placement stays correct.
                ItemStack buildDisplay = getInventory().getItem(buildSlot);
                if (!buildDisplay.isEmpty()) {
                    setItemInHand(InteractionHand.MAIN_HAND, buildDisplay);
                    broadcastEquipment();
                }

                // Apply vertical physics (keeps gravity correct between jumps)
                applyVerticalPhysicsOnly();

                int heightDiff2 = pillarTarget.getY() - blockPosition().getY();
                if (onGround() && jumpCooldown == 0) {
                    System.out.printf("[AMB-PILLAR] %s BUILDING jump: botY=%d targetY=%d heightDiff=%d jumpCooldown=%d%n",
                        getName().getString(), blockPosition().getY(), pillarTarget.getY(), heightDiff2, jumpCooldown);
                    jumpFromGround();
                    jumpCooldown = 20;
                    pillarWasAirborne = false;
                } else if (onGround() && jumpCooldown > 0) {
                    if (tickCount % 10 == 0) {
                        System.out.printf("[AMB-PILLAR] %s BUILDING waiting for jumpCooldown=%d botY=%d%n",
                            getName().getString(), jumpCooldown, blockPosition().getY());
                    }
                } else if (!onGround() && getDeltaMovement().y > 0.05) {
                    pillarWasAirborne = true; // mark that we've left the ground rising
                } else if (pillarWasAirborne && !onGround() && getDeltaMovement().y <= 0.05) {
                    // At or near the jump apex — place block in the air below us
                    BlockPos placePos = new BlockPos(blockPosition().getX(),
                                                     blockPosition().getY() - 1,
                                                     blockPosition().getZ());
                    BlockState placeState = level().getBlockState(placePos);
                    System.out.printf("[AMB-PILLAR] %s BUILDING apex: placing at %s (canOcclude=%b isLog=%b)%n",
                        getName().getString(), placePos, placeState.canOcclude(),
                        placeState.is(BlockTags.LOGS));

                    if (!placeState.canOcclude()) {
                        // Normal case: air below us — place building block
                        if (level() instanceof ServerLevel sl) {
                            ItemStack stack = getInventory().getItem(buildSlot);
                            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
                                sl.setBlock(placePos, bi.getBlock().defaultBlockState(), 3);
                                stack.shrink(1);
                                placedPillarBlocks.add(placePos);
                                pillarWasAirborne = false;
                                pillarCooldown = 8;
                                System.out.printf("[AMB-PILLAR] %s placed %s at %s (pillar height=%d remaining=%d)%n",
                                    getName().getString(), bi.getBlock().getName().getString(),
                                    placePos, placedPillarBlocks.size(), stack.getCount());
                            } else {
                                System.out.printf("[AMB-PILLAR] %s BUILDING apex but buildSlot stack invalid slot=%d%n",
                                    getName().getString(), buildSlot);
                                pillarWasAirborne = false; // reset so bot can try again next jump
                            }
                        }
                    } else if (placeState.is(BlockTags.LOGS) && shouldMineBlock(placeState)) {
                        // FIX A/C: Tree trunk occupies the placement position — we're already at
                        // a harvestable log.  Switch directly to MINING instead of looping.
                        pillarTarget = placePos;
                        pillarPhase = PillarPhase.MINING;
                        pillarWasAirborne = false;
                        System.out.printf("[AMB-PILLAR] %s apex is a log at %s — switching to MINING%n",
                            getName().getString(), placePos);
                    } else {
                        // Placement position is occupied by a non-log solid block (terrain, etc.)
                        pillarWasAirborne = false;
                        pillarPlaceFailCount++;
                        System.out.printf("[AMB-PILLAR] %s apex placement blocked by %s at %s — fail %d/3%n",
                            getName().getString(), placeState.getBlock().getName().getString(), placePos, pillarPlaceFailCount);
                        if (pillarPlaceFailCount >= 3) {
                            // Terrain is permanently obstructing the apex — abort pillar and pick a new target.
                            System.out.printf("[AMB-PILLAR] %s aborting pillar after 3 blocked apices — resuming task%n",
                                getName().getString());
                            pillarPhase = PillarPhase.TEARDOWN;
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
                    // Obstruction check: even when elevated, a leaf between bot and log must be cleared first
                    BlockPos pillarMineTarget = pillarTarget;
                    BlockState pillarMineState = bs;
                    if (bs.is(BlockTags.LOGS)) {
                        BlockPos leaf = findObstructingLeaf(pillarTarget);
                        if (leaf != null) {
                            System.out.printf("[AMB-FOLIAGE] %s pillar-MINING: log at %s obstructed by leaf at %s%n",
                                getName().getString(), pillarTarget, leaf);
                            foliageClearTarget = leaf;
                            pillarMineTarget = leaf;
                            pillarMineState = level().getBlockState(leaf);
                        } else {
                            foliageClearTarget = BlockPos.ZERO;
                        }
                    }
                    RealisticActions.equipBestTool(this, pillarMineState);
                    RealisticActions.startMining(this, pillarMineTarget, miningState);
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
     * Discrete ray march from the bot's eye to the center of `target`.
     * Returns the position of the first leaf block found along the path, or null if clear.
     * Used to enforce that a log cannot be broken through intervening foliage — a real player
     * would have to break the leaves first.
     *
     * Steps in 0.5-block increments to catch every intermediate block along the line of access.
     */
    /**
     * FIX B: Two-pass obstruction check.
     *
     * Pass 1 — ray march (0.5-block steps) from eye to log center.
     *   Finds leaves that are directly on the line of sight.
     *
     * Pass 2 — neighbourhood scan (±2-block cube around target).
     *   Catches leaves that are slightly off the direct ray but still physically
     *   between the bot and the log (e.g. leaves on adjacent faces of a log in a
     *   dense cluster).  Only leaves that are (a) closer to the bot than the log,
     *   and (b) roughly in the forward half-cone (dot product > 0.5) are flagged.
     */
    private BlockPos findObstructingLeaf(BlockPos target) {
        Vec3 from = new Vec3(getX(), getEyeY(), getZ());
        Vec3 to   = Vec3.atCenterOf(target);
        Vec3 delta = to.subtract(from);
        double totalDist = delta.length();
        if (totalDist < 0.5) return null;

        // ── Pass 1: ray march ────────────────────────────────────────────────
        Vec3 stepVec = delta.normalize().scale(0.5);
        int maxSteps = (int)(totalDist / 0.5) + 2;
        for (int i = 1; i <= maxSteps; i++) {
            Vec3 pos = from.add(stepVec.x * i, stepVec.y * i, stepVec.z * i);
            BlockPos check = BlockPos.containing(pos);
            if (check.equals(target)) break;
            if (level().getBlockState(check).is(BlockTags.LEAVES)) {
                return check; // leaf on direct ray
            }
        }

        // ── Pass 2: neighbourhood scan (±2 blocks around target) ─────────────
        // Only flags leaves that are between the bot and the log and broadly
        // in the approach direction.
        Vec3 botPos      = position();
        Vec3 toTargetNorm = to.subtract(botPos).normalize();
        BlockPos closest  = null;
        double closestDist = Double.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos check = target.offset(dx, dy, dz);
                    if (!level().getBlockState(check).is(BlockTags.LEAVES)) continue;
                    Vec3 leafCenter    = Vec3.atCenterOf(check);
                    double distToBot   = leafCenter.distanceTo(botPos);
                    if (distToBot >= totalDist) continue; // behind or at the target
                    Vec3 toLeaf = leafCenter.subtract(botPos).normalize();
                    if (toTargetNorm.dot(toLeaf) < 0.5) continue; // outside forward half-cone
                    if (distToBot < closestDist) {
                        closestDist = distToBot;
                        closest = check;
                    }
                }
            }
        }
        if (closest != null) {
            System.out.printf("[AMB-FOLIAGE] %s secondary scan: off-ray leaf at %s obstructs log at %s (dist=%.1f)%n",
                getName().getString(), closest, target, closestDist);
        }
        return closest;
    }

    /**
     * General-purpose obstruction check: ray-march from the bot's eye to the target block.
     * Returns the first block on the ray that is solid (canOcclude OR leaves) and is NOT
     * the target itself. Returns null when the target is directly accessible.
     *
     * Replaces the leaf-only findObstructingLeaf() for all mining operations so the bot
     * cannot break blocks through intervening terrain (stone, dirt, other logs, etc.).
     */
    private BlockPos findSolidObstruction(BlockPos target) {
        Vec3 from = new Vec3(getX(), getEyeY(), getZ());
        Vec3 to   = Vec3.atCenterOf(target);
        Vec3 delta = to.subtract(from);
        double totalDist = delta.length();
        if (totalDist < 0.5) return null;

        Vec3 stepVec = delta.normalize().scale(0.5);
        int maxSteps = (int)(totalDist / 0.5) + 2;
        for (int i = 1; i <= maxSteps; i++) {
            Vec3 pos = from.add(stepVec.x * i, stepVec.y * i, stepVec.z * i);
            BlockPos check = BlockPos.containing(pos);
            if (check.equals(target)) return null; // first solid hit is the target — accessible
            BlockState bs = level().getBlockState(check);
            if (bs.canOcclude() || bs.is(BlockTags.LEAVES)) {
                return check;
            }
        }
        return null;
    }

    /**
     * Fix C: Find the lowest leaf block in the column directly above the bot (within maxHeight).
     * Used to clear leaves before and during pillar jumps so they don't cap the jump arc.
     */
    private BlockPos findLeafInColumnAbove(int maxHeight) {
        for (int dy = 1; dy <= maxHeight; dy++) {
            BlockPos check = blockPosition().above(dy);
            BlockState bs = level().getBlockState(check);
            if (bs.is(BlockTags.LEAVES)) return check;
            if (bs.canOcclude()) break; // a solid block ends the column search
        }
        return null;
    }

    /**
     * Fix B: Returns true if there is at least one log block within hRadius horizontal blocks
     * and within ±4 Y of the bot (i.e., ground-level reachable without a pillar), excluding
     * the current goal which is already established as out of reach.
     */
    private boolean hasOtherReachableLogs(int hRadius) {
        int myY = blockPosition().getY();
        for (int dx = -hRadius; dx <= hRadius; dx++) {
            for (int dy = -2; dy <= 6; dy++) { // FIX A/C: 4→6 — logs 5-6 blocks up are navigable
                for (int dz = -hRadius; dz <= hRadius; dz++) {
                    BlockPos check = blockPosition().offset(dx, dy, dz);
                    if (check.equals(currentGoal)) continue;
                    if (!level().getBlockState(check).is(BlockTags.LOGS)) continue;
                    // Reachable: within ±6 Y (A* handles moderate slopes; pillar only for 7+)
                    if (Math.abs(check.getY() - myY) <= 6) return true;
                }
            }
        }
        return false;
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
    // Break cooldown: allow at most one block break every N ticks to emulate survival mining speed
    private int navBreakCooldown = 0;

    // ==================== PLATFORM ESCAPE ====================
    /**
     * When A* is repeatedly blocked (jump-loop bail), attempt to place one building block
     * adjacent to the bot to create a step-up surface. Targets the direction toward the goal
     * first, then falls back to all 4 cardinals.
     *
     * Typical use: bot is 1 block below a ledge; places dirt/cobble to step up.
     * Returns true if a block was successfully placed and a jump initiated.
     */
    private boolean tryPlatformEscape() {
        if (!(level() instanceof ServerLevel sl)) return false;
        if (currentGoal.equals(BlockPos.ZERO)) return false;

        // Safety check: don't use platform blocks at all if already enclosed — the
        // BotEscapeHelper handles structural escape and using blocks here causes self-trapping.
        if (BotNavigationHelper.isEnclosed(sl, blockPosition())) {
            System.out.printf("[AMB-PLATFORM] %s enclosed — platform escape suppressed (BotEscapeHelper should handle this)%n",
                getName().getString());
            return false;
        }

        ItemStack buildStack = findPlaceableBlock();
        if (buildStack.isEmpty()) {
            System.out.printf("[AMB-PLATFORM] %s has no building blocks — platform escape skipped%n",
                getName().getString());
            return false;
        }
        Block blockType = Block.byItem(buildStack.getItem());
        if (blockType == Blocks.AIR) return false;

        // Determine goal direction as primary candidate
        Vec3 toGoal = Vec3.atCenterOf(currentGoal).subtract(position());
        Direction goalDir = Direction.getNearest((int) Math.round(toGoal.x), 0, (int) Math.round(toGoal.z), Direction.NORTH);

        // Try goal direction first, then all cardinals
        java.util.LinkedHashSet<Direction> tryDirs = new java.util.LinkedHashSet<>();
        tryDirs.add(goalDir);
        tryDirs.add(Direction.NORTH); tryDirs.add(Direction.SOUTH);
        tryDirs.add(Direction.EAST);  tryDirs.add(Direction.WEST);

        BlockPos me = blockPosition();
        for (Direction dir : tryDirs) {
            BlockPos adj = me.relative(dir);

            // Case 1 only: place at adj (same Y) to fill a gap and create walkable surface.
            // Requirements: adj must be air, support below adj solid, head clearance above adj clear.
            // Case 2 (place atop wall at head level) is REMOVED — it places blocks at head height
            // adjacent to the bot, blocking movement in that direction and causing self-enclosure.
            if (!sl.getBlockState(adj).isAir()) continue;
            if (!sl.getBlockState(adj.below()).canOcclude()) continue;
            if (!sl.getBlockState(adj.above()).isAir()) continue;

            // Placement safety: after placing here, verify the bot still has at least one
            // other adjacent direction that is passable (2-tall). This prevents the last
            // exit being blocked off and creating a 1×1 prison.
            int remainingExits = 0;
            for (Direction checkDir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (checkDir == dir) continue; // the direction being filled
                BlockPos checkAdj = me.relative(checkDir);
                if (BotNavigationHelper.isPassableBlock(sl.getBlockState(checkAdj))
                        && BotNavigationHelper.isPassableBlock(sl.getBlockState(checkAdj.above()))) {
                    remainingExits++;
                }
            }
            if (remainingExits == 0) {
                System.out.printf("[AMB-PLATFORM] %s safety reject: placing at %s would leave 0 exits — skipping dir=%s%n",
                    getName().getString(), adj, dir.getName());
                continue;
            }

            sl.setBlock(adj, blockType.defaultBlockState(), 3);
            buildStack.shrink(1);
            selfPlacedNavigationBlocks.add(adj); // Track so harvest searches skip this block
            System.out.printf("[AMB-PLATFORM] %s placed %s at %s (bridge at same-Y) dir=%s exits_remaining=%d%n",
                getName().getString(), blockType.getName().getString(), adj, dir.getName(), remainingExits);
            if (onGround() && jumpCooldown == 0) { jumpFromGround(); jumpCooldown = 15; }
            currentPath.clear(); pathIndex = 0;
            return true;
        }

        System.out.printf("[AMB-PLATFORM] %s no safe platform spot — giving up%n",
            getName().getString());
        return false;
    }

    /**
     * Find a solid block in inventory suitable for navigation platform placement.
     * Prefers expendable terrain items (dirt, cobble) over crafting materials.
     */
    private ItemStack findPlaceableBlock() {
        Item[] preferred = {
            Items.DIRT, Items.COBBLESTONE, Items.GRAVEL, Items.SAND,
            Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG,
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS
        };
        for (Item item : preferred) {
            for (int i = 0; i < getInventory().getContainerSize(); i++) {
                ItemStack s = getInventory().getItem(i);
                if (!s.isEmpty() && s.is(item)) return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Scan 2 blocks in each cardinal direction (plus one block up) for leaf blocks.
     * Returns the first leaf found, or null.  Used by jump-loop bail Option L to clear
     * leaf obstructions that are trapping the bot in a canopy pocket.
     */
    private BlockPos findAdjacentLeaf() {
        BlockPos botPos = blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int dist = 1; dist <= 2; dist++) {
                BlockPos check = botPos.relative(dir, dist);
                if (level().getBlockState(check).is(BlockTags.LEAVES)) return check;
                BlockPos checkAbove = check.above();
                if (level().getBlockState(checkAbove).is(BlockTags.LEAVES)) return checkAbove;
            }
        }
        return null;
    }

    private void tryBreakPathBlock(int wpDY) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (navBreakCooldown > 0) return; // wait for previous break to "complete"
        BlockPos ahead = blockPosition().relative(getDirection());
        int dyStart = (wpDY > 1) ? 1 : 0;
        int dyEnd   = (wpDY > 1) ? 2 : 1;
        for (int dy = dyStart; dy <= dyEnd; dy++) {
            BlockPos breakPos = ahead.above(dy);
            BlockState bs = sl.getBlockState(breakPos);
            if (bs.is(Blocks.BEDROCK)) continue;
            // Leaf blocks have canOcclude()=false yet possess a full collision box in 1.21.1.
            // A* treats them as passable (clearable), but entities are physically blocked by them.
            // Allow tryBreakPathBlock to handle leaves so forward motion isn't permanently stalled.
            boolean physicallyBlocking = bs.canOcclude() || bs.is(BlockTags.LEAVES);
            if (!physicallyBlocking) continue;

            // NEVER break blocks that can simply be climbed — stairs, slabs, walls, fences.
            // These should be handled by the step-up / jump system instead.
            if (bs.getBlock() instanceof StairBlock || bs.getBlock() instanceof SlabBlock
                    || bs.is(BlockTags.FENCES) || bs.is(BlockTags.WALLS)) {
                // Force a jump attempt instead of breaking
                if (onGround() && jumpCooldown == 0) {
                    jumpFromGround();
                    jumpCooldown = 15;
                }
                return;
            }

            // Only break natural terrain blocks during navigation.
            // NEVER break player-placed blocks (planks, stone bricks, cobblestone, doors, etc.)
            if (!isNaturalTerrainBlock(bs)) {
                return; // go around; don't damage structures
            }

            RealisticActions.equipBestTool(this, bs);
            float hardness = bs.getDestroySpeed(sl, breakPos);
            navBreakCooldown = Math.min(120, Math.max(5, (int)(hardness * 10)));
            this.gameMode.destroyBlock(breakPos);
            return;
        }
    }

    /**
     * Returns true for naturally-generated terrain blocks that the bot may clear during navigation.
     * Never returns true for player-craftable/placeable blocks (planks, bricks, cobblestone, etc.)
     */
    /** Delegates to the single authoritative classification in BotNavigationHelper. */
    private static boolean isNaturalTerrainBlock(BlockState bs) {
        return BotNavigationHelper.isNaturalTerrainBlock(bs);
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

    // ==================== PROGRESSION FRAMEWORK ====================

    /** Count all log-type items in inventory (all wood species). */
    private int countLogsInInventory() {
        return countItemInInventory(Items.OAK_LOG) + countItemInInventory(Items.SPRUCE_LOG)
            + countItemInInventory(Items.BIRCH_LOG) + countItemInInventory(Items.JUNGLE_LOG)
            + countItemInInventory(Items.ACACIA_LOG) + countItemInInventory(Items.DARK_OAK_LOG)
            + countItemInInventory(Items.MANGROVE_LOG) + countItemInInventory(Items.CHERRY_LOG)
            + countItemInInventory(Items.BAMBOO_BLOCK);
    }

    /**
     * Rule-based progression advisor.
     *
     * Evaluates the bot's current inventory against survival tech-tree thresholds
     * and returns the task the bot SHOULD be doing, or null if the current task
     * is already appropriate.
     *
     * Priority order (highest first):
     *   1. No tools + enough wood → craft
     *   2. Wooden pickaxe but no stone pickaxe + low cobble → mine_stone
     *   3. Enough cobble but no stone pickaxe → craft (stone tools)
     *   4. Stone pick obtained → mine_stone (keep being productive)
     *   5. Otherwise → null (keep current task)
     */
    private String evaluateProgressionTask() {
        int logs   = countLogsInInventory();
        int planks = countTotalPlanks();
        int woodEquiv = logs * 4 + planks; // total plank-equivalent

        boolean hasWoodPick  = getInventory().countItem(Items.WOODEN_PICKAXE) > 0;
        boolean hasStonePick = getInventory().countItem(Items.STONE_PICKAXE) > 0
                            || getInventory().countItem(Items.IRON_PICKAXE) > 0
                            || getInventory().countItem(Items.DIAMOND_PICKAXE) > 0;
        boolean hasAnyPick   = hasWoodPick || hasStonePick;
        int cobble = countItemInInventory(Items.COBBLESTONE);

        // Stage 1: Have enough wood for table + tools but no pickaxe → craft.
        // Threshold 16 (4 logs) matches ensureCraftingTableAvailable's 16-plank requirement:
        // table(4) + sticks(2) + pick(3+2sticks) + axe(3+2sticks) + sword(2+1stick) = 16 planks.
        if (!hasAnyPick && woodEquiv >= 16) {
            System.out.printf("[AMB-PROG] %s stage=NEED_TOOLS wood=%d — switching gather_wood→craft%n",
                getName().getString(), woodEquiv);
            return "craft";
        }

        // Stage 2: Have wooden pick, no stone pick, and low cobble → mine stone
        if (hasWoodPick && !hasStonePick && cobble < 8) {
            System.out.printf("[AMB-PROG] %s stage=NEED_STONE cobble=%d — switching→mine_stone%n",
                getName().getString(), cobble);
            return "mine_stone";
        }

        // Stage 3: Have 8+ cobble but no stone pick → craft stone tools
        if (cobble >= 8 && !hasStonePick) {
            System.out.printf("[AMB-PROG] %s stage=NEED_STONE_TOOLS cobble=%d — switching→craft%n",
                getName().getString(), cobble);
            return "craft";
        }

        // Stage 4: Stone tools obtained — but check if there's higher-tier gear to craft first.
        // If iron ingots or diamonds are available, stay in "craft" so the bot can make iron/diamond gear.
        // Only advance to mine_stone when the current materials have been fully converted to items.
        if (hasStonePick) {
            int iron     = countItemInInventory(Items.IRON_INGOT);
            int diamonds = countItemInInventory(Items.DIAMOND);
            boolean hasCraftableIron    = iron >= 3 &&
                (getInventory().countItem(Items.IRON_PICKAXE) == 0
                 || getInventory().countItem(Items.IRON_AXE) == 0
                 || getInventory().countItem(Items.IRON_SWORD) == 0);
            boolean hasCraftableDiamonds = diamonds >= 3 &&
                (getInventory().countItem(Items.DIAMOND_PICKAXE) == 0
                 || getInventory().countItem(Items.DIAMOND_SWORD) == 0
                 || getInventory().countItem(Items.DIAMOND_AXE) == 0
                 || getInventory().countItem(Items.DIAMOND_HELMET) == 0
                 || getInventory().countItem(Items.DIAMOND_CHESTPLATE) == 0
                 || getInventory().countItem(Items.DIAMOND_LEGGINGS) == 0
                 || getInventory().countItem(Items.DIAMOND_BOOTS) == 0);
            if (hasCraftableIron || hasCraftableDiamonds) {
                System.out.printf("[AMB-PROG] %s stage=TOOLS_COMPLETE iron=%d diamonds=%d — staying in craft for gear upgrade%n",
                    getName().getString(), iron, diamonds);
                return "craft";
            }
            System.out.printf("[AMB-PROG] %s stage=TOOLS_COMPLETE — advancing to mine_stone%n",
                getName().getString());
            return "mine_stone";
        }

        return null; // current task is appropriate
    }

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

        // Inventory snapshot at every task-execute call — essential for diagnosing progression failures
        System.out.printf("[AMB-TASK] %s executeCurrentTask(%s) inv: logs=%d planks=%d sticks=%d cobble=%d woodPick=%d stonePick=%d%n",
            getName().getString(), currentTask,
            countLogsInInventory(), countTotalPlanks(), countItemInInventory(Items.STICK),
            countItemInInventory(Items.COBBLESTONE),
            getInventory().countItem(Items.WOODEN_PICKAXE),
            getInventory().countItem(Items.STONE_PICKAXE));

        switch (currentTask) {
            case "gather_wood" -> {
                // Progression check: self-advance when enough resources are available.
                // Guard: inProgressionEval prevents re-entrant call from the recursive executeCurrentTask().
                if (!inProgressionEval) {
                    inProgressionEval = true;
                    String nextTask = evaluateProgressionTask();
                    inProgressionEval = false;
                    if (nextTask != null && !nextTask.equals("gather_wood")) {
                        System.out.printf("[AMB-PROG] %s gather_wood quota met → switching to %s%n",
                            getName().getString(), nextTask);
                        currentTask = nextTask;
                        executeCurrentTask();
                        return;
                    }
                }
                // Only target logs that are part of a natural tree (have leaves nearby).
                // This prevents mining logs in player structures.
                // FIX D: log local perception snapshot before choosing target.
                int nearbyLogs   = countBlocksNearby(BlockTags.LOGS,   20);
                int nearbyLeaves = countBlocksNearby(BlockTags.LEAVES,  20);
                System.out.printf("[AMB-PERCEIVE] %s local snapshot: logs=%d leaves=%d within 20 blocks%n",
                    getName().getString(), nearbyLogs, nearbyLeaves);
                BlockPos log = findNearestTreeLog(32);
                if (log != null) {
                    double dist = position().distanceTo(Vec3.atCenterOf(log));
                    // Quick obstruction hint: how many leaves are adjacent to this log?
                    int adjLeaves = 0;
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                        if (level().getBlockState(log.offset(dx, dy, dz)).is(BlockTags.LEAVES)) adjLeaves++;
                    }
                    currentGoal = log;
                    doorPhase = 0; doorPos = BlockPos.ZERO; originalDoorPos = BlockPos.ZERO; doorTimer = 0; avoidTicks = 0;
                    currentPath.clear();
                    pathIndex = 0;
                    pathRetryTimer = 0;
                    System.out.printf("[AMB-PERCEIVE] %s gather_wood target: log at %s dist=%.1f adjLeaves=%d%n",
                        getName().getString(), log, dist, adjLeaves);
                } else {
                    // No natural tree found — wander to find one
                    currentGoal = blockPosition().offset(
                        random.nextInt(40) - 20, 0, random.nextInt(40) - 20);
                    System.out.println("[AMB] " + getName().getString() + " no natural trees in range, wandering");
                }
            }
            case "mine_stone" -> {
                // Progression check: if we've gathered enough cobble to upgrade tools, craft.
                if (!inProgressionEval) {
                    inProgressionEval = true;
                    String nextTask = evaluateProgressionTask();
                    inProgressionEval = false;
                    if (nextTask != null && !nextTask.equals("mine_stone")) {
                        System.out.printf("[AMB-PROG] %s mine_stone quota met → switching to %s%n",
                            getName().getString(), nextTask);
                        currentTask = nextTask;
                        executeCurrentTask();
                        return;
                    }
                }
                BlockPos stone = findNearestHarvestTarget(new net.minecraft.world.level.block.Block[]{
                    Blocks.STONE, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE}, 32);
                if (stone != null) {
                    currentGoal = stone;
                    doorPhase = 0; doorPos = BlockPos.ZERO; originalDoorPos = BlockPos.ZERO; doorTimer = 0; avoidTicks = 0;
                    currentPath.clear(); pathIndex = 0; pathRetryTimer = 0;
                    System.out.printf("[AMB-PERCEIVE] %s mine_stone target: %s at %s%n",
                        getName().getString(), level().getBlockState(stone).getBlock().getName().getString(), stone);
                } else {
                    currentGoal = blockPosition().offset(random.nextInt(40) - 20, 0, random.nextInt(40) - 20);
                    System.out.println("[AMB] " + getName().getString() + " no stone in range, wandering");
                }
            }
            case "mine_dirt" -> {
                // FIX D: mine_dirt — find nearest dirt-type block
                BlockPos dirt = findNearestHarvestTarget(new net.minecraft.world.level.block.Block[]{
                    Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
                    Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND}, 32);
                if (dirt != null) {
                    currentGoal = dirt;
                    doorPhase = 0; doorPos = BlockPos.ZERO; originalDoorPos = BlockPos.ZERO; doorTimer = 0; avoidTicks = 0;
                    currentPath.clear(); pathIndex = 0; pathRetryTimer = 0;
                    System.out.printf("[AMB-PERCEIVE] %s mine_dirt target: %s at %s%n",
                        getName().getString(), level().getBlockState(dirt).getBlock().getName().getString(), dirt);
                } else {
                    currentGoal = blockPosition().offset(random.nextInt(40) - 20, 0, random.nextInt(40) - 20);
                    System.out.println("[AMB] " + getName().getString() + " no dirt-type blocks in range, wandering");
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
            case "build_underground_base" -> {
                if (baseConstructionPhase == 0 || (baseConstructionPhase == 1 && baseDigQueue.isEmpty())) {
                    // Phase 0: no base yet — find a good underground spot and initialise dig queue
                    if (baseConstructionPhase == 0) {
                        BlockPos spot = findUndergroundBaseSpot();
                        if (spot == null) {
                            // No stone-rich area yet — explore first
                            currentGoal = blockPosition().offset(random.nextInt(30) - 15, 0, random.nextInt(30) - 15);
                            goalLockTimer = 200;
                            broadcastGroupChat("Scouting for a good base location...");
                            break;
                        }
                        populateBaseQueues(spot);
                    }
                    // Phase transitions after dig queue exhausted are handled in mining callback
                }

                if (baseConstructionPhase == 1 && !baseDigQueue.isEmpty()) {
                    // Navigate to next block and let shouldMineBlock + mining state machine handle it
                    BlockPos nextDig = baseDigQueue.peek();
                    currentGoal = nextDig;
                    goalLockTimer = 200;
                    currentPath.clear();
                    pathIndex = 0;
                    pathRetryTimer = 0;

                } else if (baseConstructionPhase == 2) {
                    // Placement phase: navigate to base center, then place all support blocks
                    if (baseSupportQueue.isEmpty()) {
                        baseConstructionPhase = 3;
                        broadcastGroupChat("Underground base is complete!");
                        setTask("idle");
                    } else if (!baseLocation.equals(BlockPos.ZERO)
                            && blockPosition().closerThan(baseLocation, 8.0)) {
                        // Close enough — place support blocks
                        placeBaseSupportBlocks();
                    } else if (!baseLocation.equals(BlockPos.ZERO)) {
                        currentGoal = baseLocation;
                        goalLockTimer = 300;
                        currentPath.clear();
                        pathIndex = 0;
                        pathRetryTimer = 0;
                    }

                } else if (baseConstructionPhase == 3) {
                    // Base is done — idle until damage triggers repair
                    currentGoal = BlockPos.ZERO;
                    goalLockTimer = 200;
                }
            }

            case "craft", "place_crafting_table" -> {
                // Guard: if progression says we should be doing something else (e.g. stone tools
                // already crafted → mine_stone), switch immediately instead of looping to the table.
                if (!inProgressionEval) {
                    inProgressionEval = true;
                    String nextTask = evaluateProgressionTask();
                    inProgressionEval = false;
                    if (nextTask != null && !"craft".equals(nextTask)) {
                        System.out.printf("[AMB-PROG] %s craft: progression says %s — switching%n",
                            getName().getString(), nextTask);
                        currentTask = nextTask;
                        executeCurrentTask();
                        return;
                    }
                }

                // Ensure a crafting table exists (find nearby, or craft one from planks and place it)
                ensureCraftingTableAvailable(false);

                if (!knownCraftingTable.equals(BlockPos.ZERO)) {
                    // Navigate to the table — crafting fires once the bot is adjacent.
                    craftStallCount = 0;
                    currentGoal = knownCraftingTable;
                    goalLockTimer = 400;
                    currentPath.clear();
                    pathIndex = 0;
                    pathRetryTimer = 0;
                    System.out.printf("[AMB-STATION] %s craft: navigating to table at %s%n",
                        getName().getString(), knownCraftingTable);
                } else {
                    // No table found and placement failed.
                    craftStallCount++;
                    int haveWoodEquiv = countLogsInInventory() * 4 + countTotalPlanks();
                    System.out.printf("[AMB-STALL] %s craft: no table available stall=%d woodEquiv=%d%n",
                        getName().getString(), craftStallCount, haveWoodEquiv);

                    if (haveWoodEquiv < 16) {
                        // Genuinely need more wood — gather it.
                        System.out.printf("[AMB-STALL] %s craft: insufficient wood — gathering more%n", getName().getString());
                        currentTask = "gather_wood";
                        craftStallCount = 0;
                        currentGoal = BlockPos.ZERO;
                        executeCurrentTask();
                    } else if (craftStallCount >= 3) {
                        // Have enough materials but still can't place — wander to find open ground.
                        craftStallCount = 0;
                        currentGoal = blockPosition().offset(random.nextInt(16) - 8, 0, random.nextInt(16) - 8);
                        System.out.printf("[AMB-STALL] %s craft stall limit: wandering to find placement space → %s%n",
                            getName().getString(), currentGoal);
                    } else {
                        // Stay in craft task — will retry placement on next 40-tick cycle.
                        currentGoal = BlockPos.ZERO;
                    }
                }
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

    // ==================== UNDERGROUND BASE HELPERS ====================

    /**
     * Scan the area for the most stone-rich underground location.
     * Returns a BlockPos at floor level of the proposed main room, or null if none found.
     */
    private BlockPos findUndergroundBaseSpot() {
        if (!(level() instanceof ServerLevel sl)) return null;
        BlockPos myPos = blockPosition();
        BlockPos best = null;
        int bestScore = -1;

        for (int dx = -20; dx <= 20; dx += 4) {
            for (int dz = -20; dz <= 20; dz += 4) {
                int x = myPos.getX() + dx;
                int z = myPos.getZ() + dz;
                int surfaceY = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int floorY = surfaceY - 9; // room floor is 9 blocks below surface (3-tall room + 3 ceiling clearance + 3 shaft)
                if (floorY < 5) continue; // too close to bedrock

                // Count solid blocks in the proposed 5x3x5 main room volume
                int score = 0;
                for (int bx = -2; bx <= 2; bx++) {
                    for (int by = 0; by <= 2; by++) {
                        for (int bz = -2; bz <= 2; bz++) {
                            BlockState bs = sl.getBlockState(new BlockPos(x + bx, floorY + by, z + bz));
                            if (bs.canOcclude() && !bs.is(Blocks.BEDROCK)) score++;
                        }
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = new BlockPos(x, floorY, z);
                }
            }
        }
        return (bestScore >= 20) ? best : null; // require at least 20/75 solid blocks
    }

    /**
     * Initialise the dig queue (ordered: furthest→closest, ceiling→floor) and the
     * support placement queue (ladders, torches, chest) for a new underground base.
     */
    private void populateBaseQueues(BlockPos center) {
        if (!(level() instanceof ServerLevel sl)) return;
        baseDigQueue.clear();
        baseSupportQueue.clear();
        knownStructureBlocks.clear();

        List<BlockPos> toDigList = new ArrayList<>();
        // Main room: 5 wide × 3 tall × 5 deep
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!sl.getBlockState(p).isAir()) toDigList.add(p);
                }
            }
        }
        // Ladder shaft: single-block column from room ceiling (center.y+3) to surface
        int surfaceY = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        for (int y = center.getY() + 3; y <= surfaceY; y++) {
            BlockPos p = new BlockPos(center.getX(), y, center.getZ());
            if (!sl.getBlockState(p).isAir() && !sl.getBlockState(p).is(Blocks.BEDROCK)) toDigList.add(p);
        }

        // Sort: furthest from bot first (so bot digs inward), then highest-Y first (ceiling before floor)
        BlockPos botPos = blockPosition();
        toDigList.sort(Comparator
            .comparingDouble((BlockPos p) -> -p.distSqr(botPos)) // furthest first
            .thenComparingInt(p -> -p.getY()));                   // highest Y first within same distance

        baseDigQueue.addAll(toDigList);

        // Support placements: torches at floor corners, chest on north wall, ladders in shaft
        // Torches (floor level, near corners)
        for (int[] corner : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
            BlockPos torch = new BlockPos(center.getX() + corner[0], center.getY(), center.getZ() + corner[1]);
            baseSupportQueue.add(torch);
            knownStructureBlocks.put(torch, Blocks.TORCH);
        }
        // Chest on north wall floor
        BlockPos chest = new BlockPos(center.getX(), center.getY(), center.getZ() - 2);
        baseSupportQueue.add(chest);
        knownStructureBlocks.put(chest, Blocks.CHEST);
        // Ladders in shaft (attached to the south wall — z+1 is solid)
        for (int y = center.getY() + 3; y <= surfaceY; y++) {
            BlockPos ladder = new BlockPos(center.getX(), y, center.getZ());
            baseSupportQueue.add(ladder);
            knownStructureBlocks.put(ladder, Blocks.LADDER);
        }

        baseLocation = center; // use baseLocation to track the base center
        baseConstructionPhase = 1;
        System.out.printf("[AMB-BASE] %s initialised base at %s: %d blocks to dig, %d supports to place%n",
            getName().getString(), center.toShortString(), baseDigQueue.size(), baseSupportQueue.size());
        broadcastGroupChat("Found great spot at " + center.toShortString() + "! Digging " + baseDigQueue.size() + " blocks!");
    }

    /**
     * Place all queued support blocks that the bot can reach from its current position.
     * Handles torches, chests, and ladders with correct block states.
     */
    private void placeBaseSupportBlocks() {
        if (!(level() instanceof ServerLevel sl)) return;
        int placed = 0;
        Iterator<BlockPos> it = baseSupportQueue.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            Block toPlace = knownStructureBlocks.get(pos);
            if (toPlace == null) { it.remove(); continue; }
            BlockState current = sl.getBlockState(pos);
            if (!current.isAir()) { it.remove(); continue; } // already placed / blocked

            // Check bot has the block in inventory (or it's free to place)
            if (toPlace == Blocks.TORCH) {
                if (getInventory().countItem(Items.TORCH) > 0) {
                    BlockPos below = pos.below();
                    if (sl.getBlockState(below).canOcclude()) {
                        sl.setBlock(pos, Blocks.TORCH.defaultBlockState(), 3);
                        removeItems(Items.TORCH, 1);
                        it.remove();
                        placed++;
                    }
                }
            } else if (toPlace == Blocks.LADDER) {
                if (getInventory().countItem(Items.LADDER) > 0) {
                    // Ladder needs a solid wall — check north wall (z-1) first, else south
                    Direction facing = sl.getBlockState(pos.north()).canOcclude() ? Direction.NORTH
                                     : sl.getBlockState(pos.south()).canOcclude() ? Direction.SOUTH
                                     : sl.getBlockState(pos.east()).canOcclude()  ? Direction.EAST
                                     : Direction.WEST;
                    BlockState ladderState = Blocks.LADDER.defaultBlockState()
                        .setValue(LadderBlock.FACING, facing);
                    sl.setBlock(pos, ladderState, 3);
                    removeItems(Items.LADDER, 1);
                    it.remove();
                    placed++;
                }
            } else if (toPlace == Blocks.CHEST) {
                if (getInventory().countItem(Items.CHEST) > 0) {
                    sl.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
                    removeItems(Items.CHEST, 1);
                    it.remove();
                    placed++;
                }
            } else {
                // Generic block placement (e.g. cobblestone for repairs)
                if (getInventory().countItem(toPlace.asItem()) > 0) {
                    sl.setBlock(pos, toPlace.defaultBlockState(), 3);
                    removeItems(toPlace.asItem(), 1);
                    it.remove();
                    placed++;
                }
            }
        }

        if (placed > 0) {
            System.out.println("[AMB-BASE] " + getName().getString() + " placed " + placed + " support blocks");
        }
        if (baseSupportQueue.isEmpty()) {
            baseConstructionPhase = 3;
            broadcastGroupChat("Underground base is complete!");
            setTask("idle");
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

                        // Skip if too far vertically (>10 blocks) — trees on hills are 6-10 blocks above
                        if (Math.abs(dy) > 10) {
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
     * Scans laterally (perpendicular to the direction toward goal) for a 2-tall passable gap
     * in the blocking wall, up to maxScan blocks to each side.
     *
     * When A* repeatedly fails because the direct path is blocked by a wall with only a narrow
     * opening to the side, this method finds that opening so the bot can route through it as
     * a detour waypoint instead of running headfirst into the wall.
     *
     * @param goal     the navigation goal the bot is trying to reach
     * @param maxScan  how many blocks to scan laterally on each side (typically 8)
     * @return a passable BlockPos adjacent to the wall opening, or null if none found
     */
    private BlockPos findLateralGap(BlockPos goal, int maxScan) {
        if (!(level() instanceof ServerLevel sl)) return null;
        BlockPos myPos = blockPosition();

        // Approximate direction toward goal
        double dx = goal.getX() - myPos.getX();
        double dz = goal.getZ() - myPos.getZ();

        // Primary move direction (dominant axis)
        Direction forward;
        Direction lateral;
        if (Math.abs(dx) >= Math.abs(dz)) {
            forward = dx > 0 ? Direction.EAST : Direction.WEST;
            lateral = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            forward = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            lateral = dx >= 0 ? Direction.EAST : Direction.WEST;
        }

        Direction lateralOpp = lateral.getOpposite();

        // Scan forward a few steps then laterally on both sides to find a passable gap
        for (int fwd = 1; fwd <= 4; fwd++) {
            BlockPos fwdBase = myPos.relative(forward, fwd);
            for (int side = 1; side <= maxScan; side++) {
                // Check both left and right sides
                for (Direction scanDir : new Direction[]{ lateral, lateralOpp }) {
                    BlockPos candidate = fwdBase.relative(scanDir, side);
                    BlockState feet = sl.getBlockState(candidate);
                    BlockState head = sl.getBlockState(candidate.above());
                    if (BotNavigationHelper.isPassableBlock(feet) && BotNavigationHelper.isPassableBlock(head)) {
                        // Requires solid floor to stand on
                        if (sl.getBlockState(candidate.below()).canOcclude()) {
                            return candidate;
                        }
                    } else {
                        // Solid wall — no gap in this lateral direction beyond here
                        break;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find the nearest block that is one of the specified block types.
     * Searches a y range of ±10, same as findNearestBlock(Block, int).
     * Used by mine_stone, mine_dirt, mine_ore task selection.
     */
    private BlockPos findNearestHarvestTarget(net.minecraft.world.level.block.Block[] blocks, int radius) {
        BlockPos myPos = blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = myPos.offset(x, y, z);
                    BlockState st = level().getBlockState(check);
                    boolean matches = false;
                    for (net.minecraft.world.level.block.Block b : blocks) {
                        if (st.is(b)) { matches = true; break; }
                    }
                    if (!matches) continue;
                    // Skip goals that recently caused a jump loop (unbreakable terrain)
                    if (unreachableGoalBlacklist.getOrDefault(check, 0) > tickCount) continue;
                    // Skip targets already claimed by a peer bot
                    if (isClaimedByOther(check)) continue;
                    // Skip blocks the bot itself placed as temporary navigation aids
                    if (selfPlacedNavigationBlocks.contains(check)) continue;
                    double dist = myPos.distSqr(check);
                    if (dist < nearestDist) { nearestDist = dist; nearest = check; }
                }
            }
        }
        if (nearest != null) claimTarget(nearest);
        return nearest;
    }

    /**
     * Find the nearest log that is part of a natural tree (has at least one leaf within 6 blocks).
     * Avoids mining logs in player structures which have no leaves nearby.
     */
    private BlockPos findNearestTreeLog(int radius) {
        BlockPos myPos = blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -5; y <= 20; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = myPos.offset(x, y, z);
                    if (!level().getBlockState(check).is(BlockTags.LOGS)) continue;
                    // Skip goals that recently caused a jump loop (unbreakable terrain between bot and block)
                    if (unreachableGoalBlacklist.getOrDefault(check, 0) > tickCount) continue;
                    // Skip targets already claimed by a peer bot
                    if (isClaimedByOther(check)) continue;
                    // Verify this log has leaves nearby (radius=10 catches tall trees)
                    if (!hasLeavesNearby(check, 10)) continue;

                    // Height-penalized score: each block upward costs 3× a horizontal block.
                    // This strongly prefers same-level or lower logs over high canopy logs,
                    // preventing the bot from committing to an unreachable high trunk when
                    // accessible ground-level logs exist.
                    int heightAboveBot = Math.max(0, y);
                    double horzDist = Math.sqrt((double)(x * x + z * z));
                    double score = horzDist + heightAboveBot * 3.0;
                    if (score < nearestDist) {
                        nearestDist = score;
                        nearest = check;
                    }
                }
            }
        }
        if (nearest != null) claimTarget(nearest);
        return nearest;
    }

    /**
     * Find the nearest collectible ItemEntity for active navigation.
     *
     * FIX A: Only redirect navigation toward items that are ALREADY collectable
     * (pickup delay expired) or within the 4-block passive-pickup radius.
     * Items still in their 10-tick delay beyond pickup range are excluded — chasing
     * them interrupts harvesting and the bot arrives to find an empty air block
     * (passive pickup already collected the item while the bot was en route).
     */
    private ItemEntity findNearestItem(double radius) {
        ItemEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        AABB search = getBoundingBox().inflate(radius, radius / 2.0, radius);
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, search)) {
            if (item.isRemoved()) continue;
            double dist = distanceTo(item);
            // Skip items still in pickup delay unless they're already in passive-pickup range.
            // Passive pickup radius is 4 blocks; items there will be grabbed anyway.
            if (item.hasPickUpDelay() && dist > 4.0) continue;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = item;
            }
        }
        return nearest;
    }

    /**
     * Passive proximity pickup — runs unconditionally every tick regardless of task or mode.
     * Items with active pickup delay are skipped; they are collected automatically once the
     * delay expires (40 ticks for player-dropped items, 10 ticks for world-spawned drops).
     * This must NOT be gated on LLM state, task state, or pillar state.
     */
    private void doPassivePickup() {
        if (level().isClientSide() || !(level() instanceof ServerLevel sl)) return;
        // Inflate: 4 blocks horizontal, 2 blocks vertical (covers items on adjacent Y levels)
        AABB pickupBox = getBoundingBox().inflate(4.0, 2.0, 4.0);
        int collectorId = (visualEntity != null && !visualEntity.isRemoved())
                          ? visualEntity.getId() : this.getId();

        for (ItemEntity itemEntity : sl.getEntitiesOfClass(ItemEntity.class, pickupBox)) {
            if (itemEntity.isRemoved()) continue;
            if (itemEntity.hasPickUpDelay()) {
                // Item nearby but delay active — log only occasionally to avoid spam.
                // This is the expected state immediately after a block break: item entity
                // is in the world and visible to clients, just waiting for the delay.
                if (tickCount % 40 == 0) {
                    System.out.printf("[AMB-PICKUP] %s world-drop %s at %s waiting (delay active)%n",
                        getName().getString(),
                        itemEntity.getItem().getHoverName().getString(),
                        itemEntity.blockPosition());
                }
                continue;
            }
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;
            int countBefore = stack.getCount();
            System.out.printf("[AMB-PICKUP] %s passive pickup attempt: %dx%s at %s%n",
                getName().getString(), countBefore,
                stack.getHoverName().getString(), itemEntity.blockPosition());
            getInventory().add(stack);
            int grabbed = countBefore - stack.getCount();
            if (grabbed > 0) {
                // Animation targets the visual entity (what clients actually see)
                ClientboundTakeItemEntityPacket pkt =
                    new ClientboundTakeItemEntityPacket(itemEntity.getId(), collectorId, grabbed);
                for (ServerPlayer sp : sl.players()) sp.connection.send(pkt);
                if (stack.isEmpty()) itemEntity.discard();
                System.out.printf("[AMB-PICKUP] %s collected %dx%s (remaining=%d)%n",
                    getName().getString(), grabbed,
                    stack.getHoverName().getString(), stack.getCount());
            } else {
                System.out.printf("[AMB-PICKUP] %s passive pickup BLOCKED (inventory full?) item=%s%n",
                    getName().getString(), stack.getHoverName().getString());
            }
        }
    }

    /**
     * Immediately collect item drops near a just-mined block, bypassing the pickup delay.
     * Uses the visual entity's ID for the animation packet so clients see items fly to the bot.
     */
    private void collectDropsNear(BlockPos pos) {
        if (!(level() instanceof ServerLevel sl)) return;
        int collectorId = (visualEntity != null && !visualEntity.isRemoved())
                          ? visualEntity.getId() : this.getId();
        AABB box = new AABB(pos).inflate(2.0, 2.0, 2.0);
        for (ItemEntity item : sl.getEntitiesOfClass(ItemEntity.class, box)) {
            if (item.isRemoved()) continue;
            ItemStack stack = item.getItem();
            if (stack.isEmpty()) continue;
            int before = stack.getCount();
            System.out.printf("[AMB-HARVEST] %s harvested drop: %dx%s at %s → collecting%n",
                getName().getString(), before, stack.getHoverName().getString(), item.blockPosition());
            getInventory().add(stack);
            int grabbed = before - stack.getCount();
            if (grabbed > 0) {
                // Target visualEntity so animation flies to what players actually see
                ClientboundTakeItemEntityPacket pkt =
                    new ClientboundTakeItemEntityPacket(item.getId(), collectorId, grabbed);
                for (ServerPlayer sp : sl.players()) sp.connection.send(pkt);
                if (stack.isEmpty()) item.discard();
                System.out.printf("[AMB-HARVEST] %s collected %dx%s from mined block%n",
                    getName().getString(), grabbed, stack.getHoverName().getString());
            }
        }
    }

    /** Count blocks matching a tag within radius of the bot's position (cheap perception snapshot). */
    private int countBlocksNearby(net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tag, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 8; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (level().getBlockState(blockPosition().offset(dx, dy, dz)).is(tag)) count++;
                }
            }
        }
        return count;
    }

    /** Returns true if there is at least one leaf block within the given radius of pos. */
    private boolean hasLeavesNearby(BlockPos pos, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (level().getBlockState(pos.offset(dx, dy, dz)).is(BlockTags.LEAVES)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Find mineable blocks nearby for the current task
     */
    private BlockPos findMineableNearby(BlockPos center, int radius, String task) {
        BlockPos nearestLog = null;
        double nearestLogDist = Double.MAX_VALUE;
        BlockPos nearestLeaf = null;
        double nearestLeafDist = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 8; dy++) { // +8 to reach top of tallest trees (logs at center+7)
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    BlockState state = level().getBlockState(check);

                    if (!shouldMineBlock(state)) continue;
                    double dist = blockPosition().distSqr(check);
                    if (state.is(BlockTags.LOGS)) {
                        if (dist < nearestLogDist) { nearestLogDist = dist; nearestLog = check; }
                    } else if (state.is(BlockTags.LEAVES)) {
                        if (dist < nearestLeafDist) { nearestLeafDist = dist; nearestLeaf = check; }
                    }
                }
            }
        }
        // Prefer logs. Fall back to clearing leaves only when no log is reachable nearby.
        return nearestLog != null ? nearestLog : nearestLeaf;
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
