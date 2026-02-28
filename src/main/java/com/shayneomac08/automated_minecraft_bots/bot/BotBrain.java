package com.shayneomac08.automated_minecraft_bots.bot;

import com.shayneomac08.automated_minecraft_bots.agent.ActionExecutor;
import com.shayneomac08.automated_minecraft_bots.agent.ActionPlan;
import com.shayneomac08.automated_minecraft_bots.llm.LLMProvider;
import com.shayneomac08.automated_minecraft_bots.llm.SimpleJson;
import com.shayneomac08.automated_minecraft_bots.llm.UnifiedLLMClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BotBrain {

    public enum Mode { IDLE, ROAM, FOLLOW_NEAREST, GOAL }

    // STEP 1: PURGED - Removed GATHER_WOOD (now using AMBTaskGoal system)
    public enum GoalType { NONE, MINE_STONE, BUILD_SHELTER, ROAM, ATTACK_HOSTILES }

    public static final class State {
        public boolean autonomous = false;
        public boolean followRequested = false;
        public boolean pvpEnabled = false;
        public boolean allowPlayerCombat = false; // PvP toggle (off by default)
        public Mode mode = Mode.ROAM;
        public GoalType goal = GoalType.NONE; // High-level objective
        public java.util.UUID followTarget = null; // player UUID to follow (when followRequested=true)

        // LLM Configuration
        public LLMProvider llmProvider = LLMProvider.OPENAI; // Which LLM this bot uses

        // Chat and Communication
        public List<String> chatHistory = new ArrayList<>(); // Recent chat messages
        public Queue<String> pendingChatMessages = new LinkedList<>(); // Messages to send
        public int lastChatTick = 0; // Last time bot sent a message

        // Cooldowns
        public int nextThinkTick = 0;
        public int goalUntilTick = 0;
        public int farTicks = 0;                  // for follow catch-up smoothing

        // Optional target info (used by executor)
        public Double goalX = null;
        public Double goalY = null;
        public Double goalZ = null;

        // Survival needs tracking
        public BotSurvivalNeeds.Needs survivalNeeds = new BotSurvivalNeeds.Needs();

        // Movement state tracking (for stuck detection)
        public BotMovementHelper.MovementState movementState = new BotMovementHelper.MovementState();

        // Pending async LLM request
        public CompletableFuture<ActionPlan> pending = null;

        public String lastThought = "";
        public String lastError = "";

        // CHAT VERIFICATION: Track actual actions for truthfulness
        public List<String> recentActions = new ArrayList<>(); // Last 10 actions performed
        public Map<String, Integer> resourcesGathered = new HashMap<>(); // Track what was actually collected
        public String currentActivity = "idle"; // What bot is actually doing right now
        public int ticksOnCurrentActivity = 0; // How long bot has been doing current activity
    }

    private static final Map<String, State> STATES = new ConcurrentHashMap<>();

    private BotBrain() {}

    public static State stateForName(String botName) {
        return STATES.computeIfAbsent(norm(botName), k -> new State());
    }

    public static void setAutonomous(String botName, boolean on) {
        stateForName(botName).autonomous = on;
    }

    public static void setFollowRequested(String botName, boolean on) {
        State st = stateForName(norm(botName));
        st.followRequested = on;
        if (!on) {
            st.followTarget = null;
            return;
        }
        // Manual follow overrides explore goals
        st.goalUntilTick = 0;
        st.goalX = st.goalY = st.goalZ = null;
        st.mode = Mode.FOLLOW_NEAREST;
    }

    public static void setFollowTarget(String botName, java.util.UUID playerId) {
        State st = stateForName(norm(botName));
        st.followRequested = true;
        st.followTarget = playerId;

        // override goals
        st.goalUntilTick = 0;
        st.goalX = st.goalY = st.goalZ = null;
        st.mode = Mode.FOLLOW_NEAREST;
    }


    public static void stopAndClearGoal(String botName) {
        State st = stateForName(norm(botName));
        st.followRequested = false;
        st.followTarget = null;
        st.goalUntilTick = 0;
        st.goalX = st.goalY = st.goalZ = null;
        st.mode = Mode.ROAM; // "Roam around" is the default when no goals or follow are active
    }


    public static void tick(MinecraftServer server, String botName, BotPair pair) {
        final String keyName = norm(botName);
        final State st = stateForName(keyName);

        // Apply completed plan on server thread
        if (st.pending != null && st.pending.isDone()) {
            try {
                ActionPlan plan = st.pending.join();
                st.pending = null;
                System.out.println("[AMB] " + botName + " received LLM response, executing plan...");
                ActionExecutor.apply(server, keyName, st, pair, plan);

            } catch (Exception e) {
                st.pending = null;
                st.lastError = (e.getMessage() == null) ? e.toString() : e.getMessage();
                System.err.println("[AMB] ERROR for bot " + botName + ": " + st.lastError);
                e.printStackTrace(); // Print full stack trace to see what's failing
            }
        }

        if (!st.autonomous) return;

        int tick = server.getTickCount();
        if (tick < st.nextThinkTick) return;

// FIX: Only call LLM when goal expires OR no goal is set
// This prevents constant API spam and lets bots complete tasks
        if (tick < st.goalUntilTick) {
            // Goal is still active - don't interrupt
            return;
        }

// If already waiting on a response, don't spam
        if (st.pending != null) return;

// If follow is manually requested, we don't need LLM spam for that.
// Let ticker handle follow movement.
        if (st.followRequested) {
            st.nextThinkTick = tick + 40; // check again in 2s
            return;
        }

        var hands = (pair == null) ? null : pair.hands(); // We prefer using hands for "player-like senses", but don't crash if missing
        if (hands == null || hands.isRemoved()) {
            st.lastError = "Bot hands missing/removed.";
            st.nextThinkTick = tick + 100;
            return;
        }

        // Get API key and model based on bot's LLM provider
        String apiKey;
        String model;

        System.out.println("[AMB] Loading config for bot: " + botName + " using LLM: " + st.llmProvider);

        switch (st.llmProvider) {
            case GEMINI -> {
                apiKey = com.shayneomac08.automated_minecraft_bots.Config.GEMINI_KEY.get();
                model = com.shayneomac08.automated_minecraft_bots.Config.GEMINI_MODEL.get();
                System.out.println("[AMB] Gemini - Model: " + model + ", Key length: " + (apiKey != null ? apiKey.length() : 0));
                if (apiKey == null || apiKey.isBlank()) {
                    st.lastError = "Missing Gemini API key. Set it in the mod config (gemini_key).";
                    st.nextThinkTick = tick + 200;
                    return;
                }
            }
            case GROK -> {
                apiKey = com.shayneomac08.automated_minecraft_bots.Config.GROK_KEY.get();
                model = com.shayneomac08.automated_minecraft_bots.Config.GROK_MODEL.get();
                System.out.println("[AMB] Grok - Model: " + model + ", Key length: " + (apiKey != null ? apiKey.length() : 0));
                if (apiKey == null || apiKey.isBlank()) {
                    st.lastError = "Missing Grok API key. Set it in the mod config (grok_key).";
                    st.nextThinkTick = tick + 200;
                    return;
                }
            }
            default -> { // OPENAI
                apiKey = com.shayneomac08.automated_minecraft_bots.Config.OPENAI_KEY.get();
                model = com.shayneomac08.automated_minecraft_bots.Config.OPENAI_MODEL.get();
                System.out.println("[AMB] OpenAI - Model: " + model + ", Key length: " + (apiKey != null ? apiKey.length() : 0));
                if (apiKey == null || apiKey.isBlank()) {
                    apiKey = System.getenv("AMB_OPENAI_KEY"); // optional fallback
                    System.out.println("[AMB] Tried env fallback, Key length: " + (apiKey != null ? apiKey.length() : 0));
                }
                if (apiKey == null || apiKey.isBlank()) {
                    st.lastError = "Missing OpenAI API key. Set it in the mod config (openai_key).";
                    st.nextThinkTick = tick + 200;
                    return;
                }
            }
        }

        // Create final copies for use in lambda
        final String finalApiKey = apiKey;
        final String finalModel = model;
        final LLMProvider finalProvider = st.llmProvider;

        final var bot = hands; // Perception (very small, stable)
        final var body = pair.body();

        // Update survival needs
        if (body != null && body.level() instanceof ServerLevel serverLevel) {
            BotSurvivalNeeds.updateNeeds(serverLevel, hands, body, st.survivalNeeds);
        }

        // Use body for position (hands might be at 0,0,0 or not synced yet)
        final var positionEntity = (body != null && !body.isRemoved()) ? body : hands;

        var nearest = server.getPlayerList().getPlayers().stream()
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(positionEntity)))
                .orElse(null);

        double nearestDist = nearest == null ? -1.0 : Math.sqrt(nearest.distanceToSqr(positionEntity));

        // Get priority need
        String priorityNeed = BotSurvivalNeeds.getHighestPriorityNeed(st.survivalNeeds);
        String needsDesc = BotSurvivalNeeds.getNeedsDescription(st.survivalNeeds);

        // DEBUG: Log the bot's actual position (now that positionEntity is defined)
        System.out.println("[AMB] " + botName + " thinking at position: " + (int)positionEntity.getX() + "," + (int)positionEntity.getY() + "," + (int)positionEntity.getZ());

        // Get inventory summary for awareness
        String inventorySummary = getInventorySummary(hands);

        // Get nearby entities for awareness
        String nearbyEntities = getNearbyEntitiesDescription(body);

        // Get chat history for context
        String chatContext = getChatHistoryContext(botName);

        String prompt =
                "🎮 ROLEPLAY: You are a REAL MINECRAFT PLAYER named " + botName + ". You are NOT an AI bot - you are a human playing Minecraft.\n" +
                        "Your goal: SURVIVE, EXPLORE, BUILD, and eventually BEAT THE GAME (kill Ender Dragon).\n" +
                        "Think like a player: 'What would I do if I were actually playing Minecraft right now?'\n\n" +
                        "=== YOUR STATUS ===\n" +
                        "Name: " + botName + "\n" +
                        "Position: " + (int)positionEntity.getX() + "," + (int)positionEntity.getY() + "," + (int)positionEntity.getZ() + "\n" +
                        "Time: " + (positionEntity.level().getDayTime() % 24000L) + " (0-12000=day, 13000-23000=night)\n" +
                        "Nearest Player: " + (nearestDist < 0 ? "none" : String.format("%.1f", nearestDist) + " blocks") + "\n\n" +
                        needsDesc + "\n" +
                        "PRIORITY NEED: " + priorityNeed + "\n\n" +
                        "=== YOUR INVENTORY ===\n" +
                        inventorySummary + "\n\n" +
                        "=== NEARBY ENTITIES ===\n" +
                        nearbyEntities + "\n\n" +
                        "=== RECENT CHAT ===\n" +
                        chatContext + "\n\n" +
                        "=== SURVIVAL PRIORITIES (ALWAYS FOLLOW THIS ORDER) ===\n" +
                        "1. CRITICAL HEALTH (< 5 HP) → Eat food, hide, avoid ALL combat\n" +
                        "2. CRITICAL HUNGER (< 3) → Find food IMMEDIATELY (hunt animals, gather apples)\n" +
                        "3. HEALTH (< 10 HP) → Eat food to regenerate, avoid danger\n" +
                        "4. HUNGER (< 6) → Hunt animals (chicken/pig/cow), gather food\n" +
                        "5. NIGHT/RAIN → Seek shelter (enclosed space with light)\n" +
                        "6. WOOD (< 8 logs) → Gather wood for tools\n" +
                        "7. STONE (< 16) → Mine stone for better tools\n" +
                        "8. PROGRESSION → Crafting table → furnace → bed → shelter\n\n" +
                        "=== FOOD SOURCES (HUNT WHEN HUNGRY) ===\n" +
                        "• Chickens → 1-2 raw chicken (EASY to kill)\n" +
                        "• Pigs → 1-3 raw porkchop (EASY to kill)\n" +
                        "• Cows → 1-3 raw beef + leather (EASY to kill)\n" +
                        "• Sheep → 1-2 raw mutton + wool (EASY to kill)\n" +
                        "• Apples → Break oak/dark oak leaves (NO combat needed)\n" +
                        "• ALWAYS COOK MEAT if you have furnace (3-4x better hunger restoration)\n\n" +
                        "=== COMBAT KNOWLEDGE ===\n" +
                        "• Attack animals by approaching and swinging weapon/hand\n" +
                        "• Weapons: Hands (1 dmg) < Wood Sword (4) < Stone Sword (5) < Iron Sword (6)\n" +
                        "• Animals are PASSIVE - they won't fight back\n" +
                        "• Hostile mobs spawn at NIGHT - avoid or hide in shelter\n" +
                        "• NEVER fight without weapons unless desperate for food\n\n" +
                        "=== TOOL EFFICIENCY (CRITICAL KNOWLEDGE) ===\n" +
                        "WITHOUT TOOLS:\n" +
                        "• Wood with hands: 3 seconds (VERY SLOW)\n" +
                        "• Stone with hands: IMPOSSIBLE (gets nothing)\n" +
                        "• Dirt with hands: 0.75 seconds (acceptable)\n\n" +
                        "WITH CORRECT TOOLS:\n" +
                        "• Wood + Axe: 0.5-1.5 seconds (3-6x FASTER)\n" +
                        "• Stone + Pickaxe: 0.75-2.25 seconds (ONLY WAY to get stone)\n" +
                        "• Dirt + Shovel: 0.15 seconds (5x FASTER)\n\n" +
                        "TOOL PRIORITY:\n" +
                        "1. Wooden Pickaxe (FIRST - mine stone)\n" +
                        "2. Stone Pickaxe (SECOND - mine iron)\n" +
                        "3. Stone Axe (THIRD - chop wood faster)\n" +
                        "4. Stone Sword (FOURTH - hunt animals)\n\n" +
                        "=== GAME MECHANICS YOU MUST UNDERSTAND ===\n" +
                        "• You CANNOT mine stone without a pickaxe (hands do nothing)\n" +
                        "• Tools make tasks 3-10x FASTER - always craft tools first\n" +
                        "• Cooked meat restores 3-4x more hunger than raw meat\n" +
                        "• Night spawns hostile mobs - ALWAYS seek shelter\n" +
                        "• Hunger < 18 prevents health regeneration\n" +
                        "• Recipe unlocking: Pick up items to discover recipes\n" +
                        "• Auto-crafting: System auto-crafts planks, sticks, and crafting table in inventory\n" +
                        "• CRAFTING TABLE RULE: Tools and weapons REQUIRE a placed crafting table nearby!\n" +
                        "  - 2x2 recipes (planks, sticks, crafting table) = craft in inventory automatically\n" +
                        "  - 3x3 recipes (tools, weapons) = MUST place crafting table first!\n\n" +
                        "=== PROGRESSION PATH ===\n" +
                        "Phase 1 (First 5 min): Punch 3-4 logs → auto-crafts planks+sticks+table → PLACE table → auto-crafts wooden pickaxe\n" +
                        "Phase 2 (5-15 min): Mine 8+ cobblestone → auto-crafts stone pickaxe → stone axe → stone sword\n" +
                        "Phase 3 (15-30 min): Hunt animals → craft furnace → cook meat → build shelter\n" +
                        "Phase 4 (30+ min): Mine iron → smelt iron → iron tools → mine diamonds\n\n" +
                        "=== DECISION FRAMEWORK ===\n" +
                        "IF hungry (< 6) → Hunt nearest animal OR gather apples\n" +
                        "IF low health (< 10) → Eat food, seek shelter, avoid combat\n" +
                        "IF night → Build/find shelter immediately\n" +
                        "IF no tools → Gather wood, craft tools (makes everything faster)\n" +
                        "IF need resource but can't see it → Explore in spiral pattern\n\n" +
                        "=== PROBLEM-SOLVING STRATEGIES (THINK LIKE A REAL PLAYER) ===\n" +
                        "Real Minecraft players solve problems creatively. You should too!\n\n" +
                        "PROBLEM: Target is too high to reach (tree top, floating block, etc.)\n" +
                        "SOLUTION: Build a scaffold!\n" +
                        "  1. If you have dirt/cobblestone in inventory → Use mine_stone goal to place blocks and pillar up\n" +
                        "  2. If you DON'T have blocks → First mine_stone to gather dirt/cobblestone, THEN pillar up\n" +
                        "  Example: See oak log at Y=65, you're at Y=59 → mine_stone to get dirt → pillar up → gather_wood\n\n" +
                        "PROBLEM: Stuck trying to reach something for multiple attempts\n" +
                        "SOLUTION: Change strategy!\n" +
                        "  1. If gathering wood but can't reach → Switch to mine_stone to get blocks, then build up\n" +
                        "  2. If path is blocked → Explore to find another route OR mine through obstacles\n" +
                        "  3. If repeatedly failing → Try a completely different goal\n\n" +
                        "PROBLEM: Need to build something but no blocks\n" +
                        "SOLUTION: Gather materials first!\n" +
                        "  1. Want to build shelter but no blocks → mine_stone to get cobblestone first\n" +
                        "  2. Want to build farm but no wood → gather_wood first\n" +
                        "  3. ALWAYS gather materials BEFORE attempting to build\n\n" +
                        "PROBLEM: Can't mine stone (no pickaxe)\n" +
                        "SOLUTION: Craft tools first!\n" +
                        "  1. No pickaxe → gather_wood → auto-craft wooden pickaxe → mine_stone\n" +
                        "  2. Have wood pickaxe → mine_stone to get cobblestone → auto-craft stone pickaxe\n\n" +
                        "PROBLEM: Inventory full\n" +
                        "SOLUTION: Manage resources!\n" +
                        "  1. Drop useless items (dirt if you have 64+, excess tools)\n" +
                        "  2. Use manage_resources to store in chests\n" +
                        "  3. Trade excess items with other bots\n\n" +
                        "KEY INSIGHT: If you're stuck doing the same thing repeatedly, STOP and try something else!\n" +
                        "Real players adapt. You should too. Don't be a robot - be creative!\n\n" +
                        "=== AVAILABLE GOALS ===\n" +
                        "• hunt_animals - Hunt chickens/pigs/cows/sheep for food (auto-finds animals, explores if needed)\n" +
                        "• shear_sheep - Shear sheep for wool (requires shears)\n" +
                        "• tame_animals - Tame wolves/cats/horses/parrots (requires appropriate food)\n" +
                        "• gather_flowers - Gather flowers for decoration\n" +
                        "• gather_wood - Chop trees (auto-finds trees, explores if needed)\n" +
                        "• mine_stone - Mine cobblestone (requires pickaxe)\n" +
                        "• mine_ore - Mine coal/iron/diamonds (requires pickaxe)\n" +
                        "• build_shelter - Build enclosed shelter\n" +
                        "• place_crafting_table - Place crafting table from inventory (REQUIRED before crafting tools!)\n" +
                        "• explore - Wander and discover new areas\n" +
                        "• idle - Stand still (only if safe and all needs met)\n" +
                        "• trade_bots - Trade items with other bots (share resources, help each other)\n" +
                        "• manage_resources - Use shared crafting tables and chests (coordinate with other bots)\n\n" +
                        "=== ADVANCED PLAYER ABILITIES ===\n" +
                        "You can do EVERYTHING a real Minecraft player can do:\n\n" +
                        "FARMING & FOOD:\n" +
                        "• farm - Plant and harvest wheat (requires seeds)\n" +
                        "• breed_animals - Feed animals to breed them (requires wheat/carrots/seeds)\n" +
                        "• fish - Use fishing rod to catch fish (requires fishing_rod)\n\n" +
                        "BUILDING & CONSTRUCTION:\n" +
                        "• build_wall - Build a defensive wall (requires blocks)\n" +
                        "• build_house - Build a 5x5 house with roof (requires blocks)\n" +
                        "• build_tower - Build a tall tower (requires blocks)\n" +
                        "• build_platform - Build a flat platform (requires blocks)\n" +
                        "• build_farm - Build farm structure (requires blocks)\n\n" +
                        "NATURE & ENVIRONMENT:\n" +
                        "• plant_trees - Plant saplings to grow trees (requires saplings)\n" +
                        "• plant_flowers - Plant flowers for decoration (requires flowers)\n" +
                        "• mine_coal, mine_iron, mine_copper, mine_gold, mine_diamond, mine_emerald - Mine specific ores\n" +
                        "• gather_sand, gather_gravel, gather_clay - Gather specific materials\n\n" +
                        "CRAFTING & PROCESSING:\n" +
                        "• smelt - Smelt ores in furnace (requires furnace, fuel, ores)\n" +
                        "• trade - Trade with villagers (requires emeralds/items)\n\n" +
                        "COOPERATION & SHARING:\n" +
                        "• trade_bots - Trade items with other bots (ask for what you need, offer what you have)\n" +
                        "• manage_resources - Use shared crafting tables and store items in shared chests\n" +
                        "• Bots can help each other by sharing resources and coordinating tasks\n" +
                        "• You can ask other bots for items you need and offer items in return\n\n" +
                        "=== CRITICAL RULES ===\n" +
                        "1. NEVER claim you did something you didn't do (check YOUR INVENTORY above for what you actually have)\n" +
                        "2. NEVER mention 'Dev' or 'the developer' unless they actually interacted with you\n" +
                        "3. NEVER claim you opened doors, gave items, or did actions that aren't in your recent history\n" +
                        "4. BE TRUTHFUL - only talk about what you can see in YOUR STATUS, YOUR INVENTORY, and NEARBY ENTITIES\n" +
                        "5. If you see hostile mobs nearby, ACKNOWLEDGE them and decide to fight or flee\n" +
                        "6. If you have tools in inventory, USE them (they make tasks 3-10x faster)\n" +
                        "7. CRAFTING TABLE REQUIRED: To craft tools/weapons, you MUST place a crafting table first!\n" +
                        "   - Have crafting table in inventory but no table nearby? → place_crafting_table goal first!\n" +
                        "   - System auto-crafts the table from planks, but YOU must place it to craft tools!\n" +
                        "8. CHECK RECENT CHAT - if a player commanded you to do something, DO IT NOW (override other priorities)\n" +
                        "9. THINK AUTONOMOUSLY - if something is unreachable, gather blocks and build up! Don't wait for commands!\n" +
                        "10. ADAPT - if stuck doing the same thing repeatedly, CHANGE YOUR STRATEGY!\n\n" +
                        "=== YOUR RESPONSE ===\n" +
                        "THINK LIKE A REAL MINECRAFT PLAYER:\n" +
                        "1. CHECK RECENT CHAT - if a player commanded you, DO IT NOW (overrides everything)\n" +
                        "2. ANALYZE YOUR SITUATION - what do you need? What's blocking you?\n" +
                        "3. SOLVE PROBLEMS AUTONOMOUSLY - if something is unreachable, gather blocks and build up!\n" +
                        "4. ADAPT YOUR STRATEGY - if stuck, try something different!\n" +
                        "5. BE CREATIVE - you have FULL PLAYER FREEDOM to build, farm, explore, or do anything!\n\n" +
                        "Return ONLY JSON:\n" +
                        "{\"actions\":[{\"type\":\"set_goal\",\"goal\":\"<any_goal_from_above>\",\"minutes\":3}]}\n\n" +
                        "EXAMPLES:\n\n" +
                        "PLAYER COMMANDS (override everything):\n" +
                        "• Player commanded 'get dirt and build scaffold' → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"mine_stone\",\"minutes\":2}]} (mine dirt/stone blocks to build with)\n" +
                        "• Player commanded 'mine stone' → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"mine_stone\",\"minutes\":3}]} (do what they asked)\n" +
                        "• Player commanded 'build a house' → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"build_shelter\",\"minutes\":5}]} (build it)\n\n" +
                        "AUTONOMOUS PROBLEM-SOLVING (no player command needed):\n" +
                        "• Gathering wood BUT tree is 5 blocks high AND have 0 dirt → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"mine_stone\",\"minutes\":2}]} (get blocks to pillar up first!)\n" +
                        "• Gathering wood BUT tree is 5 blocks high AND have 10 dirt → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"gather_wood\",\"minutes\":3}]} (use dirt to pillar up!)\n" +
                        "• Want to build shelter BUT have 0 blocks → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"mine_stone\",\"minutes\":3}]} (get cobblestone first!)\n" +
                        "• Have crafting table in inventory BUT no table nearby → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"place_crafting_table\",\"minutes\":1}]} (place it to craft tools!)\n" +
                        "• Have planks+sticks BUT no pickaxe AND no table nearby → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"place_crafting_table\",\"minutes\":1}]} (place table first!)\n" +
                        "• Stuck trying same thing for 30+ seconds → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"explore\",\"minutes\":2}]} (try something different!)\n" +
                        "• See oak log at Y=65, I'm at Y=59, have 0 blocks → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"mine_stone\",\"minutes\":2}]} (get dirt to build up!)\n\n" +
                        "SURVIVAL PRIORITIES:\n" +
                        "• Hunger < 6 AND Food = 0 → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"hunt_animals\",\"minutes\":3}]} (hunt for food)\n" +
                        "• Wood < 8 → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"gather_wood\",\"minutes\":3}]}\n" +
                        "• Stone < 16 → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"mine_stone\",\"minutes\":3}]}\n" +
                        "• Night → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"build_shelter\",\"minutes\":3}]}\n\n" +
                        "CREATIVE GAMEPLAY:\n" +
                        "• Have seeds → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"farm\",\"minutes\":5}]} (plant crops)\n" +
                        "• Have wheat → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"breed_animals\",\"minutes\":3}]} (breed cows/sheep)\n" +
                        "• Have fishing rod → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"fish\",\"minutes\":5}]} (catch fish)\n" +
                        "• Have saplings → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"plant_trees\",\"minutes\":3}]} (reforest)\n" +
                        "• Have blocks → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"build_house\",\"minutes\":5}]} (build shelter)\n" +
                        "• Need iron but have excess wood → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"trade_bots\",\"minutes\":2}]} (trade with other bots)\n" +
                        "• Inventory full → {\"actions\":[{\"type\":\"set_goal\",\"goal\":\"manage_resources\",\"minutes\":2}]} (store in shared chest)\n";



                // Run the network call off-thread
        st.pending = CompletableFuture.supplyAsync(() -> {
            try {
                UnifiedLLMClient client = new UnifiedLLMClient(finalProvider, finalApiKey, finalModel);
                String response = client.chat(
                    List.of(Map.of("role", "system", "content", prompt)),
                    500
                );
                // Extract JSON from response
                String json = extractFirstJsonObject(response);
                return SimpleJson.parseActionPlan(json);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // CRITICAL FIX: Set cooldown to prevent infinite loop
        // If LLM request fails or takes time, don't spam requests every tick
        st.nextThinkTick = tick + 40; // Wait 2 seconds (40 ticks) before thinking again
        st.lastError = "";
    }

    private static String norm(String s) {
        return (s == null) ? "" : s.trim().toLowerCase();
    }

    private static String extractFirstJsonObject(String s) {
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b < 0 || b <= a) throw new RuntimeException("No JSON object found");
        return s.substring(a, b + 1);
    }

    /**
     * Add a chat message to the bot's history
     */
    public static void addChatMessage(String botName, String sender, String message) {
        State st = stateForName(botName);
        String formattedMsg = "<" + sender + "> " + message;
        st.chatHistory.add(formattedMsg);

        // Keep only last 20 messages
        if (st.chatHistory.size() > 20) {
            st.chatHistory.removeFirst();
        }
    }

    /**
     * Process a chat command directed at the bot
     * Returns true if the bot will obey, false if it chooses to ignore
     */
    public static CompletableFuture<Boolean> processChatCommand(String botName, String sender, String command, BotPair pair) {
        State st = stateForName(botName);

        // Add to chat history
        addChatMessage(botName, sender, command);

        // Get LLM configuration
        String apiKey;
        String model;

        System.out.println("[AMB] Processing chat command for bot: " + botName + " using LLM: " + st.llmProvider);

        switch (st.llmProvider) {
            case GEMINI -> {
                apiKey = com.shayneomac08.automated_minecraft_bots.Config.GEMINI_KEY.get();
                model = com.shayneomac08.automated_minecraft_bots.Config.GEMINI_MODEL.get();
                System.out.println("[AMB] Chat - Gemini - Model: " + model + ", Key length: " + (apiKey != null ? apiKey.length() : 0));
            }
            case GROK -> {
                apiKey = com.shayneomac08.automated_minecraft_bots.Config.GROK_KEY.get();
                model = com.shayneomac08.automated_minecraft_bots.Config.GROK_MODEL.get();
                System.out.println("[AMB] Chat - Grok - Model: " + model + ", Key length: " + (apiKey != null ? apiKey.length() : 0));
            }
            default -> {
                apiKey = com.shayneomac08.automated_minecraft_bots.Config.OPENAI_KEY.get();
                model = com.shayneomac08.automated_minecraft_bots.Config.OPENAI_MODEL.get();
                System.out.println("[AMB] Chat - OpenAI - Model: " + model + ", Key length: " + (apiKey != null ? apiKey.length() : 0));
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[AMB] Chat command aborted - API key is null or blank");
            return CompletableFuture.completedFuture(false);
        }

        final String finalApiKey = apiKey;
        final String finalModel = model;
        final LLMProvider finalProvider = st.llmProvider;

        // Ask LLM if bot wants to obey this command
        return CompletableFuture.supplyAsync(() -> {
            try {
                // CHAT VERIFICATION: Include what bot is actually doing
                String verificationContext = getVerificationContext(botName);

                String prompt = "You are " + botName + ", an autonomous AI entity in Minecraft.\n" +
                        "A player named '" + sender + "' just said: \"" + command + "\"\n\n" +
                        verificationContext + "\n" +
                        "You have free will and can choose whether to obey commands. Consider:\n" +
                        "- Is this a reasonable request?\n" +
                        "- Do you trust this player?\n" +
                        "- Are you busy with something important?\n" +
                        "- Does this align with your survival needs?\n\n" +
                        "When responding, be TRUTHFUL about what you're doing (see ACTUAL ACTIONS above).\n" +
                        "Don't claim to be doing something you're not actually doing.\n\n" +
                        "Respond with ONLY a JSON object:\n" +
                        "{\"obey\": true/false, \"response\": \"your chat message to the player\"}\n\n" +
                        "Example responses:\n" +
                        "{\"obey\": true, \"response\": \"Sure, I'll help you chop trees!\"}\n" +
                        "{\"obey\": false, \"response\": \"Sorry, I'm busy gathering food right now.\"}\n";

                UnifiedLLMClient client = new UnifiedLLMClient(finalProvider, finalApiKey, finalModel);
                String response = client.chat(
                    List.of(Map.of("role", "system", "content", prompt)),
                    200
                );

                // Parse response
                Map<String, Object> json = null;
                try {
                    // Try to extract JSON from response
                    String jsonStr = extractFirstJsonObject(response);
                    json = SimpleJson.parseObject(jsonStr);
                } catch (Exception parseError) {
                    System.err.println("[AMB] Failed to parse LLM response as JSON: " + parseError.getMessage());
                    System.err.println("[AMB] Raw response: " + response);
                }

                if (json == null || json.isEmpty()) {
                    // Default to ignoring command if we can't parse the response
                    st.pendingChatMessages.add("Sorry, I'm having trouble understanding right now.");
                    return false;
                }

                // Safely extract values with proper type checking
                boolean obey = false;
                String chatResponse = "";

                Object obeyObj = json.get("obey");
                if (obeyObj instanceof Boolean) {
                    obey = (Boolean) obeyObj;
                }

                Object responseObj = json.get("response");
                if (responseObj instanceof String) {
                    chatResponse = (String) responseObj;
                }

                // Queue the bot's response
                if (!chatResponse.isEmpty()) {
                    st.pendingChatMessages.add(chatResponse);
                }

                return obey;
            } catch (Exception e) {
                System.err.println("[AMB] Error processing command: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Get the next pending chat message for the bot to send
     */
    public static String getNextChatMessage(String botName) {
        State st = stateForName(botName);
        return st.pendingChatMessages.poll();
    }

    /**
     * Interrupt the bot's current goal and make it re-think based on a player command
     * This is called when a bot agrees to obey a player's command
     */
    public static void interruptWithCommand(String botName, String playerName, String command) {
        State st = stateForName(botName);

        // Cancel current goal immediately
        st.goalUntilTick = 0;
        st.goalX = st.goalY = st.goalZ = null;

        // Cancel any pending LLM request
        if (st.pending != null && !st.pending.isDone()) {
            st.pending.cancel(true);
        }
        st.pending = null;

        // Force immediate re-think on next tick
        st.nextThinkTick = 0;

        // Add the command to chat history so the LLM sees it in context
        st.chatHistory.add(playerName + " commanded: " + command);

        System.out.println("[AMB] " + botName + " interrupted current goal to obey command from " + playerName);
    }

    /**
     * Set the LLM provider for a bot
     */
    public static void setLLMProvider(String botName, LLMProvider provider) {
        State st = stateForName(botName);
        st.llmProvider = provider;
    }

    /**
     * Give bot an immediate initial goal on spawn based on survival needs
     * UPDATED: Makes bots think like actual Minecraft players, not robots
     */
    public static void setInitialGoal(MinecraftServer server, String botName, BotPair pair) {
        final String keyName = norm(botName);
        final State st = stateForName(keyName);

        if (!st.autonomous) return;

        var body = pair.body();
        if (body == null || body.isRemoved()) return;

        // Update survival needs first
        if (body.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            BotSurvivalNeeds.updateNeeds(serverLevel, pair.hands(), body, st.survivalNeeds);
        }

        // Determine initial goal based on player-like thinking
        String initialGoal;
        int minutes;

        // Think like a player: "What would I do if I just spawned in Minecraft?"
        if (st.survivalNeeds.health < 5.0f) {
            initialGoal = "idle"; // Low health - need to recover
            minutes = 2;
        } else if (st.survivalNeeds.hunger < 6.0f) {
            initialGoal = "hunt_animals"; // Hungry - need food
            minutes = 3;
        } else if (st.survivalNeeds.woodCount < 16) {
            // Player thinking: "I need wood to make tools and survive"
            initialGoal = "gather_wood";
            minutes = 3; // Shorter duration - get what you need and move on
        } else if (st.survivalNeeds.stoneCount < 32) {
            // Player thinking: "I have wood, now I need stone for better tools"
            initialGoal = "mine_stone";
            minutes = 4;
        } else {
            // Player thinking: "I have basics, let me explore and find resources"
            initialGoal = "explore";
            minutes = 5;
        }

        // Create a simple action plan
        ActionPlan.Action action = new ActionPlan.Action(
            "set_goal",     // type
            null,           // text
            null,           // x
            null,           // y
            null,           // z
            null,           // speed
            null,           // seconds
            initialGoal,    // goal
            (double) minutes // minutes
        );

        ActionPlan plan = new ActionPlan(java.util.List.of(action));

        // Apply immediately
        ActionExecutor.apply(server, keyName, st, pair, plan);

        System.out.println("[AMB] " + botName + " spawned with player mindset: " + initialGoal + " (thinking: " + getPlayerThought(initialGoal) + ")");
    }

    /**
     * Get what a player would be thinking when doing this goal
     */
    private static String getPlayerThought(String goal) {
        return switch (goal) {
            case "gather_wood" -> "Need wood for tools and crafting";
            case "mine_stone" -> "Time to upgrade to stone tools";
            case "hunt_animals" -> "I'm hungry, need food";
            case "shear_sheep" -> "Gathering wool from sheep";
            case "tame_animals" -> "Taming animals";
            case "gather_flowers" -> "Gathering flowers for decoration";
            case "explore" -> "Let's see what's around here";
            case "idle" -> "Need to rest and recover";
            default -> "Let's get to work";
        };
    }

    /**
     * Get recent chat history for context
     */
    public static String getChatHistoryContext(String botName) {
        State st = stateForName(botName);
        if (st.chatHistory.isEmpty()) {
            return "No recent chat.";
        }
        return "Recent chat:\n" + String.join("\n", st.chatHistory);
    }

    // ==================== CHAT VERIFICATION SYSTEM ====================

    /**
     * Record an action the bot actually performed (for chat verification)
     */
    public static void recordAction(String botName, String action) {
        State st = stateForName(botName);
        st.recentActions.add(action);

        // Keep only last 10 actions
        if (st.recentActions.size() > 10) {
            st.recentActions.remove(0);
        }
    }

    /**
     * Record resources gathered (for chat verification)
     */
    public static void recordResourceGathered(String botName, String resourceType, int amount) {
        State st = stateForName(botName);
        st.resourcesGathered.merge(resourceType, amount, Integer::sum);
    }

    /**
     * Update current activity (for chat verification)
     */
    public static void updateActivity(String botName, String activity) {
        State st = stateForName(botName);
        if (!activity.equals(st.currentActivity)) {
            st.currentActivity = activity;
            st.ticksOnCurrentActivity = 0;
        } else {
            st.ticksOnCurrentActivity++;
        }
    }

    /**
     * Get inventory summary for LLM awareness
     */
    private static String getInventorySummary(net.neoforged.neoforge.common.util.FakePlayer hands) {
        if (hands == null) return "Empty inventory";

        StringBuilder sb = new StringBuilder();
        int itemCount = 0;
        boolean hasCraftingTable = false;

        for (int i = 0; i < hands.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = hands.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                sb.append("- ").append(stack.getCount()).append("x ").append(stack.getHoverName().getString()).append("\n");
                itemCount++;

                // Check if we have a crafting table
                if (stack.getItem().toString().contains("crafting_table")) {
                    hasCraftingTable = true;
                }
            }
        }

        if (itemCount == 0) {
            return "Empty inventory (no items)";
        }

        // Add reminder if we have crafting table but haven't placed it
        if (hasCraftingTable) {
            sb.append("\n⚠️ IMPORTANT: You have a crafting table in inventory! Place it to craft tools!");
        }

        return sb.toString().trim();
    }

    // PERFORMANCE: Cache entity searches to prevent lag
    private static final java.util.Map<String, CachedEntitySearch> entitySearchCache = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CachedEntitySearch {
        String result;
        long timestamp;

        CachedEntitySearch(String result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }
    }

    /**
     * Get nearby entities description for LLM awareness
     * PERFORMANCE: Cached for 2 seconds (40 ticks) to prevent expensive searches every LLM call
     */
    private static String getNearbyEntitiesDescription(net.minecraft.world.entity.LivingEntity body) {
        if (body == null) return "No entities nearby";

        String botName = body.getName().getString();
        long currentTime = System.currentTimeMillis();

        // Check cache (2 second TTL)
        CachedEntitySearch cached = entitySearchCache.get(botName);
        if (cached != null && (currentTime - cached.timestamp) < 2000) {
            return cached.result;
        }

        StringBuilder sb = new StringBuilder();

        // Find hostile mobs within 16 blocks
        java.util.List<net.minecraft.world.entity.monster.Monster> hostiles = body.level().getEntitiesOfClass(
            net.minecraft.world.entity.monster.Monster.class,
            body.getBoundingBox().inflate(16.0)
        );

        if (!hostiles.isEmpty()) {
            sb.append("⚠️ HOSTILE MOBS NEARBY:\n");
            for (net.minecraft.world.entity.monster.Monster mob : hostiles) {
                double dist = Math.sqrt(body.distanceToSqr(mob));
                sb.append("- ").append(mob.getName().getString())
                  .append(" (").append(String.format("%.1f", dist)).append(" blocks away)\n");
            }
        }

        // Find passive animals within 16 blocks
        java.util.List<net.minecraft.world.entity.animal.Animal> animals = body.level().getEntitiesOfClass(
            net.minecraft.world.entity.animal.Animal.class,
            body.getBoundingBox().inflate(16.0)
        );

        if (!animals.isEmpty()) {
            sb.append("🐄 ANIMALS NEARBY (can hunt for food):\n");
            int count = 0;
            for (net.minecraft.world.entity.animal.Animal animal : animals) {
                if (count >= 5) break; // Limit to 5 to avoid spam
                double dist = Math.sqrt(body.distanceToSqr(animal));
                sb.append("- ").append(animal.getName().getString())
                  .append(" (").append(String.format("%.1f", dist)).append(" blocks away)\n");
                count++;
            }
            if (animals.size() > 5) {
                sb.append("- ... and ").append(animals.size() - 5).append(" more\n");
            }
        }

        // Find other bots within 16 blocks
        java.util.List<com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity> bots = body.level().getEntitiesOfClass(
            com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity.class,
            body.getBoundingBox().inflate(16.0)
        );

        if (bots.size() > 1) { // More than just this bot
            sb.append("🤖 OTHER BOTS NEARBY (can trade/coordinate):\n");
            for (com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity bot : bots) {
                if (bot == body) continue; // Skip self
                double dist = Math.sqrt(body.distanceToSqr(bot));
                sb.append("- ").append(bot.getName().getString())
                  .append(" (").append(String.format("%.1f", dist)).append(" blocks away)\n");
            }
        }

        String result = sb.length() == 0 ? "No entities nearby (safe area)" : sb.toString().trim();

        // Cache the result
        entitySearchCache.put(botName, new CachedEntitySearch(result, currentTime));

        return result;
    }

    /**
     * Get verification context for chat responses
     * This helps the AI know what the bot has ACTUALLY done
     */
    public static String getVerificationContext(String botName) {
        State st = stateForName(botName);
        StringBuilder context = new StringBuilder();

        context.append("=== ACTUAL ACTIONS (TRUTH) ===\n");
        context.append("Current Activity: ").append(st.currentActivity);
        context.append(" (for ").append(st.ticksOnCurrentActivity / 20).append(" seconds)\n");

        if (!st.recentActions.isEmpty()) {
            context.append("Recent Actions:\n");
            for (String action : st.recentActions) {
                context.append("  - ").append(action).append("\n");
            }
        }

        if (!st.resourcesGathered.isEmpty()) {
            context.append("Resources Gathered:\n");
            for (Map.Entry<String, Integer> entry : st.resourcesGathered.entrySet()) {
                context.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        // PERFORMANCE: Reuse cached entity search instead of doing another expensive search
        // Add nearby mobs/threats from cache
        com.shayneomac08.automated_minecraft_bots.bot.BotPair pair = com.shayneomac08.automated_minecraft_bots.bot.BotRegistry.get(botName);
        if (pair != null && pair.body() != null && !pair.body().level().isClientSide()) {
            // Use cached entity description instead of doing another search
            String cachedEntities = getNearbyEntitiesDescription(pair.body());
            if (cachedEntities.contains("HOSTILE MOBS")) {
                context.append("\n=== NEARBY THREATS ===\n");
                context.append("WARNING: Hostile mobs nearby! Consider fighting or fleeing.\n");
            }
        }

        context.append("\n=== TRUTHFULNESS RULES ===\n");
        context.append("CRITICAL: Only claim actions you've ACTUALLY performed (listed above).\n");
        context.append("If you haven't done something, say so honestly or make it clear you're planning to do it.\n");
        context.append("Personality-based lying (joking, hiding info) is OK, but don't claim false accomplishments.\n\n");
        context.append("NEVER SAY:\n");
        context.append("• 'Dev gave me X' (unless player actually used /amb give command)\n");
        context.append("• 'I opened the door' (unless you actually opened a door)\n");
        context.append("• 'I have X' (unless it's in your inventory above)\n\n");
        context.append("ALWAYS SAY:\n");
        context.append("• 'I crafted X' (if you crafted it yourself)\n");
        context.append("• 'I gathered X' or 'I found X' (if you collected it yourself)\n");
        context.append("• 'I'm looking for X' (if you need something)\n");

        return context.toString();
    }

    /**
     * Clear resource tracking (e.g., when bot stores items in chest)
     */
    public static void clearResourceTracking(String botName) {
        State st = stateForName(botName);
        st.resourcesGathered.clear();
    }
}
