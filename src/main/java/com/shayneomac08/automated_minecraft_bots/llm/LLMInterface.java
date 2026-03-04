package com.shayneomac08.automated_minecraft_bots.llm;

import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM INTERFACE EXPANSION
 * Enhanced state reporting and fine-grained action parsing for bot control
 */
public class LLMInterface {

    /**
     * Enhanced bot state for LLM context
     */
    public static class EnhancedBotState {
        public BlockPos position;
        public BlockPos goal;
        public String currentTask;
        public int pathWaypoints;
        public int stuckLevel;
        public boolean isInterior;
        public int verticalDistance; // dy to goal
        public List<String> mineableBlocks;
        public int health;
        public int hunger;
        public String inventorySummary;

        public EnhancedBotState(AmbNpcEntity bot) {
            this.position = bot.blockPosition();
            this.goal = BlockPos.ZERO; // Would be set from bot's currentGoal
            this.currentTask = bot.getCurrentTask();
            this.pathWaypoints = 0; // Would be set from bot's path size
            this.stuckLevel = 0; // Would be set from bot's stuck state
            this.isInterior = !bot.level().canSeeSky(bot.blockPosition().above(5));
            this.verticalDistance = 0; // Would be calculated
            this.mineableBlocks = new ArrayList<>();
            this.health = (int) bot.getHealth();
            this.hunger = bot.getFoodData().getFoodLevel();
            this.inventorySummary = getInventorySummary(bot);
        }

        private String getInventorySummary(AmbNpcEntity bot) {
            // Count key items
            StringBuilder summary = new StringBuilder();
            summary.append("wood:").append(countLogs(bot));
            summary.append(",stone:").append(countStone(bot));
            summary.append(",food:").append(countFood(bot));
            return summary.toString();
        }

        private int countLogs(AmbNpcEntity bot) {
            int count = 0;
            for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
                var stack = bot.getInventory().getItem(i);
                if (stack.getItem().toString().contains("log")) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        private int countStone(AmbNpcEntity bot) {
            int count = 0;
            for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
                var stack = bot.getInventory().getItem(i);
                if (stack.getItem().toString().contains("stone") ||
                    stack.getItem().toString().contains("cobblestone")) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        private int countFood(AmbNpcEntity bot) {
            int count = 0;
            for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
                var stack = bot.getInventory().getItem(i);
                String itemName = stack.getItem().toString().toLowerCase();
                // Check if item is food (common food items)
                if (itemName.contains("beef") || itemName.contains("pork") ||
                    itemName.contains("chicken") || itemName.contains("mutton") ||
                    itemName.contains("bread") || itemName.contains("apple") ||
                    itemName.contains("carrot") || itemName.contains("potato") ||
                    itemName.contains("fish") || itemName.contains("cod") ||
                    itemName.contains("salmon")) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        public String toPrompt() {
            return String.format(
                "Bot at %s, goal:%s, task:%s, path_wps:%d, stuck:%d, interior:%b, dy:%d, mineables:%s, health:%d, hunger:%d, inv:%s",
                position, goal, currentTask, pathWaypoints, stuckLevel, isInterior,
                verticalDistance, mineableBlocks, health, hunger, inventorySummary
            );
        }
    }

    /**
     * Enhanced action types for fine-grained control
     */
    public enum ActionType {
        FINE_MOVE,      // fine_move{dx,dy,dz}
        PILLAR_UP,      // pillar_up{h}
        DIG_OUT,        // dig_out{dir}
        JUMP_SEQ,       // jump_seq
        AVOID,          // avoid{obs}
        REPLAN,         // Task failed, replan
        MINE_BLOCK,     // mine_block{x,y,z}
        PLACE_BLOCK,    // place_block{x,y,z,type}
        CRAFT_ITEM      // craft_item{item}
    }

    /**
     * Parsed action from LLM response
     */
    public static class ParsedAction {
        public ActionType type;
        public int dx, dy, dz;
        public int height;
        public String direction;
        public String obstacle;
        public String item;
        public BlockPos targetPos;

        public ParsedAction(ActionType type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return switch (type) {
                case FINE_MOVE -> String.format("fine_move{%d,%d,%d}", dx, dy, dz);
                case PILLAR_UP -> String.format("pillar_up{%d}", height);
                case DIG_OUT -> String.format("dig_out{%s}", direction);
                case JUMP_SEQ -> "jump_seq";
                case AVOID -> String.format("avoid{%s}", obstacle);
                case REPLAN -> "replan";
                case MINE_BLOCK -> String.format("mine_block{%s}", targetPos);
                case PLACE_BLOCK -> String.format("place_block{%s,%s}", targetPos, item);
                case CRAFT_ITEM -> String.format("craft_item{%s}", item);
            };
        }
    }

    /**
     * Request LLM assistance for stuck/complex situations
     */
    public static String requestLLMAssistance(AmbNpcEntity bot, String reason) {
        EnhancedBotState state = new EnhancedBotState(bot);

        String prompt = String.format(
            "Bot stuck: %s. State: %s. Suggest action (fine_move/pillar_up/dig_out/jump_seq/avoid/replan):",
            reason, state.toPrompt()
        );

        System.out.println("[AMB-LLM] Request: " + prompt);

        // This would call the actual LLM client
        // For now, return a placeholder response
        return "replan";
    }

    /**
     * Parse LLM response into actionable commands
     */
    public static List<ParsedAction> parseResponse(String response) {
        List<ParsedAction> actions = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            return actions;
        }

        // Parse different action formats
        if (response.contains("fine_move")) {
            ParsedAction action = parseFineMove(response);
            if (action != null) actions.add(action);
        }

        if (response.contains("pillar_up")) {
            ParsedAction action = parsePillarUp(response);
            if (action != null) actions.add(action);
        }

        if (response.contains("dig_out")) {
            ParsedAction action = parseDigOut(response);
            if (action != null) actions.add(action);
        }

        if (response.contains("jump_seq")) {
            actions.add(new ParsedAction(ActionType.JUMP_SEQ));
        }

        if (response.contains("avoid")) {
            ParsedAction action = parseAvoid(response);
            if (action != null) actions.add(action);
        }

        if (response.contains("replan")) {
            actions.add(new ParsedAction(ActionType.REPLAN));
        }

        return actions;
    }

    /**
     * Parse fine_move{dx,dy,dz} command
     */
    private static ParsedAction parseFineMove(String response) {
        try {
            int start = response.indexOf("fine_move{") + 10;
            int end = response.indexOf("}", start);
            String params = response.substring(start, end);
            String[] parts = params.split(",");

            ParsedAction action = new ParsedAction(ActionType.FINE_MOVE);
            action.dx = Integer.parseInt(parts[0].trim());
            action.dy = Integer.parseInt(parts[1].trim());
            action.dz = Integer.parseInt(parts[2].trim());
            return action;
        } catch (Exception e) {
            System.out.println("[AMB-LLM] Failed to parse fine_move: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse pillar_up{h} command
     */
    private static ParsedAction parsePillarUp(String response) {
        try {
            int start = response.indexOf("pillar_up{") + 10;
            int end = response.indexOf("}", start);
            String param = response.substring(start, end);

            ParsedAction action = new ParsedAction(ActionType.PILLAR_UP);
            action.height = Integer.parseInt(param.trim());
            return action;
        } catch (Exception e) {
            System.out.println("[AMB-LLM] Failed to parse pillar_up: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse dig_out{dir} command
     */
    private static ParsedAction parseDigOut(String response) {
        try {
            int start = response.indexOf("dig_out{") + 8;
            int end = response.indexOf("}", start);
            String param = response.substring(start, end);

            ParsedAction action = new ParsedAction(ActionType.DIG_OUT);
            action.direction = param.trim();
            return action;
        } catch (Exception e) {
            System.out.println("[AMB-LLM] Failed to parse dig_out: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse avoid{obs} command
     */
    private static ParsedAction parseAvoid(String response) {
        try {
            int start = response.indexOf("avoid{") + 6;
            int end = response.indexOf("}", start);
            String param = response.substring(start, end);

            ParsedAction action = new ParsedAction(ActionType.AVOID);
            action.obstacle = param.trim();
            return action;
        } catch (Exception e) {
            System.out.println("[AMB-LLM] Failed to parse avoid: " + e.getMessage());
            return null;
        }
    }

    /**
     * Execute parsed actions on the bot
     */
    public static void executeActions(AmbNpcEntity bot, List<ParsedAction> actions) {
        for (ParsedAction action : actions) {
            System.out.println("[AMB-LLM] Executing: " + action);

            switch (action.type) {
                case FINE_MOVE:
                    executeFineMove(bot, action.dx, action.dy, action.dz);
                    break;
                case PILLAR_UP:
                    executePillarUp(bot, action.height);
                    break;
                case DIG_OUT:
                    executeDigOut(bot, action.direction);
                    break;
                case JUMP_SEQ:
                    executeJumpSeq(bot);
                    break;
                case AVOID:
                    executeAvoid(bot, action.obstacle);
                    break;
                case REPLAN:
                    executeReplan(bot);
                    break;
                default:
                    System.out.println("[AMB-LLM] Unknown action type: " + action.type);
            }
        }
    }

    private static void executeFineMove(AmbNpcEntity bot, int dx, int dy, int dz) {
        BlockPos target = bot.blockPosition().offset(dx, dy, dz);
        System.out.println("[AMB-LLM] Fine move to " + target);
        // Would set bot's goal to target
    }

    private static void executePillarUp(AmbNpcEntity bot, int height) {
        System.out.println("[AMB-LLM] Building pillar of height " + height);
        com.shayneomac08.automated_minecraft_bots.movement.VerticalNavigation.buildPillar(bot, height);
    }

    private static void executeDigOut(AmbNpcEntity bot, String direction) {
        System.out.println("[AMB-LLM] Digging out in direction " + direction);
        // Would break blocks in the specified direction
    }

    private static void executeJumpSeq(AmbNpcEntity bot) {
        System.out.println("[AMB-LLM] Executing jump sequence");
        if (bot.onGround()) {
            bot.setDeltaMovement(bot.getDeltaMovement().x, 0.42, bot.getDeltaMovement().z);
        }
    }

    private static void executeAvoid(AmbNpcEntity bot, String obstacle) {
        System.out.println("[AMB-LLM] Avoiding obstacle: " + obstacle);
        // Would implement obstacle avoidance
    }

    private static void executeReplan(AmbNpcEntity bot) {
        System.out.println("[AMB-LLM] Replanning task");
        // Would clear current goal and find new task
    }
}
