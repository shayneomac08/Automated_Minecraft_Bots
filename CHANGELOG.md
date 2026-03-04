# Changelog

## Version 1.0.0 (Current)

### MAJOR OVERHAUL (2026-03-04) - Movement System Rewrite

**New Systems Added:**
- **StuckDetection.java**: Multi-level recovery (jump/strafe → block placement → LLM override)
- **VerticalNavigation.java**: Multi-block jumps, climbing support, vertical pathfinding costs
- **HumanlikeMovement.java**: Natural head sway, smooth rotation, movement variation
- **TaskValidation.java**: Goal validation, reachability checks, automatic alternatives

**Enhanced Systems:**
- **4-Phase Door Navigation**: Added post-exit check phase, pre-exit goal restoration
- **A* Pathfinding**: Integrated vertical movement costs and neighbors
- **Movement Physics**: Added ±2% random variation for natural behavior

**Key Improvements:**
- Bots move naturally with wobbles and head movements (no more robot-like straight lines)
- Multi-level stuck recovery prevents infinite loops
- Task validation prevents unreachable goals (e.g., trees 10 blocks up)
- Door navigation more reliable with 4 phases instead of 3
- Comprehensive debug logging: `[AMB-STUCK]`, `[AMB-DOOR]`, `[AMB-TASK]`, `[AMB-VERTICAL]`

**Files Created:** 4 new movement system files
**Files Modified:** AmbNpcEntity.java, RealisticMovement.java
**Build Status:** ✅ Successful

### Previous Fixes (2026-03-03)

**Unreachable Goals & Vertical Distance:**
- Fixed infinite exit/re-entry loop when goal is too high (>5 blocks)
- Added vertical distance penalties in pathfinding (3x multiplier)
- Bot now finds reachable same-level alternatives

**Head Pitch Reset:**
- Fixed bot staring at ground by resetting X rotation to 0.0F
- Now looks forward naturally like a real player

**Exit Distance & Cooldown:**
- Increased exit distance from 3 to 8 blocks beyond door
- Increased exit cooldown from 5 to 10 seconds
- Prevents bot from looping back inside after exiting

**Goal Clearing Logic:**
- Fixed bot not moving when spawning near trees
- Check distance before setting goal to prevent immediate clearing
- Let mining start or stuck detection handle goal completion

**Movement Physics:**
- Fixed teleportation (24-25 blocks/tick) by correcting entity.move() usage
- Apply movement with collision, then clear horizontal velocity
- Bot now moves at real player speed (~0.13 blocks/tick)

**Door Navigation:**
- Implemented 3-phase door passage system (approach → pass through → verify)
- Added exit cooldown to prevent re-triggering
- Fixed infinite loop at doorways

### Earlier Fixes (2026-03-01)

**Pathfinding & Door Navigation:**
- Context-aware interior exit (only when task requires being outside)
- Physics enforcement (no climbing on doors/non-solid blocks)
- Multi-strategy stuck recovery (door rescue → force fall → strafe → recompute → abandon)
- A* pathfinding improvements for doors (passable with solid ground below)
- Multi-direction door collision detection

### Earlier Updates (2026-02-28)

**Realistic Movement & Action System:**
- Fully functional movement with player-like physics (pathfinding, jumping, swimming)
- Progressive block mining with breaking animations
- Automatic tool switching based on task
- Survival AI (auto-eating, health monitoring, tool efficiency)
- Hybrid FakePlayer + AmbNpcVisualEntity architecture
- Inventory GUI system (`/amb gui` and `/amb inventory` commands)

### Earlier Updates (2026-02-26)

**Human-Like Behavior & Resource Management:**
- Mining speed matches real player speed
- Smart crafting (no infinite loops, only craft what's needed)
- Intelligent hole escape (try walking/jumping first, break blocks as last resort)
- Recipe progression system

**Complete FakePlayer Action System:**
- Full player capabilities (eating, attacking, breeding, taming, fishing, sleeping)
- All actions FakePlayer-compatible
- Exposed to LLM for autonomous decision-making

**Core Features:**
- LLM-driven autonomous behavior (Grok, OpenAI, Gemini, Claude)
- Real player rules enforcement
- Auto-crafting system
- Natural item pickup and inventory management
