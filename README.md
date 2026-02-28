# 🤖 Automated Minecraft Bots

A Minecraft mod that adds intelligent AI-powered bots capable of autonomous resource gathering, crafting, building, farming, and interaction.

**Current Version:** 1.0.28 - Inventory System Removed
**Status:** ⚠️ INVENTORY DISABLED - Core functionality intact, inventory will be rebuilt later

---

## ✨ Features

- 🧠 **Autonomous AI** - Bots make smart decisions every 30 seconds based on needs
- 🏃 **Player-Like Movement** - Auto-sprint, smooth pathfinding, fall damage, respawn
- 🧲 **Item Pickup** - Bots can pick up dropped items from the ground (like real players)
- 🤝 **Knowledge Sharing** - Bots share discoveries with nearby group members (8 block range)
- 👥 **Group System** - Organize bots into collaborative groups
- 🎭 **Personality Dialects** - Bots communicate with unique styles (grok, gemini, chatgpt, neutral)
- 🗺️ **Map Memory** - Bots create and share maps of discovered resources
- 🧠 **Memory System** - Bots remember discovered items and resource locations
- 💱 **Trust-Based Bartering** - Same group = free sharing, cross-group = trade or refuse
- 👑 **Social Hierarchies** - Leaders share freely, scouts barter, workers use group trust
- 🎮 **GUI Inventory** - Open bot inventory with /amb gui command
- 📦 **Proper Spawning** - Bots spawn at player height (no ground clipping)
- 🧬 **Breeding System** - Bots can breed and create offspring with inherited traits
- ⏳ **Lifespan System** - Bots age and die of old age after 90 real days
- 🎭 **Personality Traits** - Each bot has 3 unique personality traits (curious, brave, etc.)
- 👫 **Gender System** - Bots have genders (male/female) affecting breeding
- 🪦 **Death & Mourning** - Old bots get tombstones, nearby bots mourn their loss
- 📊 **Population Cap** - Maximum 30 bots to prevent server overload
- 🌲 **Resource Gathering** - Wood, stone, ores, dirt with enhanced vision
- 🔨 **LLM-Driven Crafting** - Bots craft ONLY when LLM decides (no automatic crafting loops)
- 🏗️ **Building System** - Auto-builds chests when inventory full
- 🌾 **Farming** - Plants, grows, and harvests wheat
- 🔥 **Smelting** - Smelts ores in furnaces
- 💬 **LLM Integration** - Chat with bots using OpenAI, Gemini, or Grok
- 🎨 **Visual Feedback** - Held items render properly in bot's hand

---

## 🚀 Quick Start

### 1. Installation
1. Download the mod JAR
2. Place in `mods/` folder
3. Start Minecraft with NeoForge 1.21.1

### 2. Spawn a Bot
```bash
/amb spawn Steve
```

### 3. Enable Autonomous AI
```bash
/amb brain Steve on
```

### 4. Watch the Bot Work!
The bot will automatically:
- Gather 16 logs
- Mine 32 cobblestone
- Craft tools as needed
- Build chests when inventory full
- Farm when health is low

---

## 📚 Documentation

**[📖 Documentation Index](DOCUMENTATION_INDEX.md)** - Complete guide to all documentation

### Quick Links
- **[GETTING_STARTED.md](GETTING_STARTED.md)** - ⭐ Start here! Setup and basic usage
- **[COMMANDS.md](COMMANDS.md)** - Complete command reference
- **[FEATURES.md](FEATURES.md)** - Detailed feature documentation
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Common issues and solutions
- **[LLM_DRIVEN_ARCHITECTURE.md](LLM_DRIVEN_ARCHITECTURE.md)** - How LLM-driven bots work
- **[CHANGELOG.md](CHANGELOG.md)** - Version history
- **[CONFIGURATION.md](CONFIGURATION.md)** - Configuration options
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Development guide

---

## 🎮 Basic Commands

```bash
# Bot Management
/amb spawn <name>              # Spawn a bot
/amb brain <name> on           # Enable autonomous AI
/amb brainall on               # Enable ALL bot brains at once
/amb brainall off              # Disable ALL bot brains at once
/amb autocraft <name> on|off   # Control auto-crafting (default: OFF)
/amb gui <name>                # Open bot inventory GUI
/amb info <name>               # View bot details (gender, age, traits)
/amb population                # Check current population (X/30)

# Social System
/amb role <name> <role>        # Set role (leader, worker, scout)
/amb dialect <name> <dialect>  # Set dialect (grok, gemini, chatgpt, neutral)
/amb group <name> <group>      # Set group for knowledge sharing

# Breeding System
/amb breed <bot1> <bot2>       # Attempt breeding between two bots

# Task Assignment
/amb task <name> gather_wood   # Gather wood
/amb task <name> mine_stone    # Mine stone
/amb task <name> farm          # Farm wheat

# Item Management
/amb give <name> <item> <amount>   # Give items to bot
/amb inventory <name>              # View inventory (text)
```

---

## 🔧 Requirements

- **Minecraft:** 1.21.1
- **NeoForge:** 1.21.1
- **Java:** 21+

---

## 🤝 Contributing

Contributions are welcome! See [DEVELOPMENT.md](DEVELOPMENT.md) for development setup and guidelines.

---

## 📝 License

[Add your license here]

---

## 🎯 Support

For issues, questions, or feature requests, please open an issue on GitHub.

---

**Ready to automate your Minecraft world!** 🚀
