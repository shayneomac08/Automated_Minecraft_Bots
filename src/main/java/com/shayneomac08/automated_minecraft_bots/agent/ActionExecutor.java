package com.shayneomac08.automated_minecraft_bots.agent;

import com.shayneomac08.automated_minecraft_bots.bot.BotBrain;
import com.shayneomac08.automated_minecraft_bots.bot.BotPair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class ActionExecutor {
    private ActionExecutor() {}

    public static void apply(MinecraftServer server, String botKeyName, BotBrain.State st, BotPair pair, ActionPlan plan) {
        if (plan == null || plan.actions() == null || plan.actions().isEmpty()) return;

        // Safe: execute only the first action
        ActionPlan.Action a = plan.actions().getFirst();
        if (a == null || a.type() == null) return;

        LivingEntity body = pair.body();
        var hands = pair.hands();
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
                if (body instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
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
                        net.minecraft.network.chat.Component.literal("[AMB] " + botKeyName + ": " + text),
                        false
                );
                st.mode = BotBrain.Mode.FOLLOW_NEAREST;
                clearGoal(st);
            }

            case "idle", "stop" -> {
                st.mode = BotBrain.Mode.IDLE;
                if (body instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
                    ambBot.stopMovement();
                    ambBot.setTask("idle");
                } else if (body instanceof Mob mob) {
                    mob.getNavigation().stop();
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

                if (body instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
                    double range = 12.0;
                    double rx = body.getX() + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2 - 1) * range;
                    double rz = body.getZ() + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2 - 1) * range;
                    double ry = body.getY();
                    float speed = (a.speed() != null) ? (float) clamp(a.speed(), 0.1, 0.3) : 0.2f;

                    ambBot.setMoveTarget(new Vec3(rx, ry, rz), speed);
                    st.lastError = "";
                } else if (body instanceof Mob mob) {
                    double range = 12.0;
                    double rx = body.getX() + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2 - 1) * range;
                    double rz = body.getZ() + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2 - 1) * range;
                    double ry = body.getY();
                    double speed = (a.speed() != null) ? clamp(a.speed(), 1.0, 1.3) : 1.0;

                    mob.getNavigation().moveTo(rx, ry, rz, speed);
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

                if (body instanceof com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity ambBot) {
                    float speed = (a.speed() != null) ? (float) clamp(a.speed(), 0.1, 0.3) : 0.2f;
                    ambBot.setMoveTarget(new Vec3(tx, ty, tz), speed);
                    st.mode = BotBrain.Mode.IDLE;
                    st.lastError = "";
                    clearGoal(st);
                } else if (body instanceof Mob mob) {
                    double speed = (a.speed() != null) ? clamp(a.speed(), 1.0, 1.3) : 1.0;

                    mob.getNavigation().moveTo(tx, ty, tz, speed);

                    st.mode = BotBrain.Mode.IDLE;
                    st.lastError = "";
                    clearGoal(st);
                } else {
                    st.lastError = "Body cannot move_to.";
                }
            }

            case "explore" -> {
                if (body instanceof Mob mob) {
                    ServerLevel lvl = (ServerLevel) mob.level();
                    int now = server.getTickCount();
                    int secs = (a.seconds() != null) ? Math.max(2, Math.min(30, a.seconds())) : 8;
                    // PLAYER PARITY FIX: Use exact player speeds
                    // Player walk: 1.0, Player sprint: 1.3 (navigation multipliers)
                    double speed = (a.speed() != null) ? clamp(a.speed(), 1.0, 1.3) : 1.0;

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

                    mob.getNavigation().moveTo(
                            st.goalX,
                            st.goalY != null ? st.goalY : body.getY(),
                            st.goalZ,
                            speed
                    );

                    st.mode = BotBrain.Mode.IDLE;
                    st.lastError = "";
                    st.lastThought = "exploring for " + secs + "s";
                } else {
                    st.lastError = "Body cannot explore (not a Mob).";
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

    private static void doAttackNearestHostile(ServerLevel level, Mob mob) {
        var target = level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                mob.getBoundingBox().inflate(16.0),
                e -> e != null && e.isAlive()
                        && e.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
        ).stream().min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(mob))).orElse(null);

        if (target == null) return;

        mob.setTarget(target);
        // PLAYER PARITY FIX: Use player sprint speed for combat
        mob.getNavigation().moveTo(target, 1.3);
    }


    private static void wander(ServerLevel level, LivingEntity body, int tick) {
        if (!(body instanceof Mob mob)) return;
        if (tick % 60 != 0) return;

        double ox = body.getX();
        double oz = body.getZ();

        double ang = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        double dist = java.util.concurrent.ThreadLocalRandom.current().nextDouble(6.0, 14.0);

        double tx = ox + Math.cos(ang) * dist;
        double tz = oz + Math.sin(ang) * dist;

        int ix = (int) Math.floor(tx);
        int iz = (int) Math.floor(tz);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);

        // PLAYER PARITY FIX: Use exact player walk speed
        mob.getNavigation().moveTo(ix + 0.5, y, iz + 0.5, 1.0);
    }

    private static void doMineStone(ServerLevel level, LivingEntity hands, LivingEntity body) {
        BlockPos origin = body.blockPosition();

        // Prefer stone-like blocks
        BlockPos target = findNearestBlock(level, origin, 10, s ->
                s.is(net.minecraft.tags.BlockTags.STONE_ORE_REPLACEABLES)
                        || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
        );


        if (target == null) return;

        moveBodyTo(level, body, target);

        if (body.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5) < 2.2) {
            level.destroyBlock(target, true, hands);
        }
    }


    private static void doBuildSimpleShelter(ServerLevel level, LivingEntity hands, LivingEntity body) {
        // TODO: Implement shelter building logic
        BlockPos placePos = body.blockPosition().above();
        level.setBlock(placePos, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), 3);
    }

    private static BlockPos findNearestBlock(ServerLevel level, BlockPos origin, int radius, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> predicate) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        // Search in expanding spherical shells for efficiency
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -10; dy <= 10; dy++) { // Trees are tall, search vertically
                for (int dz = -radius; dz <= radius; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

                    // Skip if too far (rough sphere check)
                    double distSq = origin.distSqr(pos);
                    if (distSq > radius * radius) continue;

                    if (predicate.test(level.getBlockState(pos))) {
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = pos.immutable();
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static void moveBodyTo(ServerLevel level, LivingEntity body, BlockPos target) {
        if (body instanceof Mob mob) {
            // PLAYER PARITY FIX: Use exact player walk speed
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- Explore safety helpers ----

    private static Vec3 pickSafeExploreTarget(ServerLevel level, Vec3 origin) {
        for (int i = 0; i < 20; i++) {
            double ang = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
            double dist = java.util.concurrent.ThreadLocalRandom.current().nextDouble(28.0 * 0.45, 28.0);

            double tx = origin.x + Math.cos(ang) * dist;
            double tz = origin.z + Math.sin(ang) * dist;

            int ix = (int) Math.floor(tx);
            int iz = (int) Math.floor(tz);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);

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
