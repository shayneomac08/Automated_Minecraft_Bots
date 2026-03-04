# Automated Minecraft Bots - Feature List

## Core Features

This file lists the active, supported features. For detailed version history, see `CHANGELOG.md`. For quick start guide, see `README.md`.

---

## Highlights

- **Human-like Movement**: Natural physics with smooth acceleration, head sway, and movement variation
- **Intelligent Navigation**: Multi-level stuck detection, vertical pathfinding, and door navigation
- **Realistic Actions**: Progressive block breaking, automatic tool switching, hunger management
- **Task Validation**: Pre-validates goals for reachability before setting them
- **Survival AI**: Automatic eating, health monitoring, and intelligent task execution
- **Enhanced Mining**: All ore types (coal, iron, copper, redstone, gold, lapis, diamond)
- **Memory Systems**: Bots remember discovered resources and placed crafting tables
- **Tribe Coordination**: Shared crafting tables, chest storage, resource awareness
- **Combat & Interaction**: Melee, ranged, hunting, breeding, taming, fishing, sleeping

---

## In-Game Commands

### Bot Management
- **`/amb ping`** - Test if the mod is loaded and working
- **`/amb spawn <name> [llm]`** - Spawn a bot with optional LLM type (grok, openai, gemini, claude)
  - Example: `/amb spawn bot`
  - Example: `/amb spawn worker grok`
- **`/amb spawnmulti <count> <llm> [group]`** - Spawn multiple bots at once
  - Example: `/amb spawnmulti 5 grok tribe1`
- **`/amb remove <name>`** - Remove a bot from the world
  - Example: `/amb remove bot`
- **`/amb list`** - List all active bots with their status
- **`/amb where <name>`** - Get the current position of a bot
  - Example: `/amb where bot`

### Bot Control
- **`/amb task <name> <task>`** - Assign a task to a bot
  - Tasks: `gather_wood`, `mine_stone`, `mine_ore`, `mine_dirt`, `explore`, `hunt_animals`
  - Example: `/amb task bot gather_wood`
- **`/amb brain <name> on|off|status`** - Control bot AI
  - `/amb brain bot on` - Enable AI decision making
  - `/amb brain bot off` - Disable AI (bot becomes idle)
  - `/amb brain bot status` - Check if AI is enabled

### Inventory Management
- **`/amb give <name> <item> [count]`** - Give items to a bot
  - Example: `/amb give bot minecraft:diamond_pickaxe`
  - Example: `/amb give bot minecraft:bread 64`
- **`/amb gui <name>`** - Open bot's inventory GUI (view/manage items)
  - Example: `/amb gui bot`
- **`/amb inventory <name>`** - Same as gui command
  - Example: `/amb inventory bot`

---

## Movement & Navigation Systems

### Enhanced Stuck Detection (Multi-Level Recovery)
- **Dual-criteria detection**: Position unchanged for 40 ticks OR average speed < 0.01 blocks/tick
- **Progressive recovery strategies**:
  - Level 1 (40+ ticks): Jump and random strafe
  - Level 2 (80+ ticks): Place block below or break block above
  - Level 3 (120+ ticks): LLM override request
- **Automatic goal abandonment** after level 3 failure
- **Movement tracking**: Calculates average speed over 10-tick windows

### Vertical Navigation
- **Multi-block jump support** with cost calculation for A* pathfinding
- **Vertical movement costs**:
  - +0.5 for jumping up 1 block
  - +2.0 for needing to place a block (2 blocks up)
  - +0.3 for falling down 1 block
  - +15.0 for dangerous falls (>3 blocks)
- **Safe fall detection**: Falls ≤3 blocks or into water are safe
- **Block placement planning** for climbing (placeholder for future implementation)

### Human-like Movement
- **Natural head movement**:
  - Idle sway: ±2.5° horizontal when not moving
  - Smooth yaw rotation over 5 ticks when moving
  - Vertical sway: ±1° pitch for realism
- **Smooth acceleration**: Speed lerp over 3 ticks
- **Sprint toggling**: Based on distance (>3 blocks) and hunger (>6)
- **Movement variation**: ±2-5% random deviation to prevent perfectly straight lines
- **Path deviation**: 10% chance to offset waypoint by ±1 block

### Task Validation
- **Pre-validation checks** before setting goals:
  - Vertical reachability (max 5 blocks without climbing)
  - Horizontal distance (max 50 blocks)
  - Block type matches task requirements
  - Goal is in loaded chunks
  - Goal is within world bounds (y: 0-320)
- **Automatic goal adjustment**: Finds same-level alternatives if goal is too high
- **Tool requirement validation**: Checks if player has required tools for task

### 4-Phase Door Navigation
- **Phase 1 (Approach)**: Move to door, open when within 2.2 blocks
- **Phase 2 (Pass Through)**: Move to position 3 blocks beyond door
- **Phase 3 (Verify Passage)**: Confirm bot is >2 blocks from door
- **Phase 4 (Post-Exit Check)**: Resume path to original goal, ignore door for 200 ticks
- **Pre-exit goal saving**: Restores original goal after door passage

---

## Action Systems

### Realistic Mining
- **Progressive block breaking** with crack animations (0-9 stages)
- **Automatic tool switching**: Equips best tool for block type
- **Mining time calculation**: Based on block hardness and tool efficiency
- **Arm swing animation**: Every 4 ticks for visual feedback
- **FakePlayer-safe**: Uses `gameMode.destroyBlock()` for proper drops

### Survival & Health
- **Automatic eating**: Triggers when hunger < 14
- **Food priority**: Cooked meat > raw meat > bread > apples
- **Health monitoring**: Retreats when health < 5 HP
- **Hunger restoration**: Proper food values (cooked beef +8, bread +5, etc.)

### Tool Management
- **Best tool selection**: Automatically equips optimal tool for task
- **Tool priority**: Diamond > Iron > Stone > Wood
- **Task-specific tools**: Axe for wood, pickaxe for stone/ore, sword for combat
- **Visible hands**: Tools display in bot's main hand

---

## Architecture

### Hybrid FakePlayer System
- **AmbNpcEntity (FakePlayer)**: Handles all bot logic, AI, inventory, tasks (server-side)
- **AmbNpcVisualEntity**: PathfinderMob that mirrors FakePlayer position for client rendering
- **RealisticMovement**: Player-like physics with pathfinding, jumping, obstacle avoidance
- **RealisticActions**: Progressive mining, tool switching, eating, combat
- **StuckDetection**: Multi-level stuck detection and recovery
- **VerticalNavigation**: Vertical pathfinding and climbing support
- **HumanlikeMovement**: Natural movement patterns and head sway
- **TaskValidation**: Goal validation and reachability checks

### Technical Details
- **FakePlayer-safe**: 100% compatible with FakePlayer physics and mechanics
- **NBT Persistence**: Role, hunger, base location, crafting table position saved to world
- **Silent operation**: No step sounds, no sprint particles
- **A* Pathfinding**: Intelligent navigation with vertical movement support
- **Goal lock timer**: 400 ticks (20 seconds) for deliberate movement
- **Mining animations**: Proper crack progression and timing

---

Last updated: 2026-03-04
