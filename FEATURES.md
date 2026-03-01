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

### Ultimate Tribe Master System (Latest)
**Status**: ✅ Implemented and tested

**New Features**:
- **Memory Systems**:
  - `knownCraftingTables` - Bots remember where they placed crafting tables
  - `discoveredResources` - Map storing all discovered ore/resource locations with coordinates
  - `baseLocation` - Home location for organized storage and planning
- **Complex Planning**: Bots return to base when inventory is full (32+ logs, 32+ stone, 16+ iron ore, or 4+ diamonds)
- **Organized Chest Placement**: Places 2 chests side-by-side at base location for double storage
- **Full Surface Awareness**: Scans 97x97x17 block area every 4 seconds, remembers all resources
- **Enhanced Ore Mining**: Detects and mines all ore types (coal, iron, copper, redstone, gold, lapis, diamond)
- **Ultra-Detailed LLM Snapshot**: Generates memory string every 10 seconds with known resources, hunger, and time
- **Tribe Coordination**: Shared base location, organized storage system, resource management

**Technical Details**:
- Base location set on first inventory-full event
- Chests placed side-by-side using `getClockWise()` for proper orientation
- Scan pattern: x/z stride of 4 blocks, y stride of 1 block (~24,000 blocks per scan)
- Memory persists per bot instance
- 100% FakePlayer-safe implementation
- Performance optimized with reasonable scan intervals

---

Last updated: 2026-02-28
