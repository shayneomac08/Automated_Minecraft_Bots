# ⚙️ Configuration Guide

## 📋 Overview
Configuration options for the Automated Minecraft Bots mod.

---

## 📁 Config File Location

**Path:** `config/automated_minecraft_bots.toml`

The config file is automatically created on first launch.

---

## 🔧 Configuration Options

### LLM Provider Settings

```toml
[llm]
# LLM provider: "openai", "anthropic", or "ollama"
provider = "openai"

# OpenAI settings
openai_api_key = "your-api-key-here"
openai_model = "gpt-3.5-turbo"

# Anthropic settings
anthropic_api_key = "your-api-key-here"
anthropic_model = "claude-3-sonnet-20240229"

# Ollama settings
ollama_base_url = "http://localhost:11434"
ollama_model = "llama2"
```

---

## 🔑 Setting Up API Keys

### OpenAI
1. Get API key from: https://platform.openai.com/api-keys
2. Open `config/automated_minecraft_bots.toml`
3. Set `openai_api_key = "sk-..."`
4. Set `provider = "openai"`

### Anthropic
1. Get API key from: https://console.anthropic.com/
2. Open `config/automated_minecraft_bots.toml`
3. Set `anthropic_api_key = "sk-ant-..."`
4. Set `provider = "anthropic"`

### Ollama (Local)
1. Install Ollama: https://ollama.ai/
2. Run: `ollama pull llama2`
3. Open `config/automated_minecraft_bots.toml`
4. Set `provider = "ollama"`
5. Ensure Ollama is running: `ollama serve`

---

## 🎮 Bot Behavior Settings

```toml
[bot]
# Resource gathering targets
wood_target = 16
stone_target = 32
iron_target = 12
diamond_target = 6

# Inventory management
chest_build_threshold = 27  # Build chest when 27/36 slots full

# Health thresholds
farm_health_threshold = 12  # Farm when health < 12
shelter_health_threshold = 15  # Seek shelter when health < 15

# Movement settings
movement_speed = 1.3
pathfinding_node_multiplier = 2.0
can_open_doors = true
can_float = true

# Stuck detection
stuck_threshold_ticks = 40  # 2 seconds
jump_threshold_ticks = 60   # 3 seconds
nudge_distance = 0.04       # blocks

# Vision range
horizontal_range = 48  # blocks
vertical_range_up = 60  # blocks
vertical_range_down = 12  # blocks
```

---

## 🔄 Applying Configuration Changes

### Method 1: Restart Server
1. Stop Minecraft server
2. Edit `config/automated_minecraft_bots.toml`
3. Start Minecraft server

### Method 2: Reload Config (if supported)
```bash
/amb reload
```

---

## 📊 Default Values

| Setting | Default | Description |
|---------|---------|-------------|
| `wood_target` | 16 | Logs to gather before stopping |
| `stone_target` | 32 | Cobblestone to mine before stopping |
| `iron_target` | 12 | Iron ingots needed for tools |
| `diamond_target` | 6 | Diamonds needed for tools |
| `chest_build_threshold` | 27 | Inventory slots before building chest |
| `farm_health_threshold` | 12 | Health level to trigger farming |
| `shelter_health_threshold` | 15 | Health level to seek shelter |
| `movement_speed` | 1.3 | Bot movement speed multiplier |
| `pathfinding_node_multiplier` | 2.0 | Pathfinding range multiplier |
| `stuck_threshold_ticks` | 40 | Ticks before stuck recovery |
| `jump_threshold_ticks` | 60 | Ticks before jumping |
| `horizontal_range` | 48 | Horizontal vision range |
| `vertical_range_up` | 60 | Upward vision range |
| `vertical_range_down` | 12 | Downward vision range |

---

## 🎯 Recommended Settings

### For Performance
```toml
[bot]
pathfinding_node_multiplier = 1.5  # Reduce pathfinding range
horizontal_range = 32              # Reduce vision range
```

### For Efficiency
```toml
[bot]
wood_target = 32                   # Gather more wood per trip
stone_target = 64                  # Gather more stone per trip
movement_speed = 1.5               # Faster movement
```

### For Realism
```toml
[bot]
movement_speed = 1.0               # Normal player speed
can_open_doors = false             # Can't open doors
stuck_threshold_ticks = 60         # More patient stuck detection
```

---

## 🐛 Troubleshooting

### LLM Not Working
- Check API key is correct
- Verify provider is set correctly
- Check internet connection (for OpenAI/Anthropic)
- Check Ollama is running (for Ollama)

### Bots Moving Too Slow
- Increase `movement_speed` (default: 1.3)
- Increase `pathfinding_node_multiplier` (default: 2.0)

### Bots Getting Stuck
- Decrease `stuck_threshold_ticks` (default: 40)
- Increase `nudge_distance` (default: 0.04)
- Enable `can_open_doors` (default: true)

### Bots Not Finding Resources
- Increase `horizontal_range` (default: 48)
- Increase `vertical_range_up` (default: 60)
- Increase `vertical_range_down` (default: 12)

---

## 📚 See Also

- **[GETTING_STARTED.md](GETTING_STARTED.md)** - Quick start guide
- **[COMMANDS.md](COMMANDS.md)** - Command reference
- **[FEATURES.md](FEATURES.md)** - Feature documentation

---

**Configuration Complete!** Customize your bots to your preferences. ⚙️
