# 🚀 Getting Started - Automated Minecraft Bots

## 📋 Overview
This mod adds intelligent AI-powered bots to Minecraft that can gather resources, build structures, farm, smelt, and interact with the world autonomously.

---

## ⚡ Quick Start

### 1. **Spawn a Bot**
```bash
/amb spawn <name>
```
Example: `/amb spawn Steve`

### 2. **Turn Brain ON**
```bash
/amb brain <name> on
```
The bot will immediately assess its needs and start working autonomously.

### 3. **Watch the Bot Work**
The bot will automatically:
- Gather 16 logs when it needs wood
- Mine 32 cobblestone when it needs stone
- Build chests when inventory is 75% full
- Farm when health is low
- Craft tools as needed

---

## 🎮 Basic Commands

### Bot Management
```bash
/amb spawn <name>              # Spawn a new bot
/amb despawn <name>            # Remove a bot
/amb brain <name> on           # Enable autonomous AI
/amb brain <name> off          # Disable autonomous AI
/amb status <name>             # Check bot status
/amb list                      # List all active bots
```

### Task Assignment
```bash
/amb task <name> gather_wood   # Gather wood
/amb task <name> mine_stone    # Mine cobblestone
/amb task <name> mine_ore      # Mine ores (iron, diamond, etc.)
/amb task <name> mine_dirt     # Dig dirt
/amb task <name> farm          # Farm wheat
/amb task <name> smelt         # Smelt ores in furnace
/amb task <name> stop          # Stop current task
```

### Item Management
```bash
/amb give <name> <item> <amount>    # Give items to bot
/amb inventory <name>                # View bot inventory
```

Examples:
```bash
/amb give Steve diamond_pickaxe 1
/amb give Steve oak_planks 64
/amb give Steve iron_ingot 12
```

### LLM Integration (Optional)
```bash
/amb chat <name> <message>     # Chat with bot using LLM
/amb llm <provider>            # Set LLM provider (openai/anthropic/ollama)
```

---

## 🧠 How the AI Works

### Autonomous Decision-Making
When brain is enabled, the bot assesses its needs every 10 seconds:

**Priority Order:**
1. **Health < 12** → Farm (need food)
2. **Health < 15** → Mine stone (need tools/shelter)
3. **Logs < 16** → Gather wood
4. **Cobblestone < 32** → Mine stone
5. **Inventory > 75% full** → Build chest

### Console Output
```
[AMB] Steve assessed needs → chose: gather_wood (Wood:0, Food:20.0)
[AMB] AMBTaskGoal started: gather_wood targeting BlockPos{x=100, y=70, z=200}
[AMB] Found tree with 8 logs
[AMBTaskGoal] Broke block at (100, 70, 200) (total: 1)
...
[AMBTaskGoal] Broke block at (100, 75, 200) (total: 16)
[AMB] Steve has enough resources: 16 items
```

---

## 🔧 Bot Capabilities

### Resource Gathering
- ✅ **Wood Gathering** - Chops entire trees, clears leaves
- ✅ **Stone Mining** - Mines cobblestone efficiently
- ✅ **Ore Mining** - Finds and mines iron, diamond, gold, etc.
- ✅ **Dirt Digging** - Clears land

### Crafting & Tools
- ✅ **Auto-Crafting** - Crafts tools when needed
- ✅ **Tool Progression** - Wooden → Stone → Iron → Diamond
- ✅ **Tool Durability** - Replaces broken tools automatically
- ✅ **Recipe Book** - 68+ recipes unlocked

### Building & Storage
- ✅ **Chest Building** - Builds chests when inventory full
- ✅ **Auto-Storage** - Stores excess items
- ✅ **Persistent Inventory** - Saves inventory between sessions

### Farming & Smelting
- ✅ **Wheat Farming** - Plants, grows, and harvests wheat
- ✅ **Furnace Smelting** - Smelts ores automatically
- ✅ **Villager Trading** - Trades with nearby villagers

### Movement & Physics
- ✅ **Player-like Movement** - Smooth, natural pathfinding
- ✅ **Fall Damage** - Takes damage from falls (3+ blocks)
- ✅ **Respawn System** - Respawns at spawn point when dying
- ✅ **Door Opening** - Can navigate through doors
- ✅ **Swimming** - Can float and swim

---

## 📊 Resource Targets

| Resource | Target Amount | Notes |
|----------|---------------|-------|
| **Logs** | 16 | All log types (oak, birch, spruce, etc.) |
| **Cobblestone** | 32 | For tools and building |
| **Iron Ore** | 12 | For iron tools (3 per tool × 4 tools) |
| **Diamonds** | 6 | For diamond tools (3 per tool × 2 tools) |

---

## 🎯 Example Workflow

### Autonomous Bot (Brain ON)
```bash
# 1. Spawn and enable brain
/amb spawn Steve
/amb brain Steve on

# Bot automatically:
# - Gathers 16 logs
# - Crafts wooden pickaxe
# - Mines 32 cobblestone
# - Crafts stone tools
# - Mines iron ore
# - Crafts iron tools
# - Builds chest when inventory full
```

### Manual Task Assignment (Brain OFF)
```bash
# 1. Spawn bot
/amb spawn Steve

# 2. Assign specific tasks
/amb task Steve gather_wood
# (wait for completion)

/amb task Steve mine_stone
# (wait for completion)

/amb task Steve farm
```

---

## 🐛 Troubleshooting

### Bot Not Moving
- Check if brain is enabled: `/amb brain <name> on`
- Check if bot has a task: `/amb status <name>`
- Try respawning: `/amb despawn <name>` then `/amb spawn <name>`

### Bot Stuck
- Bot has automatic stuck detection and recovery
- Will attempt tiny nudges (0.04 blocks) when stuck
- Only jumps when stuck for 3+ seconds

### Bot Not Gathering Resources
- Check console for "[AMB]" messages
- Verify bot is near resources (trees, stone, etc.)
- Check inventory: `/amb inventory <name>`

### Console Spam
- This is normal - shows bot's decision-making process
- Look for patterns: "assessed needs → chose: [task]"

---

## 💡 Tips & Tricks

### Efficient Resource Gathering
- Spawn bot near forests for wood gathering
- Spawn bot near stone for mining
- Bot will automatically find nearest resources

### Tool Management
- Bot automatically crafts tools when needed
- Bot replaces broken tools (< 5% durability)
- Give bot materials to speed up progression

### Inventory Management
- Bot builds chest at 75% inventory (27/36 slots)
- Give bot planks to enable chest building: `/amb give <name> oak_planks 8`

### LLM Integration
- Set API key in config file
- Use `/amb chat <name> <message>` for natural language commands
- Bot can respond intelligently to requests

---

## 📚 Next Steps

- **[COMMANDS.md](COMMANDS.md)** - Complete command reference
- **[FEATURES.md](FEATURES.md)** - Detailed feature documentation
- **[CONFIGURATION.md](CONFIGURATION.md)** - Configuration options
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Development guide

---

## ✅ Quick Reference

**Spawn & Enable:**
```bash
/amb spawn Steve
/amb brain Steve on
```

**Give Items:**
```bash
/amb give Steve diamond_pickaxe 1
/amb give Steve oak_planks 64
```

**Assign Tasks:**
```bash
/amb task Steve gather_wood
/amb task Steve mine_stone
/amb task Steve farm
```

**Check Status:**
```bash
/amb status Steve
/amb inventory Steve
/amb list
```

---

**Ready to start!** Spawn a bot and watch it work autonomously. 🎮
