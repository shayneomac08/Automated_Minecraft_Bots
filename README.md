# Automated Minecraft Bots

AI-powered autonomous bots for Minecraft using LLM decision-making.

**Version:** 1.0.0
**Minecraft:** 1.21.1
**Mod Loader:** NeoForge 21.1.38-beta

## Features

- **LLM-Driven AI** - Bots use Grok API for autonomous decision-making
- **Real Player Rules** - Enforces crafting tables, tool requirements, mining speeds
- **Auto-Crafting** - Automatically crafts planks, sticks, and tools from gathered materials
- **Task System** - Supports gather_wood, mine_stone, build_shelter, explore, hunt_animals, etc.
- **Chat Commands** - Give bots commands via chat for manual override
- **Autonomous Problem-Solving** - Bots build scaffolding, adapt strategies when stuck

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

### Chat with Bots
Just type the bot's name in chat:
```
botname, gather wood
botname, build a shelter
```

## Documentation

- **CHANGELOG.md** - Version history and recent changes
- **API_KEYS_SETUP.md** - API key configuration guide
- **SWEEP.md** - Development guidelines
- **Debug_console.txt** - Paste game logs here for debugging

## Build

```bash
./gradlew clean build
```

JAR output: `build/libs/automated_minecraft_bots-1.0.0.jar`
