# LLM-Driven Bot Architecture

## Overview

Bots are now **fully LLM-driven** with **player-like behavior**. No automatic crafting loops, no instant crafting, no item disappearing.

---

## Key Principles

### 1. **No Automatic Crafting**
- ❌ **REMOVED**: `proactiveCrafting()` automatic loops
- ❌ **REMOVED**: Auto-stick crafting, auto-plank conversion
- ✅ **NEW**: Bots craft only when LLM decides they need something
- ✅ **NEW**: Crafting is intentional, not reactive

### 2. **Player-Like Behavior**
- Bots must use crafting tables for 3x3 recipes (stone tools, chests, etc.)
- Bots can craft 2x2 recipes in inventory (planks, sticks, crafting table)
- No instant crafting - must have materials and decide to craft
- Full awareness of inventory and surroundings

### 3. **Chest Access Enabled**
- ✅ Bots can open and access chests
- ✅ Bots can store items in chests
- ✅ Bots can retrieve items from chests
- ✅ Bots can create their own "private stash" chests
- ✅ Shared resource system available

### 4. **Social Interaction**
- Bots can ask other bots for items
- Bots can share or refuse to share based on personality
- Bots can trade items with each other
- If nobody has an item, bot goes to get it themselves

---

## How Crafting Works Now

### LLM Decision-Making
The LLM (OpenAI, Gemini, Grok) controls when bots craft:

```
Bot thinks: "I need a pickaxe to mine stone"
  ↓
LLM decides: "Craft a wooden pickaxe"
  ↓
Bot checks inventory: Has 3 planks + 2 sticks
  ↓
Bot crafts: Wooden pickaxe (2x2 recipe, no table needed)
```

### Crafting Table Requirement
For 3x3 recipes (stone tools, chests, etc.):

```
Bot thinks: "I need a stone pickaxe"
  ↓
LLM decides: "Find or place a crafting table"
  ↓
Bot searches: Finds crafting table within 5 blocks
  ↓
Bot crafts: Stone pickaxe (requires 3x3 grid)
```

### No More Runaway Loops
**Before (BAD)**:
```
Bot has 64 logs
  ↓ (automatic every 20 ticks)
Converts to 256 planks
  ↓ (automatic every 20 ticks)
Converts to 512 sticks
  ↓ (automatic every 20 ticks)
Inventory full of sticks!
```

**Now (GOOD)**:
```
Bot has 64 logs
  ↓ (LLM decides)
"I need 4 planks for a crafting table"
  ↓ (intentional craft)
Crafts 4 planks from 1 log
  ↓ (done)
Logs stay as logs until needed
```

---

## Chest System

### Accessing Chests
Bots can interact with chests like players:

```java
// Bot approaches chest
bot.interactWith(chest);

// Bot opens chest inventory
ChestBlockEntity chestEntity = ...;

// Bot stores items
chestEntity.setItem(slot, itemStack);

// Bot retrieves items
ItemStack item = chestEntity.getItem(slot);
```

### Shared vs Private Chests
- **Shared Chests**: Group resource pools (wood, stone, tools, food)
- **Private Chests**: Bot's personal stash (they decide later)
- Bots can create and label chests based on contents

### Chest Types
1. **Tools** - Pickaxes, axes, shovels, hoes
2. **Resources** - Logs, planks, sticks, cobblestone, ores
3. **Building** - Dirt, stone, bricks, glass
4. **Food** - Wheat, bread, cooked meat
5. **Misc** - Everything else

---

## LLM Integration

### Bot Awareness
Bots have full awareness of:
- **Inventory**: What items they have and quantities
- **Surroundings**: Nearby blocks, chests, crafting tables
- **Other Bots**: Who's nearby, what they have
- **Needs**: Health, hunger, tool durability

### LLM Prompts
The LLM receives context like:
```
Bot: Steve
Health: 20/20
Inventory: 16 oak_log, 8 cobblestone, 2 stick
Nearby: crafting_table (3 blocks), chest (5 blocks), Alex (10 blocks)
Task: Gather wood
Thinking: "I have enough logs, should I craft tools or store items?"
```

### LLM Responses
The LLM can decide:
- `craft_item` - Craft a specific item
- `use_chest` - Store or retrieve items
- `ask_bot` - Request items from another bot
- `gather_resource` - Go get more materials
- `continue_task` - Keep doing current task

---

## Commands

### Auto-Craft Toggle (Legacy)
```bash
/amb autocraft <name> on|off|status
```
**Note**: This is now **OFF by default** and should stay OFF for LLM-driven behavior.

### Manual Crafting (For Testing)
Bots can still craft via the `autoCraft()` method when called explicitly by goals/commands.

---

## Code Architecture

### Removed Systems
1. ❌ `proactiveCrafting()` - Automatic crafting loops
2. ❌ `autoCraftTools()` - Tool auto-crafting
3. ❌ `processCraftingQueue()` - Crafting queue system

### Active Systems
1. ✅ `autoCraft()` - Manual crafting when LLM decides
2. ✅ `SharedResourceGoal` - Chest access and sharing
3. ✅ `BotBrain` - LLM decision-making
4. ✅ `storeItemsInChest()` - Chest storage
5. ✅ `retrieveItemsFromChest()` - Chest retrieval

### Key Files
- `AmbNpcEntity.java` - Main bot entity (crafting disabled)
- `BotBrain.java` - LLM integration and decision-making
- `SharedResourceGoal.java` - Chest access and sharing
- `AMBTaskGoal.java` - Task execution and chest storage

---

## Example Scenarios

### Scenario 1: Bot Needs Tools
```
1. Bot: "I need a pickaxe to mine stone"
2. LLM: "Check inventory for materials"
3. Bot: "I have 5 planks and 3 sticks"
4. LLM: "Craft a wooden pickaxe"
5. Bot: Crafts wooden pickaxe (2x2 recipe)
6. Bot: Continues mining
```

### Scenario 2: Bot Inventory Full
```
1. Bot: "My inventory is full (30/36 slots)"
2. LLM: "Find a chest to store items"
3. Bot: Searches for nearby chest
4. Bot: Finds chest at (100, 64, 200)
5. Bot: Walks to chest and opens it
6. Bot: Stores excess cobblestone and logs
7. Bot: Returns to task
```

### Scenario 3: Bot Needs Items from Another Bot
```
1. Bot Steve: "I need sticks but don't have any"
2. LLM: "Ask nearby bots for sticks"
3. Steve: Broadcasts "Anyone have sticks?"
4. Bot Alex: "I have 16 sticks"
5. LLM (Alex): "Share 8 sticks with Steve (same group)"
6. Alex: Gives 8 sticks to Steve
7. Steve: "Thanks! I'll craft a pickaxe now"
```

### Scenario 4: Bot Creates Private Stash
```
1. Bot: "I found 3 diamonds!"
2. LLM: "These are valuable, create a private chest"
3. Bot: Crafts chest (8 planks)
4. Bot: Places chest in hidden location
5. Bot: Stores diamonds in chest
6. Bot: Remembers chest location for later
```

---

## Benefits

### For Players
- ✅ No more item disappearing issues
- ✅ Bots behave like real players
- ✅ Predictable and understandable behavior
- ✅ Can watch bots make decisions in real-time

### For Bots
- ✅ Full autonomy via LLM
- ✅ Can develop unique personalities
- ✅ Can create their own strategies
- ✅ Can cooperate or compete with other bots

### For Development
- ✅ Cleaner codebase (removed 200+ lines of auto-craft loops)
- ✅ Easier to debug (no hidden automatic behaviors)
- ✅ More extensible (LLM can learn new behaviors)
- ✅ Better player parity (bots = players)

---

## Testing

### Test 1: No Auto-Crafting
```bash
1. /amb spawn testbot
2. /amb give testbot oak_log 64
3. Wait 30 seconds
4. /amb inventory testbot
Expected: Still has 64 logs (not converted to planks/sticks)
```

### Test 2: Chest Access
```bash
1. /amb spawn testbot
2. Place a chest near bot
3. /amb give testbot cobblestone 64
4. Bot should store items in chest when inventory full
Expected: Bot walks to chest, opens it, stores items
```

### Test 3: LLM Crafting
```bash
1. /amb spawn testbot
2. /amb brain testbot on
3. /amb give testbot oak_planks 10
4. /amb give testbot stick 5
5. Bot decides to craft tools based on needs
Expected: Bot crafts only what it needs, when it needs it
```

---

## Future Enhancements

### Planned Features
1. **Chest Labeling** - Bots place signs on chests
2. **Inventory Management** - Bots organize items by type
3. **Trading System** - Bots trade items with each other
4. **Crafting Recipes** - LLM learns new recipes over time
5. **Resource Optimization** - Bots minimize waste

### Community Ideas
- Bots can create shops with chests
- Bots can hide treasure chests
- Bots can share chest locations with trusted friends
- Bots can lock chests with passwords

---

## Summary

**Old System**: Automatic crafting loops → runaway stick production → items disappear
**New System**: LLM decides → intentional crafting → player-like behavior

Bots are now **intelligent agents** that think, decide, and act like players. They have full access to the world (chests, crafting tables, other bots) and make their own decisions via LLM integration.

**No more automatic anything. Everything is intentional.** 🎯
