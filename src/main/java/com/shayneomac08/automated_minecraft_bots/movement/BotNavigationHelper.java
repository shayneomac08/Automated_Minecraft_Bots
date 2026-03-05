package com.shayneomac08.automated_minecraft_bots.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Central navigation utility used by BotEscapeHelper and other systems.
 * All methods are pure static — no entity state is stored here.
 */
public final class BotNavigationHelper {

    private BotNavigationHelper() {}

    // ── Part 1a: isEnclosed ───────────────────────────────────────────────────

    /**
     * Returns true if the bot cannot see open sky AND has no clear horizontal
     * exit (2-tall, 1-wide) within 16 blocks in any cardinal direction.
     */
    public static boolean isEnclosed(ServerLevel level, BlockPos botPos) {
        // Fast path: if sky is visible within 4 blocks above, not enclosed
        for (int dy = 0; dy <= 4; dy++) {
            if (level.canSeeSky(botPos.above(dy))) return false;
        }

        // Check each cardinal direction for a 2-tall corridor leading to sky
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            for (int dist = 1; dist <= 16; dist++) {
                BlockPos feet = botPos.relative(dir, dist);
                BlockState feetState = level.getBlockState(feet);
                BlockState headState = level.getBlockState(feet.above());

                if (isPassableBlock(feetState) && isPassableBlock(headState)) {
                    // Check if this position sees sky
                    if (level.canSeeSky(feet) || level.canSeeSky(feet.above())) {
                        return false; // Clear exit found in this direction
                    }
                } else {
                    break; // Solid wall, stop scanning this direction
                }
            }
        }
        return true;
    }

    // ── Part 1b: findNearestExit ──────────────────────────────────────────────

    /**
     * Scans outward in expanding rings up to 32 blocks.
     * Priority: doors first, then 2-tall horizontal openings leading to sky,
     * then upward staircases.
     * Returns the BlockPos of the exit, or null if none found.
     */
    public static BlockPos findNearestExit(ServerLevel level, BlockPos botPos) {
        BlockPos doorExit = null;
        BlockPos openingExit = null;
        BlockPos stairExit = null;

        outer:
        for (int ring = 1; ring <= 32; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue; // Border of ring only

                    for (int dy = -2; dy <= 4; dy++) {
                        BlockPos check = botPos.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(check);

                        // Priority 1: Any door
                        if (doorExit == null && state.is(BlockTags.DOORS)) {
                            doorExit = check;
                            break outer; // Door is highest priority — return immediately
                        }

                        // Priority 2: 2-tall air opening leading toward sky
                        if (openingExit == null
                                && isPassableBlock(state)
                                && isPassableBlock(level.getBlockState(check.above()))) {
                            if (level.canSeeSky(check) || level.canSeeSky(check.above(2))) {
                                openingExit = check;
                            }
                        }

                        // Priority 3: Staircase leading upward with passable top
                        if (stairExit == null && state.getBlock() instanceof StairBlock) {
                            BlockState above  = level.getBlockState(check.above());
                            BlockState above2 = level.getBlockState(check.above(2));
                            if (isPassableBlock(above) && isPassableBlock(above2)) {
                                stairExit = check;
                            }
                        }
                    }
                }
            }

            // Return best found at this ring before expanding further
            if (doorExit != null)    return doorExit;
            if (openingExit != null) return openingExit;
        }

        if (openingExit != null) return openingExit;
        return stairExit;
    }

    // ── Part 1c: findSurfacePath ──────────────────────────────────────────────

    /**
     * Scans in expanding spiral up to 8 blocks horizontally to find a column
     * where every block from botPos.Y to the surface is air, leaves, or water.
     * Returns BlockPos at bot's Y in that column as a dig-up/walk-up target,
     * or null if no such column exists within range.
     */
    public static BlockPos findSurfacePath(ServerLevel level, BlockPos botPos) {
        int searchCap = botPos.getY() + 100; // Reasonable cap — don't scan all the way to 320

        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    int colX = botPos.getX() + dx;
                    int colZ = botPos.getZ() + dz;

                    boolean columnClear = true;
                    for (int y = botPos.getY(); y < searchCap; y++) {
                        BlockState state = level.getBlockState(new BlockPos(colX, y, colZ));
                        if (!isClearOrSoft(level, new BlockPos(colX, y, colZ), state)) {
                            columnClear = false;
                            break;
                        }
                    }

                    if (columnClear) {
                        return new BlockPos(colX, botPos.getY(), colZ);
                    }
                }
            }
        }
        return null; // No clear column within 8 blocks
    }

    // ── Part 1d: isPathClear ──────────────────────────────────────────────────

    /**
     * Simple line-of-sight check through passable blocks between two positions.
     * Returns true if no solid block interrupts the line between from and to.
     */
    public static boolean isPathClear(ServerLevel level, BlockPos from, BlockPos to) {
        int steps = (int) Math.ceil(Math.sqrt(from.distSqr(to)));
        if (steps == 0) return true;

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            BlockPos check = BlockPos.containing(
                from.getX() + t * (to.getX() - from.getX()),
                from.getY() + t * (to.getY() - from.getY()),
                from.getZ() + t * (to.getZ() - from.getZ())
            );
            if (!isPassableBlock(level.getBlockState(check))) return false;
        }
        return true;
    }

    // ── Part 1e: canStandAt ───────────────────────────────────────────────────

    /**
     * Returns true if pos is a solid floor, pos+1 is passable, and pos+2 is passable.
     */
    public static boolean canStandAt(ServerLevel level, BlockPos pos) {
        BlockState floor = level.getBlockState(pos);
        BlockState feet  = level.getBlockState(pos.above());
        BlockState head  = level.getBlockState(pos.above(2));
        return floor.canOcclude() && isPassableBlock(feet) && isPassableBlock(head);
    }

    // ── Part 3: Door interaction ──────────────────────────────────────────────

    /**
     * Toggles the open state of the door at doorPos.
     * Returns true if the door was found and toggled.
     */
    public static boolean interactWithDoor(ServerLevel level, BlockPos doorPos) {
        BlockState state = level.getBlockState(doorPos);
        if (!(state.getBlock() instanceof DoorBlock)) return false;

        boolean currentOpen = state.getOptionalValue(BlockStateProperties.OPEN).orElse(false);
        level.setBlock(doorPos,
                state.setValue(BlockStateProperties.OPEN, !currentOpen),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        return true;
    }

    /**
     * Opens the door at doorPos (if closed) and returns the BlockPos on the
     * far side of the door relative to approachFrom.
     * The caller should navigate to the returned pos to complete the passage.
     */
    public static BlockPos useDoor(ServerLevel level, BlockPos doorPos, BlockPos approachFrom) {
        BlockState state = level.getBlockState(doorPos);
        if (state.getBlock() instanceof DoorBlock) {
            boolean isOpen = state.getOptionalValue(BlockStateProperties.OPEN).orElse(false);
            if (!isOpen) {
                level.setBlock(doorPos,
                        state.setValue(BlockStateProperties.OPEN, true),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
        }
        return getDoorFarSide(level, doorPos, approachFrom);
    }

    /**
     * Returns the BlockPos 2 blocks beyond the door on the far side from approachFrom.
     */
    public static BlockPos getDoorFarSide(ServerLevel level, BlockPos doorPos, BlockPos approachFrom) {
        BlockState state = level.getBlockState(doorPos);
        Direction facing = state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                .orElse(Direction.NORTH);

        BlockPos side1 = doorPos.relative(facing, 2);
        BlockPos side2 = doorPos.relative(facing.getOpposite(), 2);

        // Return the side farther from where the bot is approaching
        double d1 = approachFrom.distSqr(side1);
        double d2 = approachFrom.distSqr(side2);
        return d1 > d2 ? side1 : side2;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /**
     * A block is passable for navigation if it does not fully occlude,
     * or if it is a door (which can be opened).
     */
    public static boolean isPassableBlock(BlockState state) {
        if (state.isAir()) return true;
        if (state.is(BlockTags.DOORS))     return true;  // Can be opened
        if (state.is(BlockTags.TRAPDOORS)) return true;  // Trapdoors
        return !state.canOcclude();                       // Non-solid blocks
    }

    /**
     * A block is "clear or soft" for upward column scanning — the bot can
     * move through it without digging (air, leaves, or water).
     */
    private static boolean isClearOrSoft(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return true;
        if (state.is(BlockTags.LEAVES)) return true;
        if (!level.getFluidState(pos).isEmpty()) return true; // Water, lava, etc.
        return false;
    }
}
