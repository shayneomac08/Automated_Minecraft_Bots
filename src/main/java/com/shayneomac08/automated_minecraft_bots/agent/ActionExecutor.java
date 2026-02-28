package com.shayneomac08.automated_minecraft_bots.agent;

import com.shayneomac08.automated_minecraft_bots.bot.BotBrain;
import com.shayneomac08.automated_minecraft_bots.bot.BotPair;
import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class ActionExecutor {
    private ActionExecutor() {}

    public static void apply(MinecraftServer server, String botKeyName, BotBrain.State st, BotPair pair, ActionPlan plan) {
        if (plan == null || plan.actions() == null || plan.actions().isEmpty()) return;

        // Safe: execute only the first action
        ActionPlan.Action a = plan.actions().getFirst();
        if (a == null || a.type() == null) return;

        LivingEntity body = pair.body();
        FakePlayer hands = pair.hands();
        if (body == null || body.isRemoved()) return;

        String type = a.type().trim().toLowerCase();

        switch (type) {

            case "set_goal" -> {
                String g = (a.goal() == null) ? "" : a.goal().trim().toLowerCase();
                int minutes = (a.minutes() == null) ? 5 : (int) Math.max(1, Math.min(15, a.minutes()));
                st.goalUntilTick = server.getTickCount() + minutes * 60 * 20;
                st.mode = BotBrain.Mode.GOAL;

                st.lastThought = "goal=" + g + " for " + minutes + "m";
                st.lastError = "";

                // FakePlayer task system - use setTask() instead of Goals
                if (body instanceof AmbNpcEntity ambBot) {
                    // Map goal names to task names
                    String task = switch (g) {
                        case "gather_wood", "mine_wood", "chop_wood" -> "gather_wood";
                        case "mine_stone", "gather_stone" -> "mine_stone";
                        case "build_shelter", "shelter", "build_house", "build" -> "build_shelter";
                        case "craft", "crafting" -> "craft";
                        case "place_crafting_table", "place_table" -> "place_crafting_table";
                        case "idle", "stop", "wait" -> "idle";
                        default -> g; // Pass through unknown tasks
                    };

                    ambBot.setTask(task);
                    System.out.println("[AMB] " + botKeyName + " task set to: " + task + " for " + minutes + " minutes");

                    st.goal = BotBrain.GoalType.NONE; // FakePlayer doesn't use GoalType enum
                } else {
                    // Fallback for non-AmbNpcEntity bodies
                    st.goal = BotBrain.GoalType.NONE;
                    System.out.println("[AMB] " + botKeyName + " goal set (non-FakePlayer body)");
                }
            }


            case "say" -> {
                String text = a.text() == null ? "" : a.text();
                st.lastThought = text;
                st.lastError = "";
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[AMB] " + botKeyName + ": " + text),
                        false
                );
                st.mode = BotBrain.Mode.FOLLOW_NEAREST;
                clearGoal(st);
            }

            case "idle", "stop" -> {
                st.mode = BotBrain.Mode.IDLE;
                if (body instanceof AmbNpcEntity ambBot) {
                    ambBot.stopMovement();
                    ambBot.setTask("idle");
                }
                body.setDeltaMovement(Vec3.ZERO);
                st.lastError = "";
                clearGoal(st);
            }

            case "follow_nearest" -> {
                st.mode = BotBrain.Mode.FOLLOW_NEAREST;
                st.lastError = "";
                clearGoal(st);
            }

            case "wander" -> {
                st.mode = BotBrain.Mode.IDLE;
                clearGoal(st);

                if (body instanceof AmbNpcEntity ambBot) {
                    double range = 12.0;
                    double rx = body.getX() + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * range;
                    double rz = body.getZ() + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * range;
                    double ry = body.getY();
                    float speed = (a.speed() != null) ? (float) clamp(a.speed(), 0.1, 0.3) : 0.2f;

                    ambBot.setMoveTarget(new Vec3(rx, ry, rz), speed);
                    st.lastError = "";
                } else {
                    st.lastError = "Body cannot wander.";
                }
            }

            // Relative move: x=dx, z=dz, optional y=dy
            case "move_to" -> {
                Double dx = a.x();
                Double dy = a.y();
                Double dz = a.z();

                if (dx == null || dz == null) {
                    st.lastError = "move_to missing x/z (dx/dz)";
                    return;
                }

                double max = 24.0;
                double rdx = clamp(dx, -max, max);
                double rdz = clamp(dz, -max, max);

                double rdy = 0.0;
                if (dy != null) rdy = clamp(dy, -4.0, 4.0);

                double tx = body.getX() + rdx;
                double tz = body.getZ() + rdz;
                double ty = body.getY() + rdy;

                if (body instanceof AmbNpcEntity ambBot) {
                    float speed = (a.speed() != null) ? (float) clamp(a.speed(), 0.1, 0.3) : 0.2f;
                    ambBot.setMoveTarget(new Vec3(tx, ty, tz), speed);
                    st.mode = BotBrain.Mode.IDLE;
                    st.lastError = "";
                    clearGoal(st);
                } else {
                    st.lastError = "Body cannot move_to.";
                }
            }

            case "explore" -> {
                if (body instanceof AmbNpcEntity ambBot) {
                    ServerLevel lvl = (ServerLevel) body.level();
                    int now = server.getTickCount();
                    int secs = (a.seconds() != null) ? Math.max(2, Math.min(30, a.seconds())) : 8;
                    float speed = (a.speed() != null) ? (float) clamp(a.speed(), 0.1, 0.3) : 0.2f;

                    boolean needNewGoal = (st.goalX == null || st.goalZ == null || now >= st.goalUntilTick);
                    if (!needNewGoal) {
                        BlockPos pos = BlockPos.containing(st.goalX, st.goalY != null ? st.goalY : body.getY(), st.goalZ);
                        if (!isSafeWalkTarget(lvl, pos)) needNewGoal = true;

                    }

                    if (needNewGoal) {
                        Vec3 target = pickSafeExploreTarget(lvl, body.position());
                        if (target == null) {
                            st.lastError = "No safe explore target found.";
                            st.goalUntilTick = now + 40;
                            return;
                        }
                        st.goalX = target.x;
                        st.goalY = target.y;
                        st.goalZ = target.z;
                    }

                    st.goalUntilTick = now + (secs * 20);

                    ambBot.setMoveTarget(new Vec3(st.goalX, st.goalY != null ? st.goalY : body.getY(), st.goalZ), speed);

                    st.mode = BotBrain.Mode.IDLE;
                    st.lastError = "";
                    st.lastThought = "exploring for " + secs + "s";
                } else {
                    st.lastError = "Body cannot explore.";
                }
            }

            default -> {
                // ignore unknown actions safely
            }
        }

        // FakePlayer: hands and body are the same entity now, no need to sync
        // Only sync if they're different (legacy support)
        if (hands != null && !hands.isRemoved() && hands != body) {
            hands.teleportTo(body.getX(), body.getY(), body.getZ());
            hands.setYRot(body.getYRot());
            hands.setYHeadRot(body.getYHeadRot());
        }
    }

    private static void clearGoal(BotBrain.State st) {
        st.goalUntilTick = 0;
        st.goalX = null;
        st.goalY = null;
        st.goalZ = null;
    }



    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- Explore safety helpers ----

    private static Vec3 pickSafeExploreTarget(ServerLevel level, Vec3 origin) {
        for (int i = 0; i < 20; i++) {
            double ang = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
            double dist = ThreadLocalRandom.current().nextDouble(28.0 * 0.45, 28.0);

            double tx = origin.x + Math.cos(ang) * dist;
            double tz = origin.z + Math.sin(ang) * dist;

            int ix = (int) Math.floor(tx);
            int iz = (int) Math.floor(tz);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);

            BlockPos feet = new BlockPos(ix, y, iz);

            // We want SAFE targets. If not safe, try again.
            if (!isSafeWalkTarget(level, feet)) continue;

            return new Vec3(ix + 0.5, y, iz + 0.5);
        }
        return null;
    }

    private static boolean isSafeWalkTarget(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockPos head = feet.above();

        var belowState = level.getBlockState(below);
        var feetState = level.getBlockState(feet);
        var headState = level.getBlockState(head);

        // Must have solid ground
        if (!belowState.canOcclude()) return false;

        // Must have space for body
        if (feetState.canOcclude() || headState.canOcclude()) return false;

        // Avoid fluids at feet
        var fluid = level.getFluidState(feet);
        return fluid.isEmpty();
    }

}
