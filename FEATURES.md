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

### Final Calm & Human - Smooth Movement (Latest)
**Status**: ✅ Implemented and tested

**New Features**:
- **Permanent No-Dirt Particles**: `spawnSprintParticle()` override prevents all sprint particles
- **Completely Silent**: `getSoundVolume()` returns 0.0F, `isSilent()` returns true, no step sounds
- **Smooth Human Movement**: Yaw updates only every 8 ticks with natural sway (±2 degrees)
- **15-Second Calm Commitment**: Goal lock timer increased to 300 ticks for more deliberate movement
- **Real Cracking Animation**: Mining uses correct timing (every 4 ticks) for authentic block-breaking visuals
- **Mining Lock**: 80-tick movement lock while mining for realistic player behavior
- **Auto-Equip on Pickup**: Tools automatically equipped every 20 ticks (1 second)
- **Enhanced Ore Mining**: Detects and mines all ore types (coal, iron, copper, redstone, gold, lapis, diamond)
- **Visible Hands**: Always-visible tool display with priority (axe > pickaxe > sword)

**Technical Details**:
- Yaw update interval: Every 8 ticks (0.4 seconds) with random sway
- Goal lock: 300 ticks (15 seconds) for calm, deliberate movement
- Mining check interval: Every 4 ticks (0.2 seconds) for proper cracking animation
- Movement lock: 80 ticks (4 seconds) while actively mining
- Tool equip check: Every 20 ticks (1 second)
- Uses `gameMode.destroyBlock()` for FakePlayer-safe mining
- 100% FakePlayer-safe implementation
- No visual or audio pollution (silent, no particles)

---

Last updated: 2026-02-28
