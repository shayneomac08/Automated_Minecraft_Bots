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

### Final Master Method - Real Cracking Animation (Latest)
**Status**: ✅ Implemented and tested

**New Features**:
- **Real Cracking Animation**: Mining now uses correct timing (every 4 ticks) for authentic block-breaking visuals
- **Mining Lock**: 80-tick movement lock while mining for realistic player behavior
- **Mining Announcements**: Bots announce what they're mining ("Starting to mine block.oak_log...")
- **Auto-Equip on Pickup**: Tools automatically equipped every 20 ticks (1 second)
- **Single Shared Crafting Table**: Simplified memory system with `knownCraftingTable` field
- **Enhanced Ore Mining**: Detects and mines all ore types (coal, iron, copper, redstone, gold, lapis, diamond)
- **Visible Hands**: Always-visible tool display with priority (axe > pickaxe > sword)

**Technical Details**:
- Mining check interval: Every 4 ticks (0.2 seconds) for proper cracking animation
- Movement lock: 80 ticks (4 seconds) while actively mining
- Tool equip check: Every 20 ticks (1 second)
- Uses `gameMode.destroyBlock()` for FakePlayer-safe mining
- Single `knownCraftingTable` BlockPos instead of Set for simplicity
- 100% FakePlayer-safe implementation

---

Last updated: 2026-02-28
