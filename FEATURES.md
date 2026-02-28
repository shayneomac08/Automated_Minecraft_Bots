# 🎯 Features Documentation - Current Reality

## 📋 Overview
Complete documentation of all bot features, capabilities, and systems.

**Current Version:** 1.0.6 - Lifespan, Breeding & Personality System
**Last Updated:** February 2026
**Status:** Production ready with lifespan system, breeding mechanics, and personality traits

---

## 🧠 Autonomous AI System

### Smart Decision-Making
Bots assess their needs every 30 seconds (reduced LLM calls) and choose tasks based on priorities:

**Priority Order:**
1. **Health < 12** → Farm (need food/health)
2. **Health < 15** → Mine stone (need tools/shelter)
3. **Logs < 16** → Gather wood (double needed)
4. **Cobblestone < 32** → Mine stone (double needed)
5. **Inventory > 75% full** → Build chest

**Key Features:**
- ✅ Real player movement with auto-sprint (smooth, no mob stiffness)
- ✅ Automatic vacuum pickup (2 block radius)
- ✅ Real fall damage (3+ blocks)
- ✅ Real respawn on death
- ✅ Smart needs-based decision making (30 second interval)
- ✅ Held items render properly in hand
- ✅ Enhanced line-of-sight vision system
- ✅ **Knowledge sharing between bots** (8 block radius)
- ✅ **Group system** (grok_group, gemini_group, chatgpt_group)
- ✅ **Memory system** (remembers discovered items and locations)
- ✅ **Bartering/negotiation** (trade resources for knowledge)

### Console Feedback
```
[AMB] Steve assessed needs → chose: gather_wood
```

Shows bot's current thinking and chosen task.

---

## 🌲 Resource Gathering

### Wood Gathering
- **Range:** 48 blocks horizontal, -12 to +60 vertical
- **Target:** 16 logs (all log types)
- **Features:**
  - Chops entire tree (finds all connected logs)
  - Clears leaves blocking path
  - Auto-picks up dropped items
  - Uses ItemTags for accurate counting (oak, birch, spruce, jungle, acacia, dark oak, cherry, mangrove)

### Stone Mining
- **Range:** 48 blocks horizontal, -12 to +60 vertical
- **Target:** 32 cobblestone
- **Features:**
  - Finds nearest stone/cobblestone
  - Uses best available pickaxe
  - Auto-crafts stone tools when needed
  - Efficient pathfinding

### Ore Mining
- **Range:** 48 blocks horizontal, -12 to +60 vertical
- **Ores:** Iron, Diamond, Gold, Redstone, Lapis, Emerald, Coal
- **Features:**
  - Finds entire ore vein (connected ore blocks)
  - Uses appropriate tool tier
  - Auto-smelts if furnace available
  - Prioritizes valuable ores

### Dirt Digging
- **Range:** 48 blocks horizontal, -12 to +60 vertical
- **Features:**
  - Clears land efficiently
  - Uses shovel if available
  - Auto-picks up dropped items

---

## 🔨 Crafting & Tool System

### Auto-Crafting
Bots automatically craft tools when needed:

**Tool Progression:**
1. **Wooden Tools** - Crafted from logs + sticks
2. **Stone Tools** - Crafted from cobblestone + sticks
3. **Iron Tools** - Crafted from iron ingots + sticks
4. **Diamond Tools** - Crafted from diamonds + sticks

**Tools Crafted:**
- Pickaxes (mining stone, ores)
- Axes (chopping wood)
- Shovels (digging dirt)

### Tool Durability System
- **Monitoring:** Checks tool damage every use
- **Threshold:** Replaces tools at <5% durability or <10 uses remaining
- **Auto-Switch:** Switches to backup tools automatically
- **Console Logging:** Shows durability status

**Example:**
```
[AMBTaskGoal] Tool durability: 50/250 (20%) - OK
[AMBTaskGoal] Tool durability: 10/250 (4%) - REPLACING
```

### Recipe Book System
**68+ Recipes Unlocked:**

**Tools:**
- Wooden: Pickaxe, Axe, Shovel, Sword, Hoe
- Stone: Pickaxe, Axe, Shovel, Sword, Hoe
- Iron: Pickaxe, Axe, Shovel, Sword, Hoe
- Diamond: Pickaxe, Axe, Shovel, Sword, Hoe
- Golden: Pickaxe, Axe, Shovel, Sword, Hoe

**Armor:**
- Leather: Helmet, Chestplate, Leggings, Boots
- Iron: Helmet, Chestplate, Leggings, Boots
- Diamond: Helmet, Chestplate, Leggings, Boots
- Golden: Helmet, Chestplate, Leggings, Boots

**Building:**
- Crafting Table, Furnace, Chest, Torch
- Ladder, Door, Trapdoor, Fence, Gate

**Food:**
- Bread, Cake, Cookie, Pumpkin Pie

**Utility:**
- Bucket, Shears, Fishing Rod, Compass, Clock
- Bed (all colors), Painting, Item Frame

---

## 🏗️ Building & Storage

### Auto-Chest Building
- **Trigger:** Inventory 75% full (27/36 slots)
- **Requirements:** 8 oak planks
- **Process:**
  1. Navigates to safe location
  2. Places chest block
  3. Splits item stacks (leaves half in inventory)
  4. Returns to autonomous mode

**Console Output:**
```
[AMB] Steve BUILT A REAL CHEST at (100, 70, 200) and stored excess items!
```

### Persistent Inventory
- **Save System:** Inventory saved to NBT data
- **Persistence:** Survives server restarts
- **Respawn:** Bot respawns with same inventory

---

## 🌾 Farming System

### Wheat Farming
- **Process:**
  1. Finds or creates farmland
  2. Plants wheat seeds
  3. Waits for crops to grow
  4. Harvests when fully grown
  5. Replants automatically

- **Features:**
  - Auto-creates farmland with hoe
  - Efficient crop layout
  - Auto-picks up drops
  - Continuous farming cycle

---

## 🔥 Smelting System

### Furnace Interaction
- **Process:**
  1. Finds nearest furnace
  2. Places ores in input slot
  3. Places fuel (coal/wood) in fuel slot
  4. Waits for smelting
  5. Collects smelted items

- **Supported Items:**
  - Iron Ore → Iron Ingot
  - Gold Ore → Gold Ingot
  - Raw Iron → Iron Ingot
  - Raw Gold → Gold Ingot
  - Sand → Glass
  - Cobblestone → Stone

---

## 🤝 Villager Trading

### Auto-Trading
- **Range:** Scans for villagers within 10 blocks
- **Process:**
  1. Finds nearest villager
  2. Checks available trades
  3. Executes beneficial trades
  4. Collects traded items

**Note:** Simplified system (works without actual villager entities due to NeoForge 1.21.1 API limitations)

---

## 🚶 Movement & Physics

### Spawning System (IMPROVED in 1.0.5)
- **Proper Height:** Bots spawn at player Y + 0.5 (no ground clipping)
- **Position:** Spawns at exact player location
- **Method:** `spawnAtPlayer()` static method for clean spawning
- **Console Output:** "Spawned Steve at player height like a real player"

### Player-Like Movement
- **Control:** Uses MoveControl for smooth movement
- **Pathfinding:** Intelligent navigation
- **Auto-Sprint:** Automatically sprints when moving (horizontalDistanceSqr > 0.01)
- **Features:**
  - Can open doors
  - Can float/swim
  - 2x node visits for long-range pathfinding
  - Natural walking/running
  - Real player speed (0.125 base, 25% faster than vanilla player)

### Fall Damage System
- **Threshold:** 3+ blocks
- **Calculation:** Damage = (fall distance - 3.0)
- **Reset:** Fall distance resets after damage
- **Example:** 5-block fall = 2 damage

### Respawn System
- **Trigger:** Bot dies (health = 0)
- **Process:**
  1. Restores health to 20.0
  2. Teleports to spawn point (0, 128, 0)
  3. Resets velocity
  4. Logs respawn event

**Console Output:**
```
[AMB] Steve died and respawned like a real player!
```

### Stuck Detection & Recovery
- **Detection:** Checks if bot hasn't moved (< 1cm in 1 second)
- **Recovery Strategies:**
  1. **40 ticks (2 sec):** Tiny nudge (0.04 blocks)
  2. **60 ticks (3 sec):** Jump (only if block is high)

**Console Output:**
```
[AMBTaskGoal] Bot stuck, attempting tiny nudge
```

---

## 🧬 Lifespan, Breeding & Personality System (NEW in 1.0.6)

### Lifespan System
Bots have a realistic lifespan system:

**Birth Time:**
- Each bot tracks their birth time in real-world milliseconds
- Age is calculated from spawn time

**Old Age:**
- Bots die of old age after **90 real days** (7,776,000,000 milliseconds)
- Old age deaths are permanent (no respawn)
- Nearby bots mourn the loss
- Tombstone created at death location (stone brick wall)

**Normal Deaths:**
- Deaths before old age result in respawn
- Bot respawns at (0, 128, 0) with full inventory intact
- Health restored to 20.0

**Check Age:**
```
/amb info <name>  # Shows age in days
```

### Breeding System
Bots can breed to create offspring:

**Requirements:**
- Two bots of opposite genders
- Population must be below 30 (cap)
- 50% chance of successful breeding

**Breeding Command:**
```
/amb breed <bot1> <bot2>
```

**Child Characteristics:**
- **Gender:** Randomly assigned (male or female)
- **Personality:** Inherits traits from both parents
- **Group:** Randomly assigned to openai_group, grok_group, or gemini_group
- **Role:** Starts as "worker"
- **Name:** Auto-generated as "child_XXX" (random number)

**Inheritance:**
- First trait from parent 1
- Second trait from parent 2
- Third trait is "inherited"

**Console Output:**
```
[AMB] Steve and Alex had a child: child_742 (grok) with traits: [brave, generous, inherited] (Population: 15/30)
```

### Gender System
Each bot has a gender that affects breeding:

**Available Genders:**
- `male` - Male bot
- `female` - Female bot
- `none` - No gender (default for old bots)

**Gender Assignment:**
- Randomly assigned at spawn (50/50 chance)
- Persistent (saved with bot data)
- Displayed in `/amb info <name>`

**Breeding Rules:**
- Same gender cannot breed
- Opposite genders can attempt breeding

### Personality Traits System
Each bot has 3 unique personality traits:

**Available Traits:**
- `curious` - Explores more
- `generous` - Shares freely
- `selfish` - Hoards resources
- `brave` - Takes risks
- `cautious` - Avoids danger
- `creative` - Builds more
- `lazy` - Works slower
- `hardworking` - Works faster
- `funny` - Lighthearted
- `serious` - Focused
- `loyal` - Stays with group
- `independent` - Works alone

**Trait Generation:**
- 3 random traits assigned at spawn
- Traits are inherited from parents during breeding
- Persistent (saved with bot data)

**View Traits:**
```
/amb info <name>  # Shows personality traits
```

### Population Management
The mod enforces a population cap to prevent server overload:

**Population Cap:**
- Maximum: **30 bots**
- Prevents spawning when cap is reached
- Prevents breeding when cap is reached

**Check Population:**
```
/amb population  # Shows current count (X/30)
```

**Console Messages:**
```
[AMB] Population cap of 30 reached - cannot spawn more bots
[AMB] Population cap reached - cannot breed
```

**Population Tracking:**
- Increments on spawn
- Increments on successful breeding
- Decrements on old age death
- Global counter (shared across all bots)

### Death & Mourning System
When a bot dies of old age:

**Mourning:**
- Console message: "The group mourns the loss of Steve who lived a full life."
- All bots within 16 blocks are notified
- Each nearby bot logs: "Alex mourns the loss of Steve"

**Tombstone:**
- Stone brick wall placed at death location (one block below)
- Console message: "Steve received a tombstone at (X, Y, Z)"
- Permanent marker of the bot's life

**Old Age vs Normal Death:**
- **Old Age (90+ days):** Permanent removal, mourning, tombstone, population decrements
- **Normal Death (<90 days):** Respawn at (0, 128, 0), inventory intact, no mourning

---

## 🤝 Knowledge Sharing & Group System

### Group System
Bots can be organized into groups for collaborative work:

**Available Groups:**
- `none` - Default, shares with all bots
- `grok_group` - Grok AI personality group
- `gemini_group` - Gemini AI personality group
- `chatgpt_group` - ChatGPT AI personality group

**Set Group:**
```
/amb group <bot_name> <group_name>
```

### Social Hierarchies (NEW in 1.0.5)
Bots can have different roles that affect how they share knowledge:

**Available Roles:**
- `worker` - Default role, uses group-based trust mechanics
- `leader` - Shares knowledge freely with everyone (no restrictions)
- `scout` - Always barters for knowledge (trades resources)

**Set Role:**
```
/amb role <bot_name> <role>
```

**Role Behaviors:**
- **Leaders:** Share all knowledge freely with any bot (ignores group boundaries)
- **Scouts:** Always negotiate/barter (asks for 8 logs in exchange for knowledge)
- **Workers:** Use group trust (same group = free, cross-group = 60% barter / 40% refuse)

### Memory System
Bots remember what they discover:

**Discovered Items:**
- Automatically remembers items picked up
- Stores item type and location
- Uses memory for future decisions

**Known Resources:**
- Tracks resource locations (iron ore, diamonds, etc.)
- Shares locations with group members
- Uses shared knowledge to find resources faster

**Map Memory (NEW in 1.0.4):**
- Creates filled maps for discovered resources
- Shares maps with other bots
- Stores map references in resourceMaps HashMap

### Personality Dialects
Bots communicate with unique personality styles:

**Available Dialects:**
- `neutral` - Standard communication (default)
- `grok` - Casual, edgy style: "Hey, I found iron_ore — just saying."
- `gemini` - Enthusiastic, poetic: "✨ I found iron_ore — what a beautiful discovery, don't you think?"
- `chatgpt` - Polite, helpful: "Hello! I found iron_ore. I hope this helps you."

**Set Dialect:**
```
/amb dialect <bot_name> <dialect>
```

### Map Memory System
Bots create maps for discovered resources:

**Map Creation:**
- Automatically creates filled map when discovering new resource
- Stores map reference in memory
- Maps shared with knowledge during communication
- Console shows: "Steve created a map for minecraft:iron_ore"

### Knowledge Sharing
Bots automatically share knowledge with nearby group members:

**Sharing Mechanics:**
- **Range:** 8 blocks
- **Frequency:** Every 30 seconds (600 ticks)
- **Share Chance:** 70% by default (LLM can override)
- **Trust System:** Same group = free sharing, cross-group = barter or refuse

**What Gets Shared:**
- Item discoveries ("I found iron_ore at X, Y, Z")
- Resource locations
- Recipe discoveries
- Exploration data

**Console Output:**
```
[AMB] Steve shared 3 knowledge items with Alex
[AMB] Alex learned: I found iron_ore at BlockPos{x=100, y=12, z=-50}
```

### Bartering & Negotiation
Bots use trust mechanics for knowledge exchange:

**Trust System:**
- **Same Group:** Free sharing, no cost
- **Cross-Group:** 60% chance to barter, 40% chance to refuse
- **Barter Cost:** 8 logs for knowledge
- **Refusal:** "Being an asshole today" - won't share

**Barter Mechanics:**
- Bot checks if other has 8+ logs
- If yes: removes 8 logs, shares knowledge
- If no: refuses to share

**Console Output (Same Group):**
```
[AMB] Steve shared 3 knowledge items with Alex (same group)
```

**Console Output (Cross-Group Barter):**
```
[AMB] Steve bartered knowledge for 8 logs with Alex
```

**Console Output (Refusal):**
```
[AMB] Steve refused to share with Alex — being an asshole today
[AMB] Steve refused to share — Alex had nothing good to trade
```

### Smart Decision Making with Shared Knowledge
Bots use shared knowledge to make better decisions:

**Example:**
- Bot learns iron ore location from group member
- Prioritizes mining when iron ore count is low
- Goes directly to known location instead of searching

**Console Output:**
```
[AMB] Steve using shared knowledge to find iron ore!
```

### Persistence
All knowledge and group data is saved:

**Saved Data:**
- Group membership
- Discovered recipes
- Known resource locations
- Shared knowledge

**Loaded on Restart:**
- Bots remember their group
- Retain all discovered knowledge
- Keep resource location memory

---

## 💬 LLM Integration

### Supported Providers
- **OpenAI** - GPT-3.5, GPT-4
- **Anthropic** - Claude models
- **Ollama** - Local models

### Chat Features
- Natural language commands
- Intelligent responses
- Task execution from chat
- Context-aware conversations

**Example:**
```
Player: /amb chat Steve gather some wood for me
Steve: I'll gather wood for you right away!
[AMB] Steve assessed needs → chose: gather_wood
```

---

## 📊 Advanced Features

### Vision System
- **Horizontal Range:** 48 blocks
- **Vertical Range:** -12 to +60 blocks
- **Block Detection:** Finds nearest valid blocks for tasks
- **Obstacle Avoidance:** Clears leaves and other obstacles

### Inventory Management
- **Capacity:** 36 slots (player inventory)
- **Auto-Pickup:** Vacuum pickup within 2 blocks (like real players)
- **Smart Storage:** Builds chests when 75% full
- **Item Splitting:** Leaves half in inventory for continued work
- **GUI Access (NEW in 1.0.5):** Open bot inventory with `/amb gui <name>` command
- **Visual Rendering:** Held items render properly in bot's hand

### Task Switching
- **Cancellation:** Can cancel current task
- **Override:** Manual tasks override autonomous decisions
- **Completion:** Returns to autonomous mode after task completion
- **Priority:** Respects priority order (health → resources → inventory)

---

## 🎯 Performance Optimizations

### Pathfinding
- **Node Multiplier:** 2.0x for long-range pathfinding
- **Door Opening:** Enabled for better navigation
- **Floating:** Enabled for water navigation
- **Speed:** 1.3x movement speed

### Resource Gathering
- **Batch Processing:** Gathers multiple blocks per task
- **Efficient Scanning:** Scans in expanding radius
- **Smart Targeting:** Prioritizes nearest resources

### Jump Reduction
- **Threshold:** 60 ticks (3 seconds) stuck
- **Condition:** Only jumps if block is 2.5+ blocks higher
- **Result:** ~98% fewer jumps than previous versions

---

## 📈 Statistics & Monitoring

### Console Logging
All bot actions are logged to console:

```
[AMB] Steve assessed needs → chose: gather_wood (Wood:0, Food:20.0)
[AMB] AMBTaskGoal started: gather_wood targeting BlockPos{x=100, y=70, z=200}
[AMB] Found tree with 8 logs
[AMBTaskGoal] Broke block at (100, 70, 200) (total: 1)
[AMBTaskGoal] Tool durability: 50/250 (20%) - OK
[AMBTaskGoal] Broke block at (100, 75, 200) (total: 16)
[AMB] Steve has enough resources: 16 items
```

### Status Command
Use `/amb status <name>` to see:
- Current position
- Health
- Brain status (ON/OFF)
- Current task
- Inventory usage

---

## 🔧 Configuration

### Config File Location
`config/automated_minecraft_bots.toml`

### Available Settings
- LLM provider (OpenAI, Anthropic, Ollama)
- API keys
- Model selection
- Bot behavior settings

---

## 📚 See Also

- **[GETTING_STARTED.md](GETTING_STARTED.md)** - Quick start guide
- **[COMMANDS.md](COMMANDS.md)** - Command reference
- **[CONFIGURATION.md](CONFIGURATION.md)** - Configuration guide
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Development guide

---

**Complete Feature Documentation!** All bot capabilities explained. 🎯
