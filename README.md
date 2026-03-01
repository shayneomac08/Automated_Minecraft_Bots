# Automated Minecraft Bots

AI-powered autonomous bots for Minecraft using LLM decision-making.

**Version:** 1.0.0
**Minecraft:** 1.21.1
**Mod Loader:** NeoForge 21.1.38-beta

## Overview

Automated Minecraft Bots are LLM-driven FakePlayer entities that behave like real players: they move with player physics, mine with progressive block-breaking, craft using crafting tables, fight, hunt, and coordinate as a group.

## Recent fixes (2026-02-28)
- **Bot visibility**: Hybrid FakePlayer + BotVisualEntity architecture for client rendering
- **Bot movement**: Task execution system with block finding and movement goals
- **Inventory GUI**: `/amb gui` and `/amb inventory` commands to view bot inventories
- **Entity attributes**: Proper registration for client synchronization
- **Player-like mining**: Progressive block-breaking with tool awareness and correct drops
- **Tool equipping**: Visible and task-aware tool switching

## Quick Start

### Installation
1. Place JAR in `mods/` folder
2. Start Minecraft with NeoForge 1.21.1

### Setup API Key
Set your Grok API key in `config/automated_minecraft_bots.toml`:
```toml
llm_provider = "grok"
grok_api_key = "your-api-key-here"
```

### Commands
```
/amb spawn <name>           - Spawn a bot
/amb remove <name>          - Remove a bot
/amb list                   - List all bots
/amb give <name> <item>     - Give item to bot
```

## Documentation (consolidated)
Keep the number of markdown files to a minimum. Primary docs are:
- `CHANGELOG.md` — Version history and recent fixes
- `FEATURES.md` — Feature list and commands (concise)
- `README.md` — Quick start and overview

Temporary or developer notes should be archived or consolidated into the above files to avoid clutter.

## Build

```powershell
./gradlew clean build
```

JAR output: `build/libs/automated_minecraft_bots-1.0.0.jar`
