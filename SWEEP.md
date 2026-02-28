# SWEEP.md - Project Guidelines & Commands

## 📋 Documentation Policy

### Core Documentation Files (DO NOT DELETE)
1. **README.md** - Project overview, quick start, feature list
2. **GETTING_STARTED.md** - Detailed setup and basic usage guide
3. **COMMANDS.md** - Complete command reference
4. **FEATURES.md** - Detailed feature documentation
5. **TROUBLESHOOTING.md** - Common issues and solutions
6. **CHANGELOG.md** - Version history and changes
7. **LLM_DRIVEN_ARCHITECTURE.md** - LLM integration architecture
8. **CONFIGURATION.md** - Configuration options
9. **DEVELOPMENT.md** - Development guide
10. **SWEEP.md** - This file (project guidelines)

### Documentation Rules

**✅ DO:**
- Update CHANGELOG.md for all version changes and bug fixes
- Update existing documentation files when features change
- Keep documentation in sync with code

**❌ DON'T:**
- Create new markdown files for bug fixes (use CHANGELOG.md)
- Create version-specific fix files (e.g., VERSION_1.0.X_FIX.md)
- Create duplicate quick start guides
- Create "COMPLETE_FIX_SUMMARY.md" or similar temporary files
- Leave outdated documentation files in the repository

**When fixing bugs:**
1. Fix the code
2. Add entry to CHANGELOG.md
3. Update relevant documentation (FEATURES.md, TROUBLESHOOTING.md, etc.)
4. DO NOT create a new markdown file

---

## 🛠️ Common Commands

### Build & Run
```bash
# Clean build
.\gradlew.bat clean build --console=plain

# Build only
.\gradlew.bat build --console=plain

# Run client (test in-game)
.\gradlew.bat runClient --console=plain

# Run server
.\gradlew.bat runServer --console=plain
```

### Testing
```bash
# Run all tests
.\gradlew.bat test --console=plain

# Run specific test
.\gradlew.bat test --tests "TestClassName" --console=plain
```

### Code Quality
```bash
# Check for errors (if linter is configured)
.\gradlew.bat check --console=plain
```

---

## 🎯 Code Style Preferences

### Naming Conventions
- **Classes:** PascalCase (e.g., `AmbNpcEntity`, `BotBrain`)
- **Methods:** camelCase (e.g., `giveItem()`, `addItemToActualInventory()`)
- **Variables:** camelCase (e.g., `targetBlock`, `remainingCount`)
- **Constants:** UPPER_SNAKE_CASE (e.g., `MAX_POPULATION`, `PICKUP_RADIUS`)

### Inventory Operations
**CRITICAL:** Always use custom slot management, NEVER use `inventory.add()`

```java
// ✅ CORRECT - Custom slot management
private boolean addItemToActualInventory(ItemStack stack) {
    // 1. Try to merge with existing stacks
    for (int i = 0; i < inv.getContainerSize(); i++) {
        ItemStack slotStack = inv.getItem(i);
        if (!slotStack.isEmpty() && ItemStack.isSameItemSameComponents(slotStack, stack)) {
            slotStack.grow(canAdd);
            inv.setItem(i, slotStack); // CRITICAL: Always call setItem()!
        }
    }
    // 2. Find empty slot if items remain
    // 3. Always call setItem() to persist changes
}

// ❌ WRONG - Never use this
private boolean addItemToActualInventory(ItemStack stack) {
    return inv.add(stack); // BROKEN - only works with existing stacks
}
```

### Logging
- Use `[AMB]` prefix for all bot-related logs
- Use `[AMBTaskGoal]` for task-specific logs
- Keep logs concise and informative
- Avoid log spam (throttle frequent messages)

---

## 📦 Project Structure

```
Automated_Minecraft_Bots/
├── src/main/java/com/shayneomac08/automated_minecraft_bots/
│   ├── entity/
│   │   └── AmbNpcEntity.java          # Main bot entity (inventory, AI, movement)
│   ├── bot/
│   │   ├── BotBrain.java              # LLM decision-making
│   │   └── BotRegistry.java           # Bot registration and management
│   ├── command/
│   │   └── AmbCommands.java           # All /amb commands
│   ├── inventory/
│   │   └── BotInventoryMenu.java     # GUI inventory menu
│   ├── goal/
│   │   ├── AMBTaskGoal.java           # Main task execution
│   │   ├── SmeltingGoal.java          # Smelting operations
│   │   └── SharedResourceGoal.java    # Chest access and sharing
│   └── event/
│       └── MobTargetingHandler.java   # Mob aggro events
├── docs/                               # Documentation files
└── build.gradle                        # Build configuration
```

---

## 🐛 Known Issues & Limitations

### Current Limitations
- Spawn point hardcoded to (0, 128, 0) - no bed spawn support yet
- Food system uses health as proxy (no direct food level access)
- Villager trading simplified (NeoForge 1.21.1 API limitations)

### Fixed Issues (See CHANGELOG.md)
- ✅ v1.0.20: Inventory system unified (items no longer disappear)
- ✅ v1.0.19: Give command fixed
- ✅ v1.0.14: GUI and item pickup fixes
- ✅ v1.0.13: Crafting table requirement added
- ✅ v1.0.10: Mob aggro, navigation, inventory persistence

---

## 🔧 Development Workflow

### Making Changes
1. Create a feature branch (optional)
2. Make code changes
3. Test in-game with `.\gradlew.bat runClient`
4. Update CHANGELOG.md with changes
5. Update relevant documentation files
6. Build and verify: `.\gradlew.bat build`
7. Commit changes

### Adding New Features
1. Implement feature in code
2. Add entry to CHANGELOG.md
3. Update FEATURES.md with feature details
4. Update COMMANDS.md if new commands added
5. Update README.md if major feature
6. Test thoroughly

### Fixing Bugs
1. Identify and fix bug in code
2. Add entry to CHANGELOG.md under current version
3. Update TROUBLESHOOTING.md if user-facing issue
4. Test fix in-game
5. **DO NOT** create VERSION_X.X.X_FIX.md files

---

## 📝 Version Numbering

Format: `MAJOR.MINOR.PATCH`

- **MAJOR:** Breaking changes, major rewrites
- **MINOR:** New features, significant improvements
- **PATCH:** Bug fixes, small improvements

Current: **1.0.20**

---

## 🎯 Future Improvements

### Planned Features
- Custom spawn points per bot
- Death messages broadcast to chat
- Advanced building (multiple structure types)
- Team coordination (multiple bots working together)
- Resource sharing via chests
- Chest labeling with signs
- Trading system between bots

### Community Ideas
- Bots can create shops with chests
- Bots can hide treasure chests
- Bots can share chest locations with trusted friends
- Bots can lock chests with passwords

---

## 📚 Additional Resources

### Minecraft Modding
- [NeoForge Documentation](https://docs.neoforged.net/)
- [Minecraft Wiki](https://minecraft.wiki/)

### LLM Integration
- [OpenAI API](https://platform.openai.com/docs)
- [Anthropic API](https://docs.anthropic.com/)
- [Ollama](https://ollama.ai/)

---

**Last Updated:** 2026-02-26
**Maintainer:** shayneomac08
