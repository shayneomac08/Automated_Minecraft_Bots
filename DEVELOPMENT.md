# 🛠️ Development Guide

## 📋 Overview
Technical documentation for developers working on the Automated Minecraft Bots mod.

---

## 🏗️ Project Structure

```
automated_minecraft_bots/
├── src/main/java/com/shayneomac08/automated_minecraft_bots/
│   ├── agent/
│   │   └── ActionExecutor.java          # Bot action execution
│   ├── bot/
│   │   ├── AvatarFactory.java           # Bot avatar/skin management
│   │   ├── BotBrain.java                # LLM integration & decision-making
│   │   ├── BotSurvivalNeeds.java        # Health/hunger tracking
│   │   └── BotTicker.java               # Bot tick management
│   ├── client/
│   │   └── AmbNpcRenderer.java          # Bot rendering
│   ├── command/
│   │   └── AmbCommands.java             # All /amb commands
│   ├── entity/
│   │   ├── AmbNpcEntity.java            # Main bot entity class
│   │   └── AMBTaskGoal.java             # Task execution & resource gathering
│   ├── llm/
│   │   ├── OpenAIResponsesClient.java   # OpenAI API client
│   │   ├── SimpleJson.java              # JSON parsing
│   │   └── UnifiedLLMClient.java        # Multi-LLM provider support
│   ├── registry/
│   │   └── ModEntities.java             # Entity registration
│   ├── AutomatedMinecraftBotsClient.java # Client-side initialization
│   └── Config.java                       # Configuration management
├── config/
│   └── automated_minecraft_bots.toml     # Configuration file
└── README.md
```

---

## 🔑 Key Classes

### AmbNpcEntity.java
**Main bot entity class - CURRENT STATE (v1.0.1)**

**Key Methods:**
- `tick()` - Main tick loop with PlayerMoveControl, fall damage, respawn
- `assessNeedsAndDecideGoal()` - Real AI decision making (every 10 seconds)
- `getGoalForTask(String task)` - Returns Goal for task string
- `turnBrainOn()` - Enables autonomous mode
- `setBrainEnabled(boolean)` - Sets brain state
- `giveItem(String, int)` - Full item registry (300+ items + aliases)

**Inner Classes:**
- `BuildChestGoal` - Builds real chests when inventory full (actually works!)
- `SmeltingGoal` - Smelts ores in furnaces
- `FarmingGoal` - Farms wheat

**Important Fields:**
- `brainEnabled` - Autonomous AI state
- `inventory` - Bot's inventory (SimpleContainer)
- `goalSelector` - Minecraft's goal system
- `ITEM_REGISTRY` - Full item registry for /amb give command

**What's Removed:**
- ❌ doGatherWood (completely purged)
- ❌ tickGoal (completely purged)
- ❌ All legacy goal classes
- ❌ Old zombie code comments

---

### AMBTaskGoal.java
**Task execution and resource gathering - CLEAN (v1.0.1)**

**Key Methods:**
- `canUse()` - Checks if goal can be used, switches to BuildChestGoal when done
- `start()` - Initializes task (improved pathfinding with doors, float, 2x nodes)
- `tick()` - Main task execution loop (no more endless loops!)
- `hasEnoughResources()` - Checks resource targets (uses ItemTags for logs)
- `findNearestValidBlock()` - Finds nearest target block
- `selectBestTool()` - Selects best tool for task (with durability check)
- `autoCraftTools()` - Auto-crafts tools when needed

**Task Types:**
- `gather_wood` - Chops trees (16 logs target)
- `mine_stone` - Mines cobblestone (32 target)
- `mine_ore` - Mines ores
- `mine_dirt` - Digs dirt
- `farm` - Farms wheat
- `smelt` - Smelts ores

**Important Fields:**
- `taskType` - Current task type
- `targetBlock` - Current target block
- `resourcesCollected` - Resources gathered count
- `stuckTicks` - Stuck detection counter

**What's Fixed:**
- ✅ No more endless resource loops
- ✅ Switches to BuildChestGoal when done
- ✅ Tiny nudge stuck recovery (no Superman flying)
- ✅ ItemTags for accurate log counting
- ✅ Clean, optimized code

---

### AmbCommands.java
**All /amb commands**

**Registered Commands:**
- `spawn` - Spawns bot
- `despawn` - Removes bot
- `brain` - Enables/disables AI
- `task` - Assigns task
- `give` - Gives items
- `inventory` - Shows inventory
- `status` - Shows status
- `list` - Lists all bots
- `chat` - LLM chat
- `llm` - Sets LLM provider

**Command Registration:**
```java
public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("amb")
        .then(Commands.literal("spawn")...)
        .then(Commands.literal("brain")...)
        // etc.
    );
}
```

---

### BotBrain.java
**LLM integration and decision-making**

**Key Methods:**
- `processLLMResponse(String response)` - Processes LLM responses
- `extractGoalFromResponse(String response)` - Extracts goals from text
- `updatePromptContext()` - Updates LLM context

**LLM Providers:**
- OpenAI (GPT-3.5, GPT-4)
- Anthropic (Claude)
- Ollama (Local models)

---

### Config.java
**Configuration management**

**Key Methods:**
- `load()` - Loads config from file
- `save()` - Saves config to file
- `getLLMProvider()` - Gets current LLM provider
- `getAPIKey()` - Gets API key for provider

---

## 🔧 Building the Project

### Prerequisites
- Java 21+
- Gradle 8.5+
- NeoForge 1.21.1

### Build Commands
```bash
# Clean build
./gradlew clean build

# Run client
./gradlew runClient

# Run server
./gradlew runServer

# Generate IDE files
./gradlew idea      # IntelliJ IDEA
./gradlew eclipse   # Eclipse
```

### Build Output
- **JAR Location:** `build/libs/automated_minecraft_bots-1.0.0.jar`
- **Mod ID:** `automated_minecraft_bots`

---

## 🎯 Adding New Features

### Adding a New Task

**1. Add task type to AMBTaskGoal.java:**
```java
private boolean hasEnoughResources() {
    int count = switch (taskType) {
        case "gather_wood" -> /* ... */;
        case "mine_stone" -> /* ... */;
        case "my_new_task" -> /* check resources */;
        default -> 0;
    };
    return count >= needed;
}
```

**2. Add task to getGoalForTask() in AmbNpcEntity.java:**
```java
private Goal getGoalForTask(String task) {
    return switch (task) {
        case "farm" -> new FarmingGoal(this);
        case "my_new_task" -> new MyNewTaskGoal(this);
        default -> new AMBTaskGoal(this, task);
    };
}
```

**3. Add command in AmbCommands.java:**
```java
.then(Commands.literal("my_new_task")
    .executes(context -> {
        // Task assignment logic
    })
)
```

---

### Adding a New Command

**1. Register command in AmbCommands.java:**
```java
public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("amb")
        .then(Commands.literal("mycommand")
            .then(Commands.argument("name", StringArgumentType.string())
                .executes(context -> {
                    String name = StringArgumentType.getString(context, "name");
                    // Command logic here
                    return 1;
                })
            )
        )
    );
}
```

**2. Add helper method:**
```java
private static int executeMyCommand(CommandContext<CommandSourceStack> context, String name) {
    // Implementation
    return 1;
}
```

---

### Adding a New Goal

**1. Create Goal class (inner class in AmbNpcEntity.java):**
```java
public static class MyNewGoal extends Goal {
    private final AmbNpcEntity bot;

    public MyNewGoal(AmbNpcEntity bot) {
        this.bot = bot;
    }

    @Override
    public boolean canUse() {
        // Check if goal can be used
        return true;
    }

    @Override
    public void start() {
        // Initialize goal
    }

    @Override
    public void tick() {
        // Execute goal logic
    }

    @Override
    public boolean canContinueToUse() {
        // Check if goal should continue
        return true;
    }
}
```

**2. Register in getGoalForTask():**
```java
case "my_new_goal" -> new MyNewGoal(this);
```

---

## 🧪 Testing

### Manual Testing
```bash
# 1. Build mod
./gradlew build

# 2. Run client
./gradlew runClient

# 3. In-game testing
/amb spawn TestBot
/amb brain TestBot on
/amb status TestBot
```

### Console Logging
Add debug logging:
```java
System.out.println("[AMB] Debug message: " + value);
```

Watch console for `[AMB]` messages.

---

## 📊 Performance Considerations

### Pathfinding
- Use `setMaxVisitedNodesMultiplier(2.0f)` for long-range
- Enable `setCanOpenDoors(true)` for better navigation
- Enable `setCanFloat(true)` for water navigation

### Tick Optimization
- Run expensive operations every N ticks (e.g., `tickCount % 200 == 0`)
- Use server-side checks: `!this.level().isClientSide()`
- Avoid unnecessary object creation in tick loops

### Resource Gathering
- Batch process blocks (e.g., entire tree, ore vein)
- Use ItemTags instead of individual item checks
- Cache frequently accessed values

---

## 🐛 Debugging

### Common Issues

**Bot Not Moving:**
- Check `brainEnabled` flag
- Verify goal is set: `goalSelector.getRunningGoals()`
- Check navigation: `bot.getNavigation().isDone()`

**Bot Stuck:**
- Check `stuckTicks` counter
- Verify pathfinding: `bot.getNavigation().getPath()`
- Check for obstacles

**Resources Not Counting:**
- Use ItemTags instead of specific items
- Check inventory: `bot.getInventory().countItem()`
- Verify FakePlayer hands inventory

**Console Spam:**
- Reduce logging frequency
- Use conditional logging: `if (tickCount % 100 == 0)`

---

## 📚 Useful Resources

### NeoForge Documentation
- https://docs.neoforged.net/

### Minecraft Forge Documentation
- https://mcforge.readthedocs.io/

### Minecraft Wiki
- https://minecraft.wiki/

---

## 🔄 Version Control

### Git Workflow
```bash
# Create feature branch
git checkout -b feature/my-new-feature

# Make changes
git add .
git commit -m "Add my new feature"

# Push to remote
git push origin feature/my-new-feature

# Create pull request
```

---

## 📝 Code Style

### Naming Conventions
- **Classes:** PascalCase (e.g., `AmbNpcEntity`)
- **Methods:** camelCase (e.g., `assessNeedsAndDecideGoal`)
- **Variables:** camelCase (e.g., `brainEnabled`)
- **Constants:** UPPER_SNAKE_CASE (e.g., `MAX_HEALTH`)

### Comments
```java
/**
 * Javadoc for public methods
 * @param name Bot name
 * @return Success status
 */
public boolean spawnBot(String name) {
    // Implementation comment
    return true;
}
```

### Logging
```java
System.out.println("[AMB] " + botName + " action: " + details);
```

---

## 🚀 Deployment

### Building Release
```bash
# Clean and build
./gradlew clean build

# JAR location
build/libs/automated_minecraft_bots-1.0.0.jar
```

### Installation
1. Copy JAR to `mods/` folder
2. Start Minecraft with NeoForge 1.21.1
3. Verify mod loaded in mods list

---

## 📚 See Also

- **[GETTING_STARTED.md](GETTING_STARTED.md)** - Quick start guide
- **[COMMANDS.md](COMMANDS.md)** - Command reference
- **[FEATURES.md](FEATURES.md)** - Feature documentation
- **[CONFIGURATION.md](CONFIGURATION.md)** - Configuration guide

---

**Development Guide Complete!** Happy coding! 🛠️
