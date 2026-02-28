package com.shayneomac08.automated_minecraft_bots.bot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class BotSurvivalNeeds {
    
    public static class Needs {
        public float hunger = 20.0f;
        public float health = 20.0f;
        public boolean needsShelter = false;
        public boolean needsFood = false;
        public boolean needsHealing = false;
        public boolean needsWood = false;
        public boolean needsStone = false;
        public boolean needsTools = false;
        public boolean needsBed = false;
        public int woodCount = 0;
        public int stoneCount = 0;
        public int foodCount = 0;
        public boolean hasCraftingTable = false;
        public boolean hasFurnace = false;
        public boolean hasBed = false;
        public boolean hasShelter = false;
        public java.util.Set<String> knownRecipes = new java.util.HashSet<>();
        public long lastAteFood = 0;
        public long lastSlept = 0;
        public long lastCrafted = 0;
    }
    
    private BotSurvivalNeeds() {}
    
    public static void updateNeeds(ServerLevel level, FakePlayer hands, LivingEntity body, Needs needs) {
        if (hands == null || body == null) return;
        FoodData foodData = hands.getFoodData();
        needs.hunger = foodData.getFoodLevel();
        needs.health = body.getHealth();
        long dayTime = level.getDayTime() % 24000L;
        boolean isNight = dayTime >= 13000L && dayTime < 23000L;
        boolean isRaining = level.isRaining();
        needs.needsShelter = isNight || isRaining;
        needs.needsFood = needs.hunger < 6.0f;
        needs.needsHealing = needs.health < 10.0f;
        var inventory = hands.getInventory();
        needs.woodCount = countItemsWithTag(inventory, "minecraft:logs");
        needs.stoneCount = countItemsWithTag(inventory, "minecraft:stone") + countItemsWithTag(inventory, "minecraft:cobblestone");
        needs.foodCount = countEdibleItems(inventory);
        needs.needsWood = needs.woodCount < 8;
        needs.needsStone = needs.stoneCount < 16;
        needs.needsTools = !hasAnyTool(inventory);
        needs.hasCraftingTable = hasItem(inventory, "minecraft:crafting_table");
        needs.hasFurnace = hasItem(inventory, "minecraft:furnace");
        needs.hasBed = hasItem(inventory, "minecraft:bed");
    }
    
    public static String getHighestPriorityNeed(Needs needs) {
        if (needs.health < 5.0f) return "critical_health";
        if (needs.hunger < 3.0f) return "critical_hunger";  // MUST hunt/gather food immediately
        if (needs.needsShelter && !needs.hasShelter) return "shelter";
        if (needs.needsHealing) return "healing";
        if (needs.needsFood && needs.foodCount == 0) return "hunt_animals";  // Hunt when hungry and no food
        if (needs.needsFood) return "food";  // Eat existing food
        if (!needs.hasCraftingTable && needs.woodCount >= 4) return "craft_table";
        if (needs.needsWood) return "gather_wood";
        if (needs.needsStone) return "mine_stone";
        if (needs.needsTools) return "craft_tools";
        if (!needs.hasBed && needs.woodCount >= 3) return "craft_bed";
        if (!needs.hasFurnace && needs.stoneCount >= 8) return "craft_furnace";
        return "explore";
    }
    
    public static String getNeedsDescription(Needs needs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Survival Status:\n");
        sb.append("- Health: ").append(String.format("%.1f", needs.health)).append("/20\n");
        sb.append("- Hunger: ").append(String.format("%.1f", needs.hunger)).append("/20\n");
        sb.append("- Wood: ").append(needs.woodCount).append("\n");
        sb.append("- Stone: ").append(needs.stoneCount).append("\n");
        sb.append("- Food: ").append(needs.foodCount).append("\n");
        sb.append("\nNeeds:\n");
        if (needs.needsShelter) sb.append("- URGENT: Need shelter (night/rain)\n");
        if (needs.needsFood) sb.append("- Need food\n");
        if (needs.needsHealing) sb.append("- Need healing\n");
        if (needs.needsWood) sb.append("- Need wood\n");
        if (needs.needsStone) sb.append("- Need stone\n");
        if (needs.needsTools) sb.append("- Need tools\n");
        sb.append("\nProgression:\n");
        sb.append("- Crafting Table: ").append(needs.hasCraftingTable ? "Y" : "N").append("\n");
        sb.append("- Furnace: ").append(needs.hasFurnace ? "Y" : "N").append("\n");
        sb.append("- Bed: ").append(needs.hasBed ? "Y" : "N").append("\n");
        sb.append("- Shelter: ").append(needs.hasShelter ? "Y" : "N").append("\n");
        return sb.toString();
    }
    
    private static int countItemsWithTag(net.minecraft.world.entity.player.Inventory inv, String tag) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.Identifier.parse(tag)))) {
                count += stack.getCount();
            }
        }
        return count;
    }
    
    private static int countEdibleItems(net.minecraft.world.entity.player.Inventory inv) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.get(DataComponents.FOOD) != null) {
                count += stack.getCount();
            }
        }
        return count;
    }
    
    private static boolean hasItem(net.minecraft.world.entity.player.Inventory inv, String itemId) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(itemId);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                var itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (itemKey.equals(id)) return true;
            }
        }
        return false;
    }
    
    private static boolean hasAnyTool(net.minecraft.world.entity.player.Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                // In 1.21.5+, tool/weapon classes were removed and replaced with data components
                // Check if the item has TOOL (for pickaxe, axe, shovel, hoe) or WEAPON (for sword) components
                if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)) {
                    return true;
                }
            }
        }
        return false;
    }
}
