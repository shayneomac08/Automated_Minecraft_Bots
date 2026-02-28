# Changelog

## Version 1.0.0 (Current)

### Recent Changes (2026-02-26)
- **Added:** Optimized FakePlayer navigation with auto-jump on collision and improved stuck recovery
- **Added:** `buildRichSurroundingsPrompt()` method for full player awareness in LLM prompts (inventory, surroundings, threats, time of day)
- **Enhanced:** Stuck detection now triggers every 30 ticks (1.5 seconds) instead of 60 ticks for faster recovery
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
