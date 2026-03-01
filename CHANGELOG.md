# Changelog

## Version 1.0.0 (Current)

### Recent Changes (2026-02-28)

#### HOTFIX: Realistic Player-Like Bot Behavior
- **FIXED:** Zigzag / jittery movement — bots now use stable straight-line movement with proper FakePlayer physics (gravity, friction, step-up jump behavior) so they move like real players.
- **FIXED:** Instant block destruction replaced with proper player-like mining: progressive crack animation, mining timing based on block hardness and tool speed, correct item drops, and stat awarding.
- **FIXED:** Tools now visibly equip in the bot's main hand and switch based on task (axe for wood, pickaxe for stone, sword for combat) with proper client synchronization.
- **FIXED:** Bots now include position coordinates in status messages and LLM snapshots to improve awareness and coordination.
- **IMPROVED:** Mining enforces correct tool requirements (bots abort mining and announce required tool instead of destroying blocks without drops).

> Note: Detailed technical notes and testing guides have been consolidated into the main project documentation: `CHANGELOG.md`, `FEATURES.md`, and `README.md`. Additional temporary markdown files were archived to reduce clutter.

### Recent Changes (2026-02-26)

#### CRITICAL FIX: Human-Like Behavior & Resource Management!
- **FIXED:** Mining speed now matches REAL PLAYER speed exactly (removed incorrect tool efficiency calculation)
- **FIXED:** Infinite crafting loops eliminated - bots only craft what they need (max 2 logs at a time, planks capped at 8)
- **FIXED:** Smart hole escape - bots try walking/jumping FIRST, only break blocks if completely trapped (3+ solid blocks)
- **FIXED:** Recipe progression system - bots can't craft diamond/iron tools without learning recipes (basic wood/stone always known)
- **IMPROVED:** Hole escape logic now human-like:
  1. Try walking/jumping out (like a real player)
  2. Check if shallow hole (can jump out)
  3. LAST RESORT: Break blocks only if surrounded
- **RESULT:** Bots now behave like REAL players - adaptive, smart, resource-conscious!

#### MAJOR UPDATE: Complete FakePlayer Action System - REAL PLAYER CAPABILITIES!
- **CRITICAL:** Bots now have FULL player action capabilities - eating, attacking, breeding, taming, fishing, sleeping!
- **NEW ACTIONS ADDED (All FakePlayer Compatible):**
  - `attackEntity()` - Proper player attack with swing animation and cooldown reset
  - `eatFood()` - Eat food from inventory to restore hunger (uses DataComponents.FOOD)
  - `useItemOnBlock()` - Place blocks, use doors, buttons, etc. with proper interaction
  - `useItem()` - Use/activate items in hand (tools, weapons, consumables)
  - `interactWithEntity()` - Interact with entities for trading, breeding, taming
  - `attackNearestHostile()` - Find and attack hostile mobs with proper combat
  - `breedAnimals()` - Breed animals with appropriate food items
  - `tameAnimal()` - Tame wolves, cats, horses, parrots
  - `fish()` - Use fishing rod to catch fish
  - `sleep()` - Sleep in beds
- **EXPOSED TO LLM:** All new actions available as tasks: `eat_food`, `use_item`, `attack_mob`, `breed_animals`, `tame_animal`, `fish`, `sleep`
- **RESULT:** Bots are now REAL players - they can do EVERYTHING a human player can do!

#### MAJOR FIX: Complete FakePlayer Movement System Overhaul
- **CRITICAL:** Eliminated ALL FakePlayer incompatible movement code across entire project
- **FIXED:** Replaced problematic movement and navigation with FakePlayer-safe methods and goal-based movement
- **RESULT:** Bots now move with proper player physics - no more movement conflicts or stuck issues!

#### Previous Fixes
- **FIXED:** Bot jumping now works correctly! Changed from `setDeltaMovement()` to `jumpFromGround()` for proper FakePlayer physics
- **FIXED:** Auto-jump on collision now actually jumps over obstacles
- **FIXED:** Stuck recovery now properly jumps instead of just setting velocity
- **Added:** Intelligent hole escape system - bot now breaks blocks to escape when stuck in holes/pits
- **Added:** `attemptEscapeFromHole()` method that checks all 4 directions + above for blocks to break
- **Added:** `canBreakBlock()` safety check to avoid breaking bedrock or unbreakable blocks
- **Added:** Optimized FakePlayer navigation with auto-jump on collision and improved stuck recovery
- **Added:** `buildRichSurroundingsPrompt()` method for full player awareness in LLM prompts (inventory, surroundings, threats, time of day)
- **Enhanced:** Stuck detection now triggers every 30 ticks (1.5 seconds) with intelligent escape behavior
- **Enhanced:** Auto-jump when `horizontalCollision` is detected (real player feel)
- **Fixed:** ActionExecutor.java compilation issues - removed legacy Mob-based code, updated to use AmbNpcEntity (FakePlayer architecture)
- **Cleaned:** Removed temporary documentation files (FIXES_SUMMARY.md, CLEANUP_SUMMARY.md, PATHFINDING_FIX.md)
- Crafting table requirement: 3x3 recipes require placed crafting table
- Auto-crafting: 2x2 recipes (planks, sticks, table) craft in inventory
- Block breaking animations and proper item drops
- Task support: explore, hunt_animals, manage_resources, place_crafting_table
- Chat command execution system
- Autonomous problem-solving (scaffolding, resource gathering)

### Core Features
- LLM-driven autonomous bot behavior (Grok API)
- Real player rules enforcement (tools, crafting, mining)
- FakePlayer implementation with full player capabilities
- Auto-crafting system for tools and resources
- Natural item pickup and inventory management
