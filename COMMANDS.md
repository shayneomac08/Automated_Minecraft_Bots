# 📖 Complete Command Reference - Current State

## 🎮 Bot Management Commands

### `/amb spawn <name>`
Spawns a new bot at your location.

**Usage:**
```bash
/amb spawn Steve
/amb spawn Alex
/amb spawn Miner1
```

**Notes:**
- Bot spawns with empty inventory
- Bot brain is OFF by default
- Bot appears at player's current position

---

### `/amb remove <name>`
Permanently removes a bot from the world.

**Usage:**
```bash
/amb remove Steve
```

**Notes:**
- Bot is completely removed from the world
- Bot entity is discarded
- Global bot count is decremented
- Bot can be respawned later with `/amb spawn` (fresh start)

---

### `/amb brain <name> <on|off>`
Enables or disables autonomous AI decision-making.

**Usage:**
```bash
/amb brain Steve on
/amb brain Steve off
```

**When Brain is ON:**
- Bot assesses needs every 10 seconds
- Bot automatically chooses tasks based on priorities
- Bot gathers resources, crafts tools, builds chests

**When Brain is OFF:**
- Bot only follows manual task assignments
- Use `/amb task` to assign specific tasks

---

### `/amb brainall <on|off>` (NEW in 1.0.7)
Enables or disables autonomous AI for ALL bots at once.

**Usage:**
```bash
/amb brainall on
/amb brainall off
```

**Features:**
- Controls all bots simultaneously
- Sets both BotBrain and entity-level brain flags
- Shows count of affected bots
- Broadcasts to all players

**Output:**
```
[AMB] Turned ON brains for 4 bots
[AMB] Turned OFF brains for 4 bots
```

**Use Cases:**
- Pause all bots for building: `/amb brainall off`
- Resume all bots: `/amb brainall on`
- Debug individual bots: Turn all off, enable one specific bot
- Performance management: Reduce server load during intensive operations

---

### `/amb status <name>`
Shows bot's current status and information.

**Usage:**
```bash
/amb status Steve
```

**Output:**
```
Bot: Steve
Position: (100, 70, 200)
Health: 20.0/20.0
Brain: ON
Current Task: gather_wood
Inventory: 12/36 slots used
```

---

### `/amb list`
Lists all active bots in the world.

**Usage:**
```bash
/amb list
```

**Output:**
```
Active Bots:
- Steve (Brain: ON, Task: gather_wood)
- Alex (Brain: OFF, Task: none)
- Miner1 (Brain: ON, Task: mine_stone)
```

---

### `/amb gui <name>` (NEW in 1.0.5)
Opens a visual GUI showing the bot's inventory.

**Usage:**
```bash
/amb gui Steve
```

**Features:**
- 3-row chest interface (36 slots)
- Interactive - add/remove items directly
- Real-time updates
- Shows bot's name in title

**Notes:**
- Must be run by a player (not console)
- Changes reflect immediately in bot's inventory

---

### `/amb info <name>` (NEW in 1.0.6)
Shows detailed information about a bot including gender, age, traits, and more.

**Usage:**
```bash
/amb info Steve
```

**Output:**
```
[AMB] Steve Info:
  Gender: male
  Age: 5 days
  Role: leader
  Group: grok_group
  Dialect: grok
  Personality: [brave, generous, curious]
  Population: 12/30
```

**Information Displayed:**
- Gender (male/female/none)
- Age in real days
- Current role
- Group membership
- Personality dialect
- Personality traits
- Current population count

---

### `/amb population` (NEW in 1.0.6)
Shows the current bot population count.

**Usage:**
```bash
/amb population
```

**Output:**
```
[AMB] Current population: 12/30
```

**Notes:**
- Maximum population is 30 bots
- Prevents spawning/breeding when cap is reached

---

## 🧬 Breeding System Commands (NEW in 1.0.6)

### `/amb breed <bot1> <bot2>`
Attempts to breed two bots to create offspring.

**Usage:**
```bash
/amb breed Steve Alex
```

**Requirements:**
- Both bots must exist
- Bots must be opposite genders (male/female)
- Population must be below 30
- 50% chance of success

**Success Output:**
```
[AMB] Steve and Alex had a child: child_742 (grok) with traits: [brave, generous, inherited] (Population: 15/30)
```

**Failure Reasons:**
- Same gender: "Steve and Alex cannot breed (same gender)"
- Population cap: "Population cap reached - cannot breed"
- Random chance: No output (50% fail rate)

**Child Characteristics:**
- Gender: Randomly assigned (male or female)
- Personality: Inherits traits from both parents
- Group: Randomly assigned (openai_group, grok_group, or gemini_group)
- Role: Starts as "worker"
- Name: Auto-generated as "child_XXX"

**Notes:**
- Child spawns at parent 1's location
- Traits inherited: parent1[0], parent2[1], "inherited"
- Population counter increments on success

---

## 👥 Social System Commands (NEW in 1.0.5)

### `/amb role <name> <role>`
Sets the bot's role in the social hierarchy.

**Usage:**
```bash
/amb role Steve leader
/amb role Alex worker
/amb role Scout1 scout
```

**Available Roles:**
- `leader` - Shares knowledge freely with everyone (ignores group boundaries)
- `worker` - Uses group-based trust (same group = free, cross-group = barter/refuse)
- `scout` - Always barters for knowledge (trades 8 logs for information)

**Notes:**
- Role affects how bot shares knowledge with other bots
- Persistent (saved with bot data)

---

### `/amb dialect <name> <dialect>`
Sets the bot's personality dialect for communication.

**Usage:**
```bash
/amb dialect Steve grok
/amb dialect Alex gemini
/amb dialect Bot1 chatgpt
```

**Available Dialects:**
- `neutral` - Standard communication (default)
- `grok` - Casual, edgy: "Hey, I found iron_ore — just saying."
- `gemini` - Enthusiastic, poetic: "✨ I found iron_ore — what a beautiful discovery, don't you think?"
- `chatgpt` - Polite, helpful: "Hello! I found iron_ore. I hope this helps you."

**Notes:**
- Affects how bot communicates when sharing knowledge
- Persistent (saved with bot data)

---

### `/amb group <name> <group>`
Sets the bot's group for knowledge sharing.

**Usage:**
```bash
/amb group Steve grok_group
/amb group Alex gemini_group
/amb group Bot1 chatgpt_group
/amb group Bot2 none
```

**Available Groups:**
- `none` - Default, shares with all bots
- `grok_group` - Grok AI personality group
- `gemini_group` - Gemini AI personality group
- `chatgpt_group` - ChatGPT AI personality group
- Custom groups (any string)

**Notes:**
- Same group = free knowledge sharing
- Cross-group = 60% barter / 40% refuse (for workers)
- Leaders ignore group boundaries
- Persistent (saved with bot data)

---

## 🎯 Task Assignment Commands

### `/amb task <name> <task>`
Assigns a specific task to a bot.

**Available Tasks:**
- `gather_wood` - Gather logs from trees
- `mine_stone` - Mine cobblestone
- `mine_ore` - Mine ores (iron, diamond, gold, etc.)
- `mine_dirt` - Dig dirt
- `farm` - Farm wheat
- `smelt` - Smelt ores in furnace
- `stop` - Stop current task

**Usage:**
```bash
/amb task Steve gather_wood
/amb task Alex mine_stone
/amb task Miner1 mine_ore
/amb task Steve stop
```

**Notes:**
- Task overrides autonomous AI decisions (even if brain is ON)
- Bot will continue task until completion or stopped
- Use `stop` to return to autonomous mode

---

## 📦 Item Management Commands

### `/amb give <name> <item> <amount>`
Gives items to a bot's inventory.

**Usage:**
```bash
/amb give Steve diamond_pickaxe 1
/amb give Steve oak_planks 64
/amb give Steve iron_ingot 12
/amb give Steve cooked_beef 32
```

**Supported Items:**
- All Minecraft items (300+ items)
- 400+ aliases (e.g., "wood" → "oak_planks")
- Tools, blocks, food, ores, etc.

**Common Aliases:**
```bash
/amb give Steve wood 64          # oak_planks
/amb give Steve stone 64         # cobblestone
/amb give Steve pick 1           # diamond_pickaxe
/amb give Steve axe 1            # diamond_axe
/amb give Steve food 32          # cooked_beef
```

---

### `/amb inventory <name>`
Shows bot's current inventory.

**Usage:**
```bash
/amb inventory Steve
```

**Output:**
```
Steve's Inventory (12/36 slots):
- Oak Log x16
- Cobblestone x32
- Wooden Pickaxe x1 (50% durability)
- Stick x8
```

---

## 💬 LLM Integration Commands

### `/amb chat <name> <message>`
Chat with a bot using LLM (requires API key).

**Usage:**
```bash
/amb chat Steve gather some wood for me
/amb chat Steve what are you doing?
/amb chat Steve build a house
```

**Notes:**
- Requires LLM provider configured (OpenAI, Anthropic, or Ollama)
- Bot responds intelligently to natural language
- Bot can execute tasks based on chat commands

---

### `/amb llm <provider>`
Sets the LLM provider.

**Usage:**
```bash
/amb llm openai
/amb llm anthropic
/amb llm ollama
```

**Supported Providers:**
- `openai` - OpenAI GPT models
- `anthropic` - Anthropic Claude models
- `ollama` - Local Ollama models

**Configuration:**
- Set API keys in config file
- See CONFIGURATION.md for details

---

## 🛠️ Auto-Craft Control Commands (NEW in 1.0.15)

### `/amb autocraft <name> <on|off|status>`
Controls whether a bot automatically crafts items from materials.

**Usage:**
```bash
/amb autocraft Steve on       # Enable auto-crafting
/amb autocraft Steve off      # Disable auto-crafting (default)
/amb autocraft Steve status   # Check current status
```

**Default:** OFF (items stay in inventory as-is)

**When Enabled:**
- Logs → Planks (if planks < 16)
- Planks → Sticks (if sticks < 8)
- Auto-crafts tools when materials available
- Auto-crafts chests when inventory full

**When Disabled:**
- Items stay exactly as placed
- No automatic material conversion
- Full manual control

**Notes:**
- Setting persists across server restarts
- Prevents items from "disappearing" when placed in inventory
- Recommended to keep OFF unless you want automatic crafting

---

## 🔧 Advanced Commands

### `/amb teleport <name> <x> <y> <z>`
Teleports a bot to specific coordinates.

**Usage:**
```bash
/amb teleport Steve 100 70 200
/amb teleport Steve ~ ~5 ~
```

---

### `/amb heal <name>`
Fully heals a bot.

**Usage:**
```bash
/amb heal Steve
```

**Notes:**
- Restores health to 20.0
- Useful for testing or recovery

---

### `/amb clear <name>`
Clears a bot's inventory.

**Usage:**
```bash
/amb clear Steve
```

**Warning:** This permanently deletes all items in bot's inventory!

---

## 📊 Task-Specific Details

### Gather Wood (`gather_wood`)
- Finds nearest tree within 48 blocks horizontal, -12 to +60 vertical
- Chops entire tree (all connected logs)
- Clears leaves blocking path
- Collects 16 logs before stopping
- Auto-crafts wooden tools if needed

### Mine Stone (`mine_stone`)
- Finds nearest stone/cobblestone
- Mines 32 cobblestone before stopping
- Auto-crafts stone tools if needed
- Uses best available pickaxe

### Mine Ore (`mine_ore`)
- Finds nearest ore (iron, diamond, gold, etc.)
- Mines entire ore vein
- Auto-smelts ores if furnace available
- Uses best available pickaxe

### Farm (`farm`)
- Finds or creates farmland
- Plants wheat seeds
- Waits for crops to grow
- Harvests when fully grown
- Replants automatically

### Smelt (`smelt`)
- Finds nearest furnace
- Smelts ores (iron, gold, etc.)
- Uses coal or wood as fuel
- Collects smelted items

---

## 🎯 Command Examples

### Complete Workflow
```bash
# 1. Spawn and setup
/amb spawn Steve
/amb brain Steve on

# 2. Give starting materials
/amb give Steve oak_planks 8
/amb give Steve cooked_beef 16

# 3. Check status
/amb status Steve
/amb inventory Steve

# 4. Manual task override
/amb task Steve gather_wood

# 5. Stop and return to autonomous mode
/amb task Steve stop

# 6. Despawn when done
/amb despawn Steve
```

### Multiple Bots
```bash
# Spawn multiple bots
/amb spawn Miner1
/amb spawn Miner2
/amb spawn Farmer1

# Assign different tasks
/amb task Miner1 mine_stone
/amb task Miner2 mine_ore
/amb task Farmer1 farm

# Enable autonomous mode for all
/amb brain Miner1 on
/amb brain Miner2 on
/amb brain Farmer1 on

# Check all bots
/amb list
```

### LLM Chat
```bash
# Setup LLM
/amb llm openai

# Chat with bot
/amb chat Steve gather 32 logs for me
/amb chat Steve what's in your inventory?
/amb chat Steve build a chest and store your items
```

---

## 💡 Tips

### Efficient Commands
- Use tab completion for bot names
- Use aliases for common items (wood, stone, pick, axe)
- Chain commands with semicolons in some terminals

### Debugging
- Use `/amb status <name>` to check current state
- Watch console for "[AMB]" messages
- Use `/amb inventory <name>` to verify items

### Performance
- Limit number of active bots (5-10 recommended)
- Turn brain OFF when not needed
- Use manual tasks for specific operations

---

## 📚 See Also

- **[GETTING_STARTED.md](GETTING_STARTED.md)** - Quick start guide
- **[FEATURES.md](FEATURES.md)** - Feature documentation
- **[CONFIGURATION.md](CONFIGURATION.md)** - Configuration options

---

**Command Reference Complete!** Use these commands to control your bots. 🎮
