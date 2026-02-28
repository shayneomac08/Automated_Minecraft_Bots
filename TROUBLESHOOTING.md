# 🔧 Troubleshooting Guide

## Common Issues and Solutions

### ❌ Items Disappearing from Bot Inventory

**Symptoms:**
- Items placed in bot inventory via GUI disappear instantly
- Picked up items vanish from inventory
- Materials convert automatically without player control

**Root Cause:**
The `proactiveCrafting()` system was running automatic crafting loops every 20 ticks.

**Solution:** ✅ **FULLY DISABLED** Automatic Crafting

All automatic crafting loops have been **completely removed**. Bots now craft only when the LLM decides they need something.

**New Architecture:**
- ❌ No automatic log → plank conversion
- ❌ No automatic plank → stick conversion
- ❌ No automatic tool crafting
- ✅ LLM-driven crafting only
- ✅ Player-like behavior

**Test the Fix:**
1. Spawn a bot: `/amb spawn testbot`
2. Give items: `/amb give testbot oak_log 64`
3. Wait 60 seconds
4. Check inventory: `/amb inventory testbot`
5. Logs should still be logs! ✅

**Note:** The `/amb autocraft` command still exists but should remain **OFF** for LLM-driven behavior.

---

### ❌ Item Pickup Shows "0 Air"

**Symptoms:**
- Bot picks up items but chat shows "0 Air"
- Actual item name not displayed

**Solution:** ✅ Fixed in v1.0.14
- Item data is now saved before adding to inventory
- Correct item names display in chat

---

### ❌ Stone Pickaxe Crafted Without Crafting Table

**Symptoms:**
- Bot crafts stone tools without a placed crafting table nearby

**Solution:** ✅ Fixed in v1.0.13
- Added proximity check for crafting table (within 5 blocks)
- Stone tools now require a placed crafting table in the world

---

### ❌ Multiple Crafting Systems Conflicting

**Symptoms:**
- Items being crafted multiple times
- Inventory behaving unpredictably
- Runaway stick crafting loops

**Solution:** ✅ **ALL REMOVED**
- Disabled ALL automatic crafting systems:
  - `autoCraftTools()` - ❌ disabled
  - `autoCraft()` - ❌ disabled (only used when LLM calls it)
  - `processCraftingQueue()` - ❌ disabled
  - `proactiveCrafting()` - ❌ **FULLY DISABLED**
- Bots now craft only via LLM decision-making
- See [LLM_DRIVEN_ARCHITECTURE.md](LLM_DRIVEN_ARCHITECTURE.md) for details

---

### ❌ Bot Not Picking Up Items

**Checklist:**
1. Is the bot within 2 blocks of the item?
2. Is the bot's inventory full? (36 slots max)
3. Is the item on the ground for at least 10 ticks?

**Solution:**
- Bots auto-pickup items within 2 blocks (like players)
- Clear inventory space: `/amb clear <name>` (WARNING: deletes all items)
- Check inventory: `/amb inventory <name>` or `/amb gui <name>`

---

### ❌ Bot Brain Not Working

**Symptoms:**
- Bot stands still and doesn't do anything
- No autonomous behavior

**Checklist:**
1. Is brain enabled? `/amb brain <name> on`
2. Does bot have starting materials? Give planks/food: `/amb give <name> oak_planks 8`
3. Is bot stuck? Try teleporting: `/amb teleport <name> ~ ~1 ~`

**Solution:**
```bash
# Enable brain
/amb brain <name> on

# Or enable all bots at once
/amb brainall on

# Give starting materials
/amb give <name> oak_planks 8
/amb give <name> cooked_beef 16
```

---

### ❌ Bot Can't Find Resources

**Symptoms:**
- Bot wanders but doesn't gather wood/stone
- "No trees found" or "No stone found" messages

**Solution:**
1. **Wood:** Bot searches 48 blocks horizontal, -12 to +60 vertical
   - Ensure trees are within range
   - Bot needs line of sight to logs

2. **Stone:** Bot searches for cobblestone/stone blocks
   - Place some cobblestone nearby for testing
   - Natural stone generates underground

3. **Manual Task:** Override with specific task
   ```bash
   /amb task <name> gather_wood
   /amb task <name> mine_stone
   ```

---

### ❌ Population Cap Reached

**Symptoms:**
- Can't spawn new bots
- Breeding fails with "Population cap reached"

**Solution:**
- Maximum population: **30 bots**
- Check current count: `/amb population`
- Remove unused bots: `/amb remove <name>`

---

### ❌ Breeding Not Working

**Requirements:**
- Both bots must exist
- Bots must be opposite genders (male/female)
- Population must be below 30
- 50% random chance of success

**Check Gender:**
```bash
/amb info <bot1>
/amb info <bot2>
```

**Solution:**
- Ensure opposite genders (male + female)
- Try multiple times (50% success rate)
- Check population: `/amb population`

---

## Build Issues

### Gradle Build Fails

**Solution:**
```bash
# Clean and rebuild
.\gradlew.bat clean build --console=plain

# If still fails, check Java version
java -version  # Should be Java 21+
```

---

### Game Won't Start

**Checklist:**
1. Minecraft version: **1.21.1**
2. NeoForge version: **1.21.1**
3. Java version: **21+**
4. Mod in `mods/` folder

**Solution:**
```bash
# Run from project directory
.\gradlew.bat runClient --console=plain
```

---

## Debug Commands

```bash
# Check bot status
/amb status <name>
/amb info <name>
/amb inventory <name>

# Check population
/amb population

# List all bots
/amb list

# Open bot GUI
/amb gui <name>

# Check auto-craft status
/amb autocraft <name> status
```

---

## Getting Help

1. Check this troubleshooting guide
2. Review [COMMANDS.md](COMMANDS.md) for command syntax
3. Check [FEATURES.md](FEATURES.md) for feature details
4. Review game logs: `run/logs/latest.log`

---

## Quick Fixes Summary

| Issue | Command/Solution |
|-------|-----------------|
| Items disappearing | ✅ **FIXED** - Auto-crafting fully disabled |
| Bot not working | `/amb brain <name> on` |
| Enable all bots | `/amb brainall on` |
| Check inventory | `/amb gui <name>` |
| Give items | `/amb give <name> <item> <amount>` |
| Check status | `/amb status <name>` |
| Population check | `/amb population` |
| Chest access | Bots can open/use chests automatically |

---

## New Architecture

Bots are now **fully LLM-driven**:
- ✅ No automatic crafting loops
- ✅ Bots craft only when LLM decides
- ✅ Full chest access enabled
- ✅ Player-like behavior

See **[LLM_DRIVEN_ARCHITECTURE.md](LLM_DRIVEN_ARCHITECTURE.md)** for complete details.

---

**Most issues are resolved by enabling bot brain and letting the LLM control behavior!** 🎯
