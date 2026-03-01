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

Last updated: 2026-02-28
