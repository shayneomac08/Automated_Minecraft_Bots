package com.shayneomac08.automated_minecraft_bots.movement;

import com.shayneomac08.automated_minecraft_bots.entity.AmbNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stateful escape helper that replaces handleInteriorExitPlan() in AmbNpcEntity.
 * Activates when the bot is enclosed and cannot find natural exits via the door system.
 * Phases: IDLE → FIND_EXIT → NAVIGATE_TO_EXIT → DIG_UP → DIG_HORIZONTAL → COMPLETE
 *
 * Call tick(currentTick) each game tick; returns true while escape is active (suppresses other tasks).
 */
public class BotEscapeHelper {

    public enum Phase { IDLE, FIND_EXIT, NAVIGATE_TO_EXIT, DIG_UP, DIG_HORIZONTAL, COMPLETE }

    private final AmbNpcEntity bot;

    private Phase phase = Phase.IDLE;
    private BlockPos exitTarget = null;
    private BlockPos digTarget  = null;
    private int cooldownTicks   = 0;  // Ticks to wait before re-evaluating
    private int phaseTicks      = 0;  // Ticks spent in current phase (timeout guard)
    private int digProgress     = 0;  // Blocks broken in current dig sequence

    // How long to wait between re-evaluating from IDLE (ticks)
    private static final int IDLE_RECHECK_INTERVAL  = 40;   // 2 s
    private static final int PHASE_TIMEOUT          = 400;  // 20 s max per phase
    private static final int NAV_TIMEOUT            = 600;  // 30 s max navigating to exit
    private static final int COMPLETE_HOLD_TICKS    = 100;  // 5 s cooldown after success

    // Minimum enclosed ticks before triggering escape (avoids false positives in doorways)
    private int enclosedTicks = 0;
    private static final int ENCLOSE_CONFIRM_TICKS  = 60;   // 3 s continuously enclosed

    public BotEscapeHelper(AmbNpcEntity bot) {
        this.bot = bot;
    }

    /**
     * Called every game tick from AmbNpcEntity.runAllPlayerActions().
     * @return true while the escape is active (other tasks should be suppressed).
     */
    public boolean tick(int currentTick) {
        if (!(bot.level() instanceof ServerLevel level)) return false;

        // Handle cooldown
        if (cooldownTicks > 0) {
            cooldownTicks--;
            if (phase == Phase.COMPLETE && cooldownTicks == 0) {
                phase = Phase.IDLE;
                enclosedTicks = 0;
            }
            return phase != Phase.IDLE && phase != Phase.COMPLETE;
        }

        switch (phase) {
            case IDLE -> {
                // Only evaluate every IDLE_RECHECK_INTERVAL ticks
                if (currentTick % IDLE_RECHECK_INTERVAL != 0) return false;

                boolean enclosed = BotNavigationHelper.isEnclosed(level, bot.blockPosition());
                if (enclosed) {
                    enclosedTicks += IDLE_RECHECK_INTERVAL;
                } else {
                    enclosedTicks = 0;
                    return false;
                }

                if (enclosedTicks >= ENCLOSE_CONFIRM_TICKS) {
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " confirmed enclosed — starting escape (" + enclosedTicks + " ticks)");
                    transitionTo(Phase.FIND_EXIT);
                }
                return false;
            }

            case FIND_EXIT -> {
                exitTarget = BotNavigationHelper.findNearestExit(level, bot.blockPosition());
                if (exitTarget == null) {
                    exitTarget = BotNavigationHelper.findSurfacePath(level, bot.blockPosition());
                }

                if (exitTarget != null) {
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " exit target: " + exitTarget);
                    transitionTo(Phase.NAVIGATE_TO_EXIT);
                } else {
                    // No horizontal exit — try to dig up
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " no exit found — switching to DIG_UP");
                    transitionTo(Phase.DIG_UP);
                }
                return true;
            }

            case NAVIGATE_TO_EXIT -> {
                phaseTicks++;
                if (phaseTicks > NAV_TIMEOUT) {
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " navigate timeout — trying DIG_UP");
                    transitionTo(Phase.DIG_UP);
                    return true;
                }

                if (exitTarget == null) {
                    transitionTo(Phase.FIND_EXIT);
                    return true;
                }

                // Check if we reached the exit or are now outside
                boolean nowOutside = !BotNavigationHelper.isEnclosed(level, bot.blockPosition());
                boolean nearTarget = bot.blockPosition().closerThan(exitTarget, 2.5);

                if (nowOutside || nearTarget) {
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " escape complete! outside=" + nowOutside + " nearTarget=" + nearTarget);
                    transitionTo(Phase.COMPLETE);
                    return true;
                }

                // Check if the path to exit is clear; if not, switch to horizontal dig
                if (!BotNavigationHelper.isPathClear(level, bot.blockPosition(), exitTarget)) {
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " path blocked — switching to DIG_HORIZONTAL");
                    transitionTo(Phase.DIG_HORIZONTAL);
                    return true;
                }

                // Navigate toward exit by updating bot's move target
                bot.setMoveTarget(
                    net.minecraft.world.phys.Vec3.atCenterOf(exitTarget), 0.13f
                );
                return true;
            }

            case DIG_UP -> {
                phaseTicks++;
                if (phaseTicks > PHASE_TIMEOUT) {
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " DIG_UP timeout — trying DIG_HORIZONTAL");
                    transitionTo(Phase.DIG_HORIZONTAL);
                    return true;
                }

                // Check if we're now outside
                if (!BotNavigationHelper.isEnclosed(level, bot.blockPosition())) {
                    System.out.println("[ESCAPE] " + bot.getName().getString() + " DIG_UP success!");
                    transitionTo(Phase.COMPLETE);
                    return true;
                }

                // Break the block directly above
                BlockPos above = bot.blockPosition().above();
                BlockState aboveState = level.getBlockState(above);

                if (!aboveState.isAir()) {
                    bot.gameMode.destroyBlock(above);
                    digProgress++;
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " digging up at " + above + " (block " + digProgress + ")");
                } else {
                    // Air above — jump/move upward
                    bot.jumpFromGround();
                    bot.setMoveTarget(
                        net.minecraft.world.phys.Vec3.atCenterOf(above), 0.1f
                    );
                }
                return true;
            }

            case DIG_HORIZONTAL -> {
                phaseTicks++;
                if (phaseTicks > PHASE_TIMEOUT) {
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " DIG_HORIZONTAL timeout — resetting escape");
                    reset();
                    return false;
                }

                // Check if we're now outside
                if (!BotNavigationHelper.isEnclosed(level, bot.blockPosition())) {
                    System.out.println("[ESCAPE] " + bot.getName().getString()
                            + " DIG_HORIZONTAL success!");
                    transitionTo(Phase.COMPLETE);
                    return true;
                }

                // Find a dig target in a cardinal direction toward open sky
                if (digTarget == null || level.getBlockState(digTarget).isAir()) {
                    digTarget = findHorizontalDigTarget(level);
                }

                if (digTarget != null) {
                    BlockState state = level.getBlockState(digTarget);
                    if (!state.isAir()) {
                        bot.gameMode.destroyBlock(digTarget);
                        // Also break the block above (2-tall clearance)
                        BlockPos digAbove = digTarget.above();
                        if (!level.getBlockState(digAbove).isAir()) {
                            bot.gameMode.destroyBlock(digAbove);
                        }
                        digProgress++;
                    } else {
                        // Block is air — move toward it
                        bot.setMoveTarget(
                            net.minecraft.world.phys.Vec3.atCenterOf(digTarget), 0.1f
                        );
                        digTarget = null; // Will re-evaluate next tick
                    }
                } else {
                    // No horizontal target found — fall back to dig up
                    transitionTo(Phase.DIG_UP);
                }
                return true;
            }

            case COMPLETE -> {
                // Handled by cooldown countdown above; this branch shouldn't normally run
                return false;
            }
        }
        return false;
    }

    /** True while escape is actively running (FIND_EXIT, NAVIGATE_TO_EXIT, DIG_*). */
    public boolean isActive() {
        return phase != Phase.IDLE && phase != Phase.COMPLETE;
    }

    /** Force-resets the escape helper (e.g., when the bot receives a new LLM task). */
    public void reset() {
        phase        = Phase.IDLE;
        exitTarget   = null;
        digTarget    = null;
        phaseTicks   = 0;
        digProgress  = 0;
        enclosedTicks = 0;
        cooldownTicks = 0;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void transitionTo(Phase next) {
        phase = next;
        phaseTicks = 0;
        digTarget = null;

        if (next == Phase.COMPLETE) {
            cooldownTicks = COMPLETE_HOLD_TICKS;
            enclosedTicks = 0;
            exitTarget = null;
            digProgress = 0;
            bot.stopMovement();
        }
    }

    /**
     * Finds the first solid block in a cardinal direction that, if broken,
     * would move the bot closer to open sky.
     */
    private BlockPos findHorizontalDigTarget(ServerLevel level) {
        net.minecraft.core.Direction[] cardinals = {
            net.minecraft.core.Direction.NORTH,
            net.minecraft.core.Direction.SOUTH,
            net.minecraft.core.Direction.EAST,
            net.minecraft.core.Direction.WEST
        };

        BlockPos botPos = bot.blockPosition();

        for (net.minecraft.core.Direction dir : cardinals) {
            for (int dist = 1; dist <= 6; dist++) {
                BlockPos check = botPos.relative(dir, dist);
                BlockState state = level.getBlockState(check);

                if (!BotNavigationHelper.isPassableBlock(state)) {
                    // This is a solid block to dig through
                    return check;
                }

                // If air and we can see sky from further out, this direction is promising
                if (BotNavigationHelper.isPassableBlock(state)) {
                    BlockPos beyond = botPos.relative(dir, dist + 1);
                    if (level.canSeeSky(beyond) || level.canSeeSky(beyond.above())) {
                        return check; // The opening is just past here
                    }
                }
            }
        }
        return null;
    }
}
