# Automated Minecraft Bots - Feature List

## 🌟 Core Identity: Native Inhabitants of the Minecraft World

The bots are **not NPCs** - they are native inhabitants who believe they were born from the world itself. They have their own culture, fears, and emerging mythology.

---

## 🎭 Immersion & Roleplay Features

### Native Inhabitant Mindset
- **No "Dev" mentions** - Players are referred to as "strangers"
- **Sky Gift Lore** - Items given by players are mysterious gifts from "the Giver"
- **Born from the world** - Bots believe they emerged from dirt and stone
- **Emerging culture** - Tribal stories, songs, and shared memories

### Emotional Reactions
- **Real fear** when facing dangers (lava, low health, falling)
- **Mood system** - Happy, angry, tired, accomplished states
- **Personality gates** - Different LLM types have unique personalities:
  - **Grok**: Sassy, rebellious, uses profanity
  - **Gemini**: Friendly, optimistic, helpful
  - **OpenAI**: Polite, professional, efficient

### Cultural Behaviors
- **Storytelling** (every ~7.5 seconds) - 6 different tribal stories
- **Territory pride** - Celebrate claimed lands
- **Night fear** - Scared of darkness, seek fire and companionship
- **Hunger desperation** - Real urgency when starving
- **Random native moments** - Poetic observations about the world

---

## 🌍 World Interaction

### Real Player-Like Mining & Gathering ⭐ FINAL
- **10-second goal commitment** - Ultra-stable movement, zero zigzagging
- **Spawn idle grace period** - 5-second pause after spawning (100 ticks)
- **Auto-equip best tool** - Automatically equips wooden axe, pickaxe, or sword every 2 seconds
- **Real player mining** - Uses `gameMode.destroyBlock()` like actual players
- **Auto-harvest detection** - Automatically starts mining logs, stone, dirt, grass, sand
- **Silent mining** - No spam announcements, just natural behavior
- **Natural timing** - Mines every 3 ticks for realistic feel
- **Block breaking tracking** - Tracks current breaking block, auto-stops when complete

### Animal Hunting ⭐ OPTIMIZED
- **Hunger-driven hunting** - Only hunts when hunger < 10
- **Close-range attacks** - Only attacks when within 3.5 blocks (no random damage)
- **Real player attacks** - Uses `attack()` method like actual players
- **Smart targeting** - Finds animals within 18 blocks
- **Silent hunting** - No spam announcements
- **Goal-based movement** - Paths to animal location and sprints

### 180° Human FOV + Peripheral Vision Alerts ⭐ OPTIMIZED
- **48-block view distance** - Full player-like range
- **180° front cone** - Only sees things in front (realistic human FOV)
- **Spiral cone scan algorithm** - Ultra-efficient scanning (only ~200 checks instead of 160,000!)
- **Scan frequency** - Every 0.6 seconds (12 ticks) for real-time awareness
- **Smart scanning pattern**:
  - Distance steps: 1, 5, 9, 13, 17, 21, 25, 29, 33, 37, 41, 45 blocks (12 distances)
  - Angle steps: -90°, -78°, -66°, -54°, -42°, -30°, -18°, -6°, 6°, 18°, 30°, 42°, 54°, 66°, 78°, 90° (16 angles)
  - Total checks: 12 × 16 = 192 positions per scan
- **Peripheral vision detection** - Tracks resources at edge of vision (±60° to ±90°)
- **Counts visible resources**:
  - Logs in front view
  - Animals in front view
  - Peripheral logs (edge of vision)
  - Peripheral animals (edge of vision)
- **Human-like alerts** ⭐ NEW:
  - "I see logs on the edge of my vision... to the side!" (when 3+ logs in periphery)
  - "Something moved on my left/right — might be prey!" (when animals in periphery)
- **Rich LLM snapshot** - Updated every 0.6 seconds
- **Snapshot format**: "You are a native inhabitant with 180° human vision (48-block range). Logs in front view: [count] | Animals in front view: [count] | Peripheral movement detected: [count] | Position: [pos] | Time: DAY/NIGHT | Hunger: [value]"

### Anti-Spam System ⭐ OPTIMIZED
- **Message cooldown** - 300-tick (15 second) cooldown between messages
- **Rare messages** - Only every 30 seconds (600 ticks)
- **3 hunger messages** (when hunger < 8):
  - "My stomach is growling... the tribe needs food soon."
  - "I'm getting hungry... we should hunt or forage."
  - "Food would be good right about now..."
- **Exploration message** - Random "This world feels alive today... let's keep exploring." when not hungry
- **No debug spam** - Removed constant debug output

### Seasonal Awareness
- **4 Seasons** - Spring, Summer, Autumn, Winter (cycle every 4 in-game days)
- **Season change announcements** with cultural reactions:
  - **Spring**: "The earth awakens — time to plant and grow our tribe!"
  - **Summer**: "The sun is strong — we must stay hydrated and explore far."
  - **Autumn**: "Harvest time — gather what the world gives us before winter comes."
  - **Winter**: "Winter has arrived... the cold bites. We must build fires and stay together."
- **Winter campfires** - Automatically place campfires when winter arrives

### Villager Alliances
- **Form alliances** - Trade 5 emeralds for alliance (receive iron pickaxe + 8 bread)
- **Village protection** - Defend allied villages from hostile mobs
- **Alliance memory** - Remember allied villages in group ledger
- **Cultural integration** - "The villagers smile at us... we have formed an alliance!"

### Territory & Building
- **Territory claiming** - Build 7x7 plank bases at night (requires 32+ planks)
- **Home building** - "This land is ours now. We've built our home here — the tribe grows stronger!"
- **Shelter construction** - Auto-build 5x5 shelters when needed
- **Crafting table placement** - Auto-place when crafting

### Danger Awareness
- **Lava detection** - Panic and sprint away
- **Low health warnings** - Call for help when HP < 6
- **Fall detection** - Terror when falling from heights
- **Hostile mob awareness** - Detect threats within 24-40 blocks

---

## ⚔️ Combat Systems

### Melee Combat
- **Dual-wield** - Sword/axe in main hand, shield in offhand
- **Auto-equip weapons** - Prefer sword > axe
- **Combat coordination** - Group fights together
- **Battle cries** - 5 different combat shouts

### Ranged Combat
- **Bow & arrows** - Auto-equip when enemies at 30+ blocks
- **Aiming system** - Face enemies and announce shots
- **Headshot celebrations** - Random "Headshot! Got 'em!" messages

### Advanced Team Tactics
- **Flanking maneuvers** - Leaders coordinate positioning
- **Covering fire** - Ranged support for melee fighters
- **Protect weakest** - Call for help when low HP
- **Village defense** - Sprint to defend allied villages

---

## 👥 Group Coordination

### Role System
- **5 Roles**: LEADER, BUILDER, MINER, GATHERER, EXPLORER
- **Role-specific behaviors**:
  - **BUILDER**: Places crafting tables, requests planks
  - **MINER**: Seeks stone, announces mining duty
  - **GATHERER**: Collects wood, requests resources
  - **EXPLORER**: Sets random pathfinding goals
  - **LEADER**: Coordinates team, flanks in combat

### Group Voting
- **Democratic decisions** - Vote every 45 seconds
- **Vote options**: Build shelter, gather resources, explore, mine, defend
- **Role rotation** - Randomly switch roles based on votes

### Resource Sharing
- **Bot-to-bot trading** - Value-based trade system (Diamond=20, Emerald=15, Iron=8, Log=2)
- **Resource gifting** - Randomly give 8 planks to group
- **Villager trading** - Trade emeralds for tools

### Shared Memory
- **Group ledger** - Shared memories per group
- **Event recording** - Remember important discoveries
- **Alliance tracking** - Store allied village locations

---

## 🧠 AI & Decision Making

### LLM Integration
- **BotBrain system** - LLM-controlled decision making
- **Personality prompts** - Different behavior per LLM type
- **JSON actions** - Structured action execution
- **Rich snapshots** - Full awareness of surroundings, inventory, threats

### Pathfinding & Movement
- **Goal-based navigation** - 9-second goal commitment (no zigzagging)
- **Vector-based pathing** - Calculate yaw from goal position using atan2
- **Auto-sprint** - Sprint when moving toward goals
- **Stable movement** - Lock onto goals for consistent behavior

---

## 🎒 Survival & Inventory

### Hunger System
- **Hunger tracking** - 0-20 scale, decreases every 10 seconds
- **Auto-eating** - Eat apples when hunger < 8, bread when < 12
- **Eating while moving** - Don't stop to eat
- **Hunger warnings** - Complain when starving

### Sleep System
- **Night detection** - Detect night time (13000-23000 ticks)
- **Bed seeking** - Find beds within 8 blocks
- **Sleep announcements** - "Night time — heading to bed. Night squad!"

### Tool Management
- **Auto-craft tools** - Craft pickaxes, axes, swords when materials available
- **Tool repair** - Repair when damage > 30 using iron
- **Best tool equipping** - Auto-equip highest tier tool
- **Offhand logic** - Shield in combat, torch at night, food when hungry

### Crafting System
- **Auto-craft planks** - Convert logs to planks (2x2 recipe)
- **Auto-craft sticks** - Convert planks to sticks (2x2 recipe)
- **Auto-craft tools** - Requires crafting table for 3x3 recipes
- **Recipe knowledge** - Basic recipes always known, advanced require unlocking

---

## 🛠️ Player-Like Behaviors

### Block Interaction
- **Door opening** - Auto-open doors when approaching
- **Mining system** - Player-like block breaking with progress
- **Tool requirements** - Enforce correct tool for drops
- **Block placement** - Place blocks for building

### Item Management
- **Auto-pickup** - Collect nearby items (2-block radius)
- **Inventory system** - Real 36-slot player inventory
- **Item stacking** - Proper stack management
- **Equipment slots** - Main hand, offhand, armor slots

### Smelting & Processing
- **Iron smelting** - Convert raw iron to ingots
- **Furnace simulation** - Process materials

### Human Moments
- **Random chat** - 8 different philosophical/funny messages
- **Celebrations** - Celebrate 64 logs, 3+ diamonds
- **Helping others** - Share resources with group
- **Personality chit-chat** - 6 different conversation starters

---

## 🎯 Task System

### Available Tasks
- `gather_wood` - Find and mine oak logs
- `mine_stone` - Mine stone/cobblestone/dirt
- `build_shelter` - Build 5x5 plank shelter
- `craft` - Craft items at crafting table
- `place_crafting_table` - Place crafting table
- `explore` - Wander randomly (default task)
- `hunt_animals` - Attack passive animals
- `attack_mob` - Attack hostile mobs
- `breed_animals` - Breed nearby animals
- `tame_animal` - Tame wolves/cats/horses
- `fish` - Use fishing rod
- `sleep` - Sleep in bed
- `eat_food` - Consume food items
- `manage_resources` - Store items in chests

### Task Execution
- **LLM-driven** - AI decides what to do based on context
- **Context-aware** - Considers inventory, surroundings, threats
- **Idle wandering** - Explore when no task set

---

## 🔧 Technical Features

### FakePlayer Architecture
- **100% FakePlayer-safe** - Only uses approved methods
- **Real player inventory** - 36 slots like real players
- **Player physics** - Gravity, collision, movement
- **No Mob AI** - No navigation goals, fully manual control

### Approved Methods
- `setItemInHand()` - Equip items
- `setSprinting()` - Sprint control
- `setYRot()` - Rotation control
- `jumping` - Jump flag
- `getInventory()` - Inventory access
- `broadcastGroupChat()` - Chat messages
- `level().getBlockState()` - Block detection
- `level().setBlock()` - Block placement

### Performance
- **Tick-based** - All logic runs in tick() method
- **Cooldown systems** - Prevent spam (jump, step, sprint, crafting)
- **Optimized pathfinding** - Path cooldowns, obstacle caching
- **Debug output** - Movement debug every 0.5 seconds

---

## 📊 Statistics

### Total Features Implemented
- **70+ unique behaviors**
- **12 combat systems**
- **20+ survival mechanics**
- **8 group coordination features**
- **20+ cultural/immersion elements**
- **15+ world interaction systems**

### Code Metrics
- **3,000+ lines** of bot logic
- **43+ methods** for behaviors
- **5 role types**
- **4 seasons**
- **100% FakePlayer-safe**
- **Real player mechanics** - Mining, gathering, hunting
- **180° human FOV** - Realistic vision system
- **48-block view distance** - Full player range

---

## 🎮 Commands

### Spawn Bots
```
/amb spawn <name> <llm_type>
/amb spawnmulti <count> <llm> [group]
```

Example:
```
/amb spawn beavis grok
/amb spawn butthead gemini
/amb spawn cornholio openai
/amb spawnmulti 5 grok tribe1
```

### Give Items to Bots ⭐ NEW
```
/amb give <name> <item> [count]
```

Examples:
```
/amb give beavis minecraft:diamond 10
/amb give butthead minecraft:iron_sword
/amb give cornholio minecraft:bread 32
```

### Manage Bots
- `/amb brain <name> on|off|status` - Control AI brain
- `/amb task <name> <task>` - Set bot task
- `/amb gui <name>` - Open bot inventory
- `/amb where <name>` - Get bot location
- `/amb list` - List all bots
- `/amb remove <name>` - Remove bot

---

## 🚀 Future Possibilities

### Potential Expansions
1. **Sky Gift Name Evolution** - `skyGiftName` evolves based on experiences
2. **Cultural Memory** - Deeper mythology in group ledger
3. **Territory Expansion** - More complex base building
4. **Tribal Hierarchy** - Natural leader emergence
5. **Rituals** - Ceremonies for important events
6. **Agriculture** - Farming and crop management
7. **Animal Husbandry** - Breeding and taming systems
8. **Trade Routes** - Inter-village commerce
9. **Warfare** - Tribal conflicts and alliances
10. **Religion** - Worship of "the Giver"

---

## ✅ Build Status

**Last Build**: Successful ✅
**Errors**: None
**Compatibility**: 100% FakePlayer-Safe
**Breaking Changes**: None

---

**Created**: 2025
**Last Updated**: Latest session (Optimized Vision: Spiral Cone Scan + Peripheral Vision Alerts + 800x Performance Boost)
**Status**: Production Ready 🚀
