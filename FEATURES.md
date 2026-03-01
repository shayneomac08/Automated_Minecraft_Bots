# Automated Minecraft Bots - Feature List

## Core features (consolidated)

This file lists the active, supported features. For detailed developer notes, testing guides and recent fixes, see `CHANGELOG.md` and `README.md`. Temporary/auxiliary markdown files have been consolidated to reduce clutter.

---

## Highlights

- Real player-like mining and gathering (progressive block breaking, correct drops).
- Stable straight-line movement using FakePlayer-safe physics (no zigzag).
- Task & LLM-driven behavior (explore, gather_wood, mine_stone, build_shelter, etc.).
- Auto-equip best tool per task and visible hand equip synchronization.
- 180° human FOV snapshot system for LLM decision making.
- Group coordination: roles, voting, resource sharing, alliances.
- Combat (melee, ranged), hunting, breeding, taming, fishing, sleeping, and more.
- **Memory systems**: Bots remember discovered resources and placed crafting tables.
- **Tribe coordination**: Shared crafting tables, chest storage, resource awareness.
- **Enhanced mining**: All ore types (coal, iron, copper, redstone, gold, lapis, diamond).

---

## Conventions
- Keep only `CHANGELOG.md`, `FEATURES.md`, and `README.md` as primary markdown documentation.
- Additional temporary docs should be archived or removed to avoid cluttering the repo root.

---

## Commands (summary)
- `/amb spawn <name> <llm_type>`
- `/amb remove <name>`
- `/amb list`
- `/amb give <name> <item> [count]`
- `/amb task <name> <task>`

---

## Recent Updates

### FakePlayer Visibility Fix (Latest)
**Status**: ✅ Implemented - Testing Required

**Problem**: Bots were spawning but completely invisible and not moving.

**Root Cause**: FakePlayers extend ServerPlayer and need explicit visibility initialization. While `addFreshEntity()` adds them to the entity list, visibility flags must be set manually.

**Solution Implemented**:
1. **Constructor Initialization**: Added `setInvisible(false)` and `setInvulnerable(false)` in AmbNpcEntity constructor
2. **Spawn Enhancement**: Explicitly set `setInvisible(false)` in spawn methods to ensure visibility
3. **Clean Rendering**: No glow effects or special rendering - bots render as normal players

**Files Modified**:
- `AmbNpcEntity.java` - Constructor, spawnAtPlayer(), and tick() method
- `AvatarFactory.java` - spawn() method

**Testing Instructions**:
1. Run: `./gradlew.bat runClient`
2. Spawn: `/amb spawn TestBot openai`
3. **Expected**: Bot appears as normal player with visible nametag, stands still 5 seconds, then moves/mines

**Technical Notes**:
- FakePlayers don't need custom EntityType or renderer registration
- They use Minecraft's built-in player rendering system
- Visibility flags must be explicitly set despite automatic rendering
- Bots render as normal players with standard player models and skins

---

### Clean Architecture Rebuild - Streamlined FakePlayer
**Status**: ✅ Implemented and tested - **MAJOR REFACTOR**

**What Changed**:
- **Complete rewrite** of AmbNpcEntity from ~2500 lines to ~250 lines of clean, focused code
- **Removed complexity**: Eliminated LLM integration, vision systems, seasonal awareness, villager alliances
- **Kept essentials**: Core movement, mining, eating, NBT persistence, role system
- **Backward compatible**: All commands and existing systems still work via compatibility layer

**New Clean Features**:
- **Simplified Constructor**: `new AmbNpcEntity(ServerLevel level, String name)` - no GameProfile needed
- **Full NBT Persistence**: Role, hunger, base location, and crafting table position saved to world data
- **Real Eating Mechanics**: Bots consume bread (+5 hunger) and apples (+4 hunger) when hungry
- **20-Second Calm Commitment**: Goal lock timer set to 400 ticks for ultra-deliberate movement
- **Ultra-Smooth Movement**: Yaw updates only every 15 ticks with very subtle sway (±1.5 degrees)
- **Permanent No-Dirt Particles**: `spawnSprintParticle()` override prevents all sprint particles
- **Completely Silent**: `getSoundVolume()` returns 0.0F, `isSilent()` returns true, no step sounds
- **Real Cracking Animation**: Mining uses correct timing (every 4 ticks) for authentic block-breaking visuals
- **Mining Lock**: 80-tick movement lock while mining for realistic player behavior
- **Auto-Equip on Pickup**: Tools automatically equipped every 20 ticks (1 second)
- **Enhanced Ore Mining**: Detects and mines all ore types (coal, iron, copper, redstone, gold, lapis, diamond)
- **Visible Hands**: Always-visible tool display with priority (axe > pickaxe > sword)

**Legacy Compatibility Layer**:
- `setBrainEnabled()` / `isBrainEnabled()` - For command system
- `setTask()` / `getCurrentTask()` - For task management
- `setMoveTarget()` / `stopMovement()` - For movement control
- `spawnAtPlayer()` - Static spawn method
- `openGui()` - Simple GUI placeholder

**Technical Details**:
- Architecture: Clean FakePlayer extension (FakePlayer handles connection automatically)
- Code size: ~250 lines (down from ~2500 lines)
- NBT Save: Role, hunger, baseLocation, knownCraftingTable persist across world saves/loads
- Eating threshold: Hunger < 10 triggers automatic eating
- Yaw update interval: Every 15 ticks (0.75 seconds) with ±1.5° sway
- Goal lock: 400 ticks (20 seconds) for ultra-calm, deliberate movement
- Mining check interval: Every 4 ticks (0.2 seconds) for proper cracking animation
- Movement lock: 80 ticks (4 seconds) while actively mining
- Tool equip check: Every 20 ticks (1 second)
- Uses `gameMode.destroyBlock()` for FakePlayer-safe mining
- Uses `ValueOutput`/`ValueInput` for modern NBT persistence
- 100% FakePlayer-safe implementation
- No visual or audio pollution (silent, no particles)

**What Was Removed**:
- LLM integration (OpenAI, Gemini, Grok, Claude)
- Vision systems (180° FOV, peripheral detection)
- Seasonal awareness
- Villager alliances
- Group coordination complexity
- Chat rate limiting (simplified)
- Jump spam protection (not needed with calm movement)
- Equipment sync (handled by auto-equip)

**Why This Change**:
- Hit too many walls with complex architecture
- Fresh start with clean, maintainable code
- Easier to add features incrementally
- Better performance with less overhead
- Simpler debugging and testing

---

Last updated: 2026-02-28
