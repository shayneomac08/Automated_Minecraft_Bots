package com.shayneomac08.automated_minecraft_bots.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET;

public final class BotTicker {

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();

        // Handle bot chat messages
        com.shayneomac08.automated_minecraft_bots.event.ChatEventHandler.tickBotChat(server);

        for (BotPair pair : BotRegistry.all()) {
            if (pair == null) continue;

            var hands = pair.hands();
            LivingEntity body = pair.body();
            if (body == null || body.isRemoved()) continue;

            ServerLevel level = (ServerLevel) body.level();
            String botName = body.getName().getString();
            BotBrain.tick(server, botName, pair);
            BotBrain.State brain = BotBrain.stateForName(botName);

            // Check for stuck movement and fix it
            boolean wasStuck = BotMovementHelper.checkAndFixStuck(level, body, brain.movementState, tick);
            if (wasStuck) {
                brain.lastThought = "Was stuck, took corrective action";
            }

            boolean isNight = (level.getDayTime() % 24000L) >= 13000L;
            boolean hostilesNearby = hasHostileNearby(level, body);
            boolean danger = isNight || hostilesNearby;

// PRIORITY 1: shelter in danger
            if (danger) {
                if (isSheltered(level, body.blockPosition())) {
                    if (body instanceof net.minecraft.world.entity.Mob) {
                        ((net.minecraft.world.entity.Mob) body).getNavigation().stop();
                    }
                    brain.goalUntilTick = tick + 40; // short lock
                } else {
                    BlockPos shelter = findShelter(level, body.blockPosition());
                    if (shelter != null && body instanceof net.minecraft.world.entity.Mob) {
                        net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) body;
                        // Speed multiplier: 1.15 = slightly faster than walk for urgency
                        mob.getNavigation().moveTo(shelter.getX() + 0.5, shelter.getY(), shelter.getZ() + 0.5, 1.15);
                        brain.goalUntilTick = tick + Math.min(ticksUntilDawn(level), 20 * 120);
                        brain.goalX = shelter.getX() + 0.5;
                        brain.goalY = (double) shelter.getY();
                        brain.goalZ = shelter.getZ() + 0.5;
                        brain.lastThought = "seeking shelter";
                    } else {
                        if (body instanceof net.minecraft.world.entity.Mob) {
                            ((net.minecraft.world.entity.Mob) body).getNavigation().stop();
                        }
                        brain.goalUntilTick = tick + 40;
                    }
                }
            }
// PRIORITY 2: manual follow (only when NOT in danger)
            // Note: followRequested is currently always false, but kept for future feature
            // else if (brain.followRequested) {
            //     smoothFollowNearestPlayer(server, level, body, tick, brain);
            // }
// PRIORITY 3: execute goal logic (LLM-driven)
            else {
                // The smart need-assessment system in AmbNpcEntity.tick() handles goal decisions
                // Goal system (AMBTaskGoal, etc.) handles all movement now
                // No manual wandering needed - RandomStrollGoal handles idle movement
            }


            // If we are currently running toward a shelter/goal lock, respect it
            boolean goalLocked = tick < brain.goalUntilTick;

            // 4) PRIORITY: manual follow (only if requested, and no locked goal)
            if (!goalLocked && brain.followRequested) {
                smoothFollowTargetPlayer(server, level, body, brain, tick);
            }
            // Note: Removed duplicate wander() calls - RandomStrollGoal handles idle movement
            // This prevents conflict between Goal system and manual velocity setting

            // 6) PvP protection: prevent attacking players unless allowed
            if (body instanceof net.minecraft.world.entity.Mob) {
                net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) body;
                var tgt = mob.getTarget();

                // If target is a player, only allow if PvP enabled OR player provoked bot recently
                if (tgt instanceof net.minecraft.server.level.ServerPlayer sp && !brain.allowPlayerCombat) {

                    boolean provoked = false;

                    // "Provoked" = player recently hurt the bot (best-effort, mappings differ)
                    try {
                        var last = body.getLastHurtByMob();
                        int ts = body.getLastHurtByMobTimestamp();
                        if (last == sp && (tick - ts) <= 200) { // 10 seconds
                            provoked = true;
                        }
                    } catch (Throwable ignored) {}

                    if (!provoked) {
                        mob.setTarget(null);
                        try { mob.getNavigation().stop(); } catch (Throwable ignored) {}
                        mob.getBrain().eraseMemory(ATTACK_TARGET);
                        mob.setAggressive(false);
                        mob.setLastHurtByMob(null);
                    }
                }
            }

            // 7) Hands always glued
            glueHands(hands, body);

            // 8) Inventory auto-saves on every change (NEW SYSTEM - no periodic persistence needed)
        }
    }


    private static void smoothFollowTargetPlayer(MinecraftServer server, ServerLevel level, LivingEntity body, BotBrain.State brain, int tick) {
        net.minecraft.server.level.ServerPlayer target = null;

        if (brain.followTarget != null) {
            target = server.getPlayerList().getPlayer(brain.followTarget);
        }

        // If target missing (logged out, etc.), fall back to nearest
        if (target == null) {
            target = server.getPlayerList().getPlayers().stream()
                    .min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(body)))
                    .orElse(null);
        }

        if (target == null) return;

        double minDist = 3.0;
        double maxDist = 160.0;
        double emergencyDist = 256.0;

        double dist2 = target.distanceToSqr(body);

        // Emergency teleport if extremely far
        if (dist2 > emergencyDist * emergencyDist) {
            double nx = target.getX() + 2.0;
            double nz = target.getZ() + 2.0;
            double ny = groundY(level, nx, nz);
            body.teleportTo(nx, ny, nz);
            body.setDeltaMovement(Vec3.ZERO);
            brain.farTicks = 0;
            return;
        }

        // Stop if close enough
        if (dist2 <= minDist * minDist) {
            if (body instanceof net.minecraft.world.entity.Mob mob) {
                mob.getNavigation().stop();
            }
            body.setDeltaMovement(body.getDeltaMovement().multiply(0.5, 1.0, 0.5));
            brain.farTicks = 0;
            return;
        }

        // Calculate direction to target
        double dx = target.getX() - body.getX();
        double dz = target.getZ() - body.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.01) return;

        // Normalize direction
        dx /= dist;
        dz /= dist;

        // Player speeds increased by 25%: walking = 0.125, sprinting = 0.1625 (base speed * 1.3)
        // When far, sprint. When close, walk.
        boolean shouldSprint = dist2 > maxDist * maxDist;

        // PLAYER PARITY: Use DIRECT MOVEMENT with exact player speeds
        if (body instanceof net.minecraft.world.entity.Mob) {
            // PLAYER PARITY FIX: Match player movement speeds exactly
            // Player walk speed: 0.1 blocks/tick (2 blocks/second)
            // Player sprint speed: 0.13 blocks/tick (2.6 blocks/second)
            double moveSpeed = shouldSprint ? 0.13 : 0.1;

            // DIRECT MOVEMENT: Set velocity directly toward target
            Vec3 movement = new Vec3(dx * moveSpeed, body.getDeltaMovement().y, dz * moveSpeed);
            body.setDeltaMovement(movement);

            // Update rotation to face target (smooth like player)
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            body.setYRot(yaw);
            body.setYHeadRot(yaw);
            body.yBodyRot = yaw;

            // Debug logging every 5 seconds
            if (tick % 100 == 0) {
                System.out.println("[AMB] Follow (Direct): Pos=" + body.blockPosition() +
                                 " Target=" + target.blockPosition() +
                                 " Dist=" + String.format("%.1f", dist) +
                                 " Velocity=" + String.format("%.3f", body.getDeltaMovement().horizontalDistance()));
            }
        }

        // Track far ticks for emergency teleport
        if (shouldSprint) {
            brain.farTicks++;
            if (brain.farTicks > 200) {
                double nx = target.getX() + 2.0;
                double nz = target.getZ() + 2.0;
                double ny = groundY(level, nx, nz);
                body.teleportTo(nx, ny, nz);
                body.setDeltaMovement(Vec3.ZERO);
                brain.farTicks = 0;
            }
        } else {
            brain.farTicks = 0;
        }
    }

    // Unused method - kept for potential future shelter logic refactoring
    // private static void doShelter(ServerLevel level, LivingEntity body, BotBrain.State brain, int tick) {
    //     if (isSheltered(level, body.blockPosition())) {
    //         if (body instanceof net.minecraft.world.entity.Mob) {
    //             ((net.minecraft.world.entity.Mob) body).getNavigation().stop();
    //         }
    //         // small lock so bot doesn't jitter-think
    //         brain.goalUntilTick = tick + 40;
    //         brain.goalX = brain.goalY = brain.goalZ = null;
    //         brain.lastThought = "staying under cover";
    //         return;
    //     }
    //
    //     BlockPos shelter = findShelter(level, body.blockPosition());
    //     if (shelter != null && body instanceof net.minecraft.world.entity.Mob) {
    //         net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) body;
    //         // Speed multiplier: 1.44 = slightly faster than walk for urgency (1.15 * 1.25)
    //         mob.getNavigation().moveTo(
    //                 shelter.getX() + 0.5,
    //                 shelter.getY(),
    //                 shelter.getZ() + 0.5,
    //                 1.44
    //         );
    //
    //         int ticksToDawn = ticksUntilDawn(level);
    //         brain.goalUntilTick = tick + Math.min(ticksToDawn, 20 * 120); // cap 2 min
    //         brain.goalX = shelter.getX() + 0.5;
    //         brain.goalY = (double) shelter.getY();
    //         brain.goalZ = shelter.getZ() + 0.5;
    //         brain.lastThought = "seeking shelter";
    //     } else {
    //         // No shelter found: pause (do not auto-follow)
    //         if (body instanceof net.minecraft.world.entity.Mob) {
    //             ((net.minecraft.world.entity.Mob) body).getNavigation().stop();
    //         }
    //         brain.goalUntilTick = tick + 40;
    //         brain.goalX = brain.goalY = brain.goalZ = null;
    //         brain.lastThought = "no shelter found, pausing";
    //     }
    // }

    private static void glueHands(net.neoforged.neoforge.common.util.FakePlayer hands, LivingEntity body) {
        if (hands != null && !hands.isRemoved()) {
            hands.teleportTo(body.getX(), body.getY(), body.getZ());
            hands.setYRot(body.getYRot());
            hands.setYHeadRot(body.getYHeadRot());
        }
    }

    // REMOVED: persistHandsOccasionally() - NEW SYSTEM auto-saves on every inventory change

    // REMOVED: wander() method - caused conflicts with RandomStrollGoal
    // The Goal system (RandomStrollGoal) handles idle wandering automatically
    // Manual velocity setting conflicted with navigation system causing "unsticking spam"

    private static double groundY(ServerLevel level, double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
    }

    private static boolean hasHostileNearby(ServerLevel level, LivingEntity body) {
        double r2 = 14.0 * 14.0;

        var hostiles = level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                body.getBoundingBox().inflate(14.0),
                m -> m.isAlive() && m.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
        );

        for (net.minecraft.world.entity.Mob m : hostiles) {
            if (m.distanceToSqr(body) <= r2) return true;
        }
        return false;
    }

    private static int ticksUntilDawn(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        return (int) (24000L - t);
    }

    private static boolean isSheltered(ServerLevel level, BlockPos pos) {
        try {
            return !level.canSeeSky(pos);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static BlockPos findShelter(ServerLevel level, BlockPos origin) {
        int ox = origin.getX();
        int oz = origin.getZ();

        BlockPos best = null;
        double bestDist2 = Double.MAX_VALUE;

        for (int dx = -22; dx <= 22; dx++) {
            for (int dz = -22; dz <= 22; dz++) {
                int x = ox + dx;
                int z = oz + dz;

                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos feet = new BlockPos(x, y, z);

                if (!isSafeWalkSpot(level, feet)) continue;
                if (!isSheltered(level, feet)) continue;

                double d2 = dx * (double) dx + dz * (double) dz;
                if (d2 < bestDist2) {
                    bestDist2 = d2;
                    best = feet;
                }
            }
        }
        return best;
    }

    private static boolean isSafeWalkSpot(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockPos head = feet.above();

        BlockState belowState = level.getBlockState(below);
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);

        if (!belowState.canOcclude()) return false;
        if (feetState.canOcclude() || headState.canOcclude()) return false;

        return level.getFluidState(feet).isEmpty();
    }
}
