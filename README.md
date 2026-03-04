# Automated Minecraft Bots

AI-powered autonomous bots for Minecraft with human-like movement and intelligent navigation.

**Version:** 1.0.0
**Minecraft:** 1.21.1
**Mod Loader:** NeoForge 21.1.38-beta

## Overview

Automated Minecraft Bots are LLM-driven FakePlayer entities that behave like real players. They feature natural movement with head sway, intelligent pathfinding with vertical navigation, multi-level stuck recovery, and realistic actions like progressive mining and automatic tool switching.

## Key Features

- **Human-like Movement**: Natural physics with smooth acceleration, head sway, and movement variation
- **Intelligent Navigation**: Multi-level stuck detection, vertical pathfinding, 4-phase door navigation
- **Task Validation**: Pre-validates goals for reachability before setting them
- **Realistic Actions**: Progressive mining, automatic tool switching, hunger management
- **Survival AI**: Auto-eating, health monitoring, intelligent task execution

## Quick Start

### Installation
1. Download the latest JAR from releases
2. Place in your `mods/` folder
3. Start Minecraft with NeoForge 1.21.1

### Basic Commands
```
/amb spawn bot              - Spawn a bot named "bot"
/amb task bot gather_wood   - Assign wood gathering task
/amb list                   - List all active bots
/amb remove bot             - Remove the bot
```

### Optional: LLM Integration
Set your API key in `config/automated_minecraft_bots.toml`:
```toml
llm_provider = "grok"
grok_api_key = "your-api-key-here"
```

Supported providers: `grok`, `openai`, `gemini`, `claude`

## Available Tasks

- `gather_wood` - Find and mine trees
- `mine_stone` - Mine stone blocks
- `mine_ore` - Mine ore blocks (coal, iron, copper, gold, diamond, etc.)
- `mine_dirt` - Mine dirt blocks
- `explore` - Wander and explore the world
- `hunt_animals` - Hunt nearby animals

## Documentation

- **FEATURES.md** - Complete feature list and all commands
- **CHANGELOG.md** - Version history and recent changes
- **README.md** - This file (quick start guide)

## Building from Source

```powershell
./gradlew clean build
```

JAR output: `build/libs/automated_minecraft_bots-1.0.0.jar`

## Recent Updates (2026-03-04)

**Major Movement System Overhaul:**
- Added 4 new movement systems (StuckDetection, VerticalNavigation, HumanlikeMovement, TaskValidation)
- Enhanced 4-phase door navigation with post-exit check
- Integrated vertical pathfinding costs into A* algorithm
- Added natural movement variation (±2-5% random deviation)
- Multi-level stuck recovery (jump/strafe → block placement → LLM override)

See CHANGELOG.md for detailed version history.
