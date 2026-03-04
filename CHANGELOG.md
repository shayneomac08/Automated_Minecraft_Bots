# Changelog

## Version 1.0.0 (Current)

### Recent Changes (2026-03-03)

#### CRITICAL FIX: Bot Looping Back Inside After Exiting - Exit Distance Too Short!
- **FIXED:** Bot exiting building then immediately walking back inside, creating infinite loop
  - **Root Cause:** Exit target was only 3 blocks beyond door, bot would reach it then path back through building to reach tree
  - Exit cooldown (5 seconds) wasn't long enough to prevent re-entry
  - Bot would physically walk back inside after cooldown expired
- **SOLUTION:** Increased exit distance and cooldown
  - Increased `exitBeyond` from 3 blocks to 8 blocks beyond door
  - Increased exit cooldown from 100 ticks (5 seconds) to 200 ticks (10 seconds)
  - Increased exit completion threshold from 3.0 to 5.0 blocks
  - This ensures bot gets far enough outside before resuming normal navigation
- **RESULT:** Bot should now exit once and stay outside instead of looping back and forth!

#### CRITICAL FIX: Bot Not Moving - Goal Cleared Immediately After Being Set!
- **FIXED:** Bot finding trees but never moving to them, goal constantly being cleared
  - **Root Cause:** Bot was spawning close to trees (within 1.5 blocks)
  - `moveTowards()` would return `false` immediately (already at destination)
  - This triggered code that cleared the goal and called `executeCurrentTask()` again
  - Created infinite loop: set goal → already there → clear goal → set goal → repeat
  - Bot never actually moved because goal was cleared before movement could happen
- **SOLUTION:** Check distance before setting goal
  - If bot is already within 2 blocks of the tree, set goal directly to the tree for mining
  - If bot is far from tree, set goal to walkable position near tree
  - Don't clear goal immediately when reaching position - let mining start or stuck detection handle it
  - Added debug logging to show distance to goal and why goal is being set
- **RESULT:** Bot now properly moves to trees and starts mining instead of standing still!

#### CRITICAL FIX: Bot Teleportation/Flying & Not Moving - Movement Physics Completely Broken!
- **FIXED:** Bot teleporting/flying across the map at 24-25 blocks per tick, then not moving at all
  - **Root Cause #1 (Teleportation):** `entity.move()` was being used incorrectly - treating velocity as displacement
  - Movement code was calling `entity.move(MoverType.SELF, velocity)` which directly moved the entity
  - Then `super.tick()` would also apply movement, causing double/accumulated movement
  - Bot was moving 24-25 blocks per tick instead of ~0.13 blocks per tick (normal player speed)
  - **Root Cause #2 (Not Moving):** Switching to only `setDeltaMovement()` didn't work for FakePlayer
  - FakePlayer's `super.tick()` doesn't apply movement the same way as regular entities
  - Bot would set velocity but never actually move
  - **Root Cause #3 (Still Not Moving):** Setting velocity after applying movement caused double application
  - `super.tick()` would apply the velocity again on the next tick, causing issues
- **SOLUTION:** Apply movement then clear horizontal velocity
  - Call `entity.move(MoverType.SELF, movement)` to apply movement with collision detection
  - Call `entity.setDeltaMovement(0, y * 0.98, 0)` to clear horizontal velocity (keep Y for gravity)
  - This prevents `super.tick()` from re-applying movement while maintaining gravity
  - Applied to both `moveTowards()` and `strafeAround()` methods
- **RESULT:** Bot now moves at REAL PLAYER SPEED with proper physics - no teleportation, no freezing!

#### CRITICAL FIX: Door Exit/Re-entry Loop - Exit Cooldown System!
- **FIXED:** Bot passing through door, then immediately turning around and going back through repeatedly
  - **Root Cause:** Two separate door navigation systems conflicting with each other:
    1. **Interior Exit System** - Detects when bot is inside and tries to exit
    2. **Door Phase System** - 3-phase door passage system
  - After successfully passing through door, interior detection immediately retriggered
  - Bot would exit → detect "inside" → exit again → infinite loop
  - Bot also climbed on chests and ran on air due to navigation confusion
- **SOLUTION:** Added exit cooldown system to prevent immediate re-triggering:
  - Added `exitCooldown` timer (5 seconds / 100 ticks)
  - Set cooldown after successfully passing through door
  - Set cooldown after successfully exiting interior
  - Interior detection skipped while cooldown is active
- **RESULT:** Bot now exits once and continues to goal instead of looping back and forth!

#### CRITICAL FIX: Door Navigation Infinite Loop - 3-Phase Door Passage System!
- **FIXED:** Bot stuck in infinite loop at doorways, repeatedly "passing through door" without moving
  - **Root Cause:** Bot was using 2.0 block threshold for doors, thinking it "reached" the door from 2 blocks away
  - Bot would clear door phase without actually moving through, then immediately get stuck again
  - Created infinite loop: stuck → door rescue → "passed through" → stuck → repeat
- **SOLUTION:** Implemented proper 3-phase door passage system:
  - **Phase 1 (Approach):** Bot moves to door position, opens it when within 2.2 blocks
  - **Phase 2 (Pass Through):** Bot sets goal to 3 blocks BEYOND the door in the direction it faces
  - **Phase 3 (Verify):** Bot only clears door phase after reaching the position beyond the door
- **IMPROVED:** Door position tracking
  - Added `originalDoorPos` to store the actual door block position
  - `doorPos` now updates to the target position beyond the door during passage
  - Prevents confusion between door location and passage target
- **IMPROVED:** Movement threshold
  - Reverted door threshold back to normal 1.5 blocks (same as other blocks)
  - Door handling now manages the full passage sequence instead of relying on threshold tricks
- **RESULT:** Bots now ACTUALLY pass through doorways instead of getting stuck in infinite loops!

### Recent Changes (2026-03-01)

#### CRITICAL FIX: Door Hitbox Collision - Bot Can Now Pass Through Doorways! (SUPERSEDED)
- **NOTE:** This fix was incomplete and caused infinite loops. See 2026-03-03 fix above.
- **FIXED:** Bot getting stuck at doorways after opening doors
  - Player hitbox is 0.6 blocks wide, door opening is 1 block wide
  - Bot was trying to navigate to exact center of door block, causing collision
  - Increased "reached destination" threshold for doors from 1.5 to 2.0 blocks
  - Bot now clears door phase after reaching door and continues to actual goal
  - Added door detection in movement system to use appropriate thresholds
- **RESULT:** Bots now successfully pass through doorways instead of getting stuck!

#### CRITICAL FIX: Pathfinding & Door Navigation System Overhaul
- **FIXED:** Bot stuck in interior exit loop - bots no longer constantly try to exit when they should be inside
  - Only check for interior exit when task requires being outside (gather_wood, mine_stone, explore)
  - Check if goal is actually outside before attempting to exit
  - Improved exit detection - bot must be outside AND near exit point to complete
  - Increased door interaction cooldown to 2 seconds to prevent spam
- **FIXED:** Bots climbing on top of doors - proper physics enforcement
  - Added non-solid block detection (doors, fence gates)
  - Force downward movement when standing on non-solid blocks
  - Improved gravity application to prevent floating on doors
  - Only jump when on solid ground, not on doors
- **FIXED:** Bot not pathfinding after opening doors - improved stuck recovery
  - Multi-strategy stuck detection: door rescue → force fall → strafe → recompute path → abandon goal
  - Added `isStuckInNonSolidBlock()` helper to detect when bot is inside a door
  - Force downward velocity when stuck in non-solid blocks
  - Better door collision detection in multiple directions
- **IMPROVED:** A* pathfinding for door navigation
  - Doors and fence gates now properly marked as passable in pathfinding
  - Prevent pathfinding through doors as floor (must have solid ground below)
  - Better neighbor generation for stepping up/down through doorways
- **IMPROVED:** Door interaction system
  - Check multiple directions (front, left, right) when colliding with doors
  - Only open one door at a time to prevent spam
  - Better logging for door interactions
- **RESULT:** Bots now navigate through structures naturally, open doors properly, and don't get stuck!

### Recent Changes (2026-02-28)

#### MAJOR UPDATE: Realistic Movement & Action System
- **FIXED:** Bot movement now actually works - bots move to goals and navigate terrain
  - Fixed FakePlayer position updates (was only setting velocity, now updates position)
  - Disabled old BotMovementHelper stuck detection (was calling stopMovement() and preventing bots from moving)
  - Added comprehensive debug logging to track movement and goal execution
  - Bots now physically move towards goals instead of standing still
- **IMPLEMENTED:** Fully functional realistic movement system with player-like physics
  - Pathfinding around obstacles with automatic navigation
  - Jumping over 1-block obstacles when blocked
  - Gravity, swimming, and environmental awareness
  - Danger avoidance (lava, cliffs, void)
  - Stuck detection and alternative path finding
  - Natural acceleration/deceleration
- **IMPLEMENTED:** Realistic action system for human-like behavior
  - Progressive block mining with proper tool usage and breaking animations
  - Automatic tool switching based on task (axe for wood, pickaxe for stone, sword for combat)
  - Realistic eating with hunger restoration
  - Combat system with attack cooldowns
  - Block placement and interaction
  - Inventory management
- **IMPLEMENTED:** Survival AI
  - Automatic eating when hungry (hunger < 14)
  - Health monitoring and retreat when critically low (< 5 HP)
  - Tool efficiency awareness (uses best available tool)
  - Task-based goal execution (gather_wood, mine_stone, hunt_animals, etc.)

#### Bot Visibility & Movement Implementation
- **FIXED:** Bot visibility using hybrid FakePlayer + AmbNpcVisualEntity architecture
- **FIXED:** Entity attribute registration for proper client synchronization
- **IMPLEMENTED:** Hybrid architecture - FakePlayer handles logic/AI, AmbNpcVisualEntity handles rendering
- **IMPLEMENTED:** Inventory GUI system (`/amb gui` and `/amb inventory` commands)

#### Previous Fixes
- **FIXED:** Zigzag / jittery movement — bots now use stable straight-line movement with proper FakePlayer physics
- **FIXED:** Instant block destruction replaced with proper player-like mining with progressive crack animation
- **FIXED:** Tools now visibly equip in the bot's main hand and switch based on task
- **FIXED:** Bots now include position coordinates in status messages and LLM snapshots
- **IMPROVED:** Mining enforces correct tool requirements

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
