/*
 * Base module copied from Meteor Client AutoTrap
 * https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/combat/AutoTrap.java
 */
package me.noramibu.tweaks.modules;

import me.noramibu.tweaks.NoraTweaks;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class AutoTrapPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("Blocks to use for trapping.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range at which blocks can be placed.")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> placeWallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("Range in which to place when behind blocks.")
        .defaultValue(4)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to select the player to target.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance to target players.")
        .defaultValue(3)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between block placements.")
        .defaultValue(1)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick.")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<TopMode> topPlacement = sgGeneral.add(new EnumSetting.Builder<TopMode>()
        .name("top-blocks")
        .description("Which blocks to place at head height.")
        .defaultValue(TopMode.Full)
        .build()
    );

    // Bottom blocks are always placed in Full mode (with crystal/anchor gap support)

    private final Setting<BuildOrder> buildOrder = sgGeneral.add(new EnumSetting.Builder<BuildOrder>()
        .name("build-order")
        .description("Order to build columns: bottom-to-top or top-to-bottom.")
        .defaultValue(BuildOrder.BottomToTop)
        .build()
    );

    private final Setting<Boolean> selfToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("self-toggle")
        .description("Toggle off after placing all blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate towards blocks when placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<GapSide> gapSide = sgGeneral.add(new EnumSetting.Builder<GapSide>()
        .name("Crystal/Anchor gap")
        .description("Leave one feet-level side as air for crystals/anchors.")
        .defaultValue(GapSide.None)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allows placing blocks in the air. Disable to build supports first.")
        .defaultValue(false)
        .build()
    );

    private final List<BlockPos> placePositions = new ArrayList<>();
    private Player target;
    private boolean placedAny;
    private int timer;
    private BlockPos gapFeetPos;

    public AutoTrapPlus() {
        super(NoraTweaks.CATEGORY, "auto-trap+", "Traps a target player. Adds an optional anti-cheat friendly support placement mode.");
    }

    @Override
    public void onActivate() {
        target = null;
        placePositions.clear();
        timer = 0;
        placedAny = false;
        gapFeetPos = null;
    }

    @Override
    public void onDeactivate() {
        placePositions.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (selfToggle.get() && placedAny && placePositions.isEmpty()) {
            placedAny = false;
            toggle();
            return;
        }

        // Find blocks in hotbar
        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.byItem(itemStack.getItem())));
        if (!block.found()) return;

        // Find target
        if (TargetUtils.isBadTarget(target, targetRange.get())) {
            target = TargetUtils.getPlayerTarget(targetRange.get(), priority.get());
            if (TargetUtils.isBadTarget(target, targetRange.get())) return;
        }

        // Compute gap position (feet-level) to skip and to avoid filling with supports
        gapFeetPos = null;
        if (gapSide.get() != GapSide.None) {
            int feetY = (int) Math.floor(target.getBoundingBox().minY);
            BlockPos center = BlockPos.containing(target.getX(), feetY, target.getZ());
            gapFeetPos = center.offset(getGapOffsetX(target), 0, getGapOffsetZ(target));
        }

        // Compute trap positions
        fillPlaceArray(target);

        // Place following sorted order (column-wise, respecting build order)
        if (timer >= delay.get() && !placePositions.isEmpty()) {
            int placedCount = 0;
            for (BlockPos placePos : placePositions) {
                if (placedCount >= blocksPerTick.get()) break;
                if (tryPlaceWithSupports(placePos, block)) {
                    placedAny = true;
                    placedCount++;
                }
            }
            timer = 0;
        } else timer++;
    }

    private boolean tryPlaceWithSupports(BlockPos placePos, FindItemResult block) {
        // Direct place if allowed
        if (airPlace.get()) return BlockUtils.place(placePos, block, rotate.get(), 50, true);

        // Need a neighbor face; first try direct
        if (BlockUtils.getPlaceSide(placePos) != null) return BlockUtils.place(placePos, block, rotate.get(), 50, true);
        if (!mc.level.getBlockState(placePos).canBeReplaced()) return false;

        // Build candidate directions with outward first, then others
        net.minecraft.core.Direction outward = net.minecraft.core.Direction.NORTH;
        if (target != null) {
            BlockPos center = BlockPos.containing(target.getX(), Math.floor(target.getBoundingBox().minY), target.getZ());
            int dx = placePos.getX() - center.getX();
            int dz = placePos.getZ() - center.getZ();
            if (Math.abs(dx) >= Math.abs(dz)) outward = dx > 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
            else outward = dz > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
        }

        net.minecraft.core.Direction[] dirs = new net.minecraft.core.Direction[] {
            outward,
            outward.getClockWise(),
            outward.getCounterClockWise(),
            outward.getOpposite()
        };

        // Optimize supports: place exactly one side support adjacent to target, preferring outward first
        for (net.minecraft.core.Direction d : dirs) {
            BlockPos s = placePos.relative(d);
            // don't block the feet gap column
            if (gapFeetPos != null && s.below().equals(gapFeetPos)) continue;
            if (BlockUtils.getPlaceSide(s) == null) continue;
            if (BlockUtils.place(s, block, rotate.get(), 50, true)) {
                return BlockUtils.getPlaceSide(placePos) != null && BlockUtils.place(placePos, block, rotate.get(), 50, true);
            }
        }

        return false;
    }

    // Removed old helper methods for broad support search to keep logic minimal

    private void fillPlaceArray(Player t) {
        placePositions.clear();

        double epsilon = 1e-5;
        AABB box = t.getBoundingBox();
        List<BlockPos> corners = new ArrayList<>();
        corners.add(BlockPos.containing(box.minX, box.minY, box.minZ));
        corners.add(BlockPos.containing(box.minX, box.minY, box.maxZ - epsilon));
        corners.add(BlockPos.containing(box.maxX - epsilon, box.minY, box.minZ));
        corners.add(BlockPos.containing(box.maxX - epsilon, box.minY, box.maxZ - epsilon));

        Set<BlockPos> overlappedPositions = new LinkedHashSet<>(corners);
        for (BlockPos base : overlappedPositions) {
            switch (topPlacement.get()) {
                case Full -> {
                    add(base.offset(0, 2, 0));
                    add(base.offset(1, 1, 0));
                    add(base.offset(-1, 1, 0));
                    add(base.offset(0, 1, 1));
                    add(base.offset(0, 1, -1));
                }
                case Face -> {
                    add(base.offset(1, 1, 0));
                    add(base.offset(-1, 1, 0));
                    add(base.offset(0, 1, 1));
                    add(base.offset(0, 1, -1));
                }
                case Top -> add(base.offset(0, 2, 0));
                case None -> {}
            }
            // Bottom - always Full: platform below (y -1) and full ring at feet level (y 0)
            add(base.offset(0, -1, 0));
            add(base.offset(1, -1, 0));
            add(base.offset(-1, -1, 0));
            add(base.offset(0, -1, 1));
            add(base.offset(0, -1, -1));

            add(base.offset(1, 0, 0));
            add(base.offset(-1, 0, 0));
            add(base.offset(0, 0, -1));
            add(base.offset(0, 0, 1));
        }

        // Apply gap: remove only the feet-level block on the chosen side
        if (gapSide.get() != GapSide.None) {
            if (gapFeetPos != null) placePositions.remove(gapFeetPos);
        }
        // Column-wise build order: group by exact (x,z), then order Y strictly by buildOrder
        boolean bottomToTop = buildOrder.get() == BuildOrder.BottomToTop;
        placePositions.sort((a, b) -> {
            if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
            if (a.getZ() != b.getZ()) return Integer.compare(a.getZ(), b.getZ());
            return bottomToTop ? Integer.compare(a.getY(), b.getY()) : Integer.compare(b.getY(), a.getY());
        });
    }

    private void add(BlockPos blockPos) {
        if (placePositions.contains(blockPos)) return;
        if (!BlockUtils.canPlace(blockPos)) return;
        if (isOutOfRange(blockPos)) return;
        placePositions.add(blockPos);
    }

    private boolean isOutOfRange(BlockPos blockPos) {
        Vec3 pos = Vec3.atCenterOf(blockPos);
        if (!PlayerUtils.isWithin(pos, placeRange.get())) return true;

        ClipContext raycastContext = new ClipContext(mc.player.getEyePosition(), pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult result = mc.level.clip(raycastContext);
        if (result == null || !result.getBlockPos().equals(blockPos)) return !PlayerUtils.isWithin(pos, placeWallsRange.get());
        return false;
    }

    // Removed unused isAirPlace()

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }

    private int getGapOffsetX(Player t) {
        return switch (gapSide.get()) {
            case East -> 1;
            case West -> -1;
            case South, North -> 0;
            case TowardPlayer -> {
                //? if >=1.21.9 {
                Vec3 toPlayer = mc.player.position().subtract(t.position());
                //?} else
                /*Vec3d toPlayer = mc.player.getPos().subtract(t.getPos());
                */
                if (Math.abs(toPlayer.x) >= Math.abs(toPlayer.z)) yield toPlayer.x > 0 ? 1 : -1;
                else yield 0;
            }
            case None -> 0;
        };
    }

    private int getGapOffsetZ(Player t) {
        return switch (gapSide.get()) {
            case South -> 1;
            case North -> -1;
            case East, West -> 0;
            case TowardPlayer -> {
                //? if >=1.21.9 {
                Vec3 toPlayer = mc.player.position().subtract(t.position());
                //?} else
                /*Vec3d toPlayer = mc.player.getPos().subtract(t.getPos());
                */
                if (Math.abs(toPlayer.z) > Math.abs(toPlayer.x)) yield toPlayer.z > 0 ? 1 : -1;
                else yield 0;
            }
            case None -> 0;
        };
    }

    public enum TopMode {
        Full,
        Top,
        Face,
        None
    }

    // Bottom mode is fixed to Full; enum removed

    public enum GapSide {
        None,
        TowardPlayer,
        North,
        South,
        East,
        West
    }

    public enum BuildOrder {
        BottomToTop,
        TopToBottom
    }
}
