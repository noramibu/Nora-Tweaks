/*
 * This code partially adapted from Meteor Rejects
 * Original source: https://github.com/AntiCope/meteor-rejects/
 * Credit: Meteor Rejects contributors
 * Heavily modified with additional features by Nora Tweaks
 */
package me.noramibu.tweaks.modules;

import me.noramibu.tweaks.NoraTweaks;
import meteordevelopment.meteorclient.events.entity.player.BreakBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class AutoFarming extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTill = settings.createGroup("Till");
    private final SettingGroup sgHarvest = settings.createGroup("Harvest Crops");
    private final SettingGroup sgTallCrops = settings.createGroup("Harvest Tall Crops");
    private final SettingGroup sgPlant = settings.createGroup("Plant");
    private final SettingGroup sgBonemeal = settings.createGroup("Bonemeal");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Auto farm range.")
        .defaultValue(4)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> bpt = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Amount of operations that can be applied in one tick.")
        .min(1)
        .defaultValue(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Whether or not to rotate towards block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("Swing hand when performing actions (helps with anti-cheat).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> till = sgTill.add(new BoolSetting.Builder()
        .name("till")
        .description("Turn nearby dirt into farmland.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> moist = sgTill.add(new BoolSetting.Builder()
        .name("moist")
        .description("Only till blocks near water.")
        .defaultValue(true)
        .visible(till::get)
        .build()
    );

    private final Setting<Boolean> harvest = sgHarvest.add(new BoolSetting.Builder()
        .name("harvest")
        .description("Harvest mature crops.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Block>> harvestBlocks = sgHarvest.add(new BlockListSetting.Builder()
        .name("harvest-blocks")
        .description("Which crops to harvest.")
        .defaultValue()
        .filter(this::harvestFilter)
        .visible(harvest::get)
        .build()
    );

    private final Setting<Boolean> harvestTallCrops = sgTallCrops.add(new BoolSetting.Builder()
        .name("harvest-tall-crops")
        .description("Harvest sugar cane, bamboo, kelp (breaks upper blocks, leaves bottom).")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Block>> tallCropBlocks = sgTallCrops.add(new BlockListSetting.Builder()
        .name("harvest-tall-blocks")
        .description("Which tall crops to harvest.")
        .defaultValue(Blocks.SUGAR_CANE, Blocks.BAMBOO, Blocks.KELP_PLANT)
        .filter(this::tallCropFilter)
        .visible(harvestTallCrops::get)
        .build()
    );

    private final Setting<Integer> tallCropMinHeight = sgTallCrops.add(new IntSetting.Builder()
        .name("min-height")
        .description("Minimum total height before harvesting. With min height 2, breaks when at least 2 blocks tall (keeps bottom 1).")
        .defaultValue(2)
        .min(2)
        .sliderRange(2, 10)
        .visible(harvestTallCrops::get)
        .build()
    );

    private final Setting<Boolean> tallCropSwingHand = sgTallCrops.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("Swing hand when breaking tall crops.")
        .defaultValue(true)
        .visible(harvestTallCrops::get)
        .build()
    );

    private final Setting<Boolean> plant = sgPlant.add(new BoolSetting.Builder()
        .name("plant")
        .description("Plant crops.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Item>> plantItems = sgPlant.add(new ItemListSetting.Builder()
        .name("plant-items")
        .description("Which crops to plant.")
        .defaultValue()
        .filter(this::plantFilter)
        .visible(plant::get)
        .build()
    );

    private final Setting<Boolean> onlyReplant = sgPlant.add(new BoolSetting.Builder()
        .name("only-replant")
        .description("Only replant where crops were previously harvested.")
        .defaultValue(true)
        .visible(plant::get)
        .onChanged(b -> clearReplantMap())
        .build()
    );

    private final Setting<Boolean> bonemeal = sgBonemeal.add(new BoolSetting.Builder()
        .name("bonemeal")
        .description("Bonemeal crops.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Block>> bonemealBlocks = sgBonemeal.add(new BlockListSetting.Builder()
        .name("bonemeal-blocks")
        .description("Which crops to bonemeal.")
        .defaultValue()
        .filter(this::bonemealFilter)
        .visible(bonemeal::get)
        .build()
    );

    private final Map<BlockPos, Item> replantMap = new HashMap<>();
    private final Pool<BlockPos.MutableBlockPos> blockPosPool = new Pool<>(BlockPos.MutableBlockPos::new);
    private final List<BlockPos.MutableBlockPos> blocks = new ArrayList<>();
    private int actions = 0;
    private int tickCounter = 0;
    private static final int REPLANT_CLEANUP_INTERVAL = 100;
    private static final double REPLANT_MAX_DISTANCE = 32.0;

    public AutoFarming() {
        super(NoraTweaks.CATEGORY, "auto-farming", "All-in-one farm utility.");
    }

    @Override
    public void onDeactivate() {
        clearReplantMap();
        freeBlockPool();
    }

    @EventHandler
    private void onBreakBlock(BreakBlockEvent event) {
        if (!onlyReplant.get()) return;

        BlockState state = mc.level.getBlockState(event.blockPos);
        Block block = state.getBlock();
        Item seedItem = getCropSeed(block);

        if (seedItem != null) {
            replantMap.put(event.blockPos.immutable(), seedItem);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        actions = 0;
        tickCounter++;

        if (tickCounter >= REPLANT_CLEANUP_INTERVAL) {
            cleanupReplantMap();
            tickCounter = 0;
        }

        collectBlocks();

        BlockIterator.after(() -> {
            sortBlocksByDistance();
            processBlocks();
            freeBlockPool();
        });
    }

    private void collectBlocks() {
        BlockIterator.register(range.get(), range.get(), (pos, state) -> {
            if (isWithinRange(pos)) {
                blocks.add(blockPosPool.get().set(pos));
            }
        });
    }

    private void sortBlocksByDistance() {
        blocks.sort(Comparator.comparingDouble(pos -> getPlayerDistance(pos)));
    }

    private void processBlocks() {
        for (BlockPos pos : blocks) {
            if (actions >= bpt.get()) break;

            BlockState state = mc.level.getBlockState(pos);
            Block block = state.getBlock();

            if (tryTill(pos, block)) continue;
            if (tryHarvest(pos, state, block)) continue;
            if (tryHarvestTallCrop(pos, block)) continue;
            if (tryPlant(pos, block)) continue;
            tryBonemeal(pos, state, block);
        }
    }

    private void freeBlockPool() {
        for (BlockPos.MutableBlockPos blockPos : blocks) {
            blockPosPool.free(blockPos);
        }
        blocks.clear();
    }

    private boolean tryTill(BlockPos pos, Block block) {
        if (!till.get()) return false;
        if (!isTillable(block)) return false;
        if (!mc.level.getBlockState(pos.above()).isAir()) return false;
        if (moist.get() && !isWaterNearby(mc.level, pos)) return false;

        FindItemResult hoe = InvUtils.findInHotbar(stack -> stack.getItem() instanceof HoeItem);
        if (!hoe.found()) return false;

        performInteraction(pos, hoe);
        actions++;
        return true;
    }

    private boolean isTillable(Block block) {
        return block == Blocks.GRASS_BLOCK ||
            block == Blocks.DIRT_PATH ||
            block == Blocks.DIRT ||
            block == Blocks.COARSE_DIRT ||
            block == Blocks.ROOTED_DIRT;
    }

    private boolean tryHarvest(BlockPos pos, BlockState state, Block block) {
        if (!harvest.get()) return false;
        if (!harvestBlocks.get().contains(block)) return false;
        if (!isMature(state, block)) return false;

        if (block instanceof SweetBerryBushBlock) {
            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), -100, () -> {
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                    if (swingHand.get()) mc.player.swing(InteractionHand.MAIN_HAND);
                });
            } else {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                if (swingHand.get()) mc.player.swing(InteractionHand.MAIN_HAND);
            }
        } else {
            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), -100, () -> {
                    mc.gameMode.continueDestroyBlock(pos, Direction.UP);
                    if (swingHand.get()) mc.player.swing(InteractionHand.MAIN_HAND);
                });
            } else {
                mc.gameMode.continueDestroyBlock(pos, Direction.UP);
                if (swingHand.get()) mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }
        actions++;
        return true;
    }

    private boolean tryHarvestTallCrop(BlockPos pos, Block block) {
        if (!harvestTallCrops.get()) return false;
        if (!tallCropBlocks.get().contains(block) && !isTallCrop(block)) return false;

        int totalHeight = getTotalTallCropHeight(pos, block);
        if (totalHeight < tallCropMinHeight.get()) return false;

        Block blockBelow = mc.level.getBlockState(pos.below()).getBlock();
        if (!isSameTallCrop(block, blockBelow)) return false;

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), -100, () -> {
                mc.gameMode.continueDestroyBlock(pos, Direction.UP);
                if (tallCropSwingHand.get()) mc.player.swing(InteractionHand.MAIN_HAND);
            });
        } else {
            mc.gameMode.continueDestroyBlock(pos, Direction.UP);
            if (tallCropSwingHand.get()) mc.player.swing(InteractionHand.MAIN_HAND);
        }
        actions++;
        return true;
    }

    private boolean isTallCrop(Block block) {
        return block == Blocks.SUGAR_CANE ||
               block == Blocks.BAMBOO ||
               block == Blocks.KELP_PLANT ||
               block == Blocks.KELP;
    }

    private boolean tallCropFilter(Block block) {
        return block == Blocks.SUGAR_CANE ||
               block == Blocks.BAMBOO ||
               block == Blocks.KELP_PLANT ||
               block == Blocks.KELP ||
               block == Blocks.CACTUS;
    }

    private boolean isSameTallCrop(Block a, Block b) {
        if ((a == Blocks.KELP || a == Blocks.KELP_PLANT) &&
            (b == Blocks.KELP || b == Blocks.KELP_PLANT)) {
            return true;
        }
        return a == b;
    }

    private int getTotalTallCropHeight(BlockPos pos, Block block) {
        int below = 0;
        BlockPos checkPos = pos.below();
        while (below < 16) {
            Block b = mc.level.getBlockState(checkPos).getBlock();
            if (!isSameTallCrop(block, b)) break;
            below++;
            checkPos = checkPos.below();
        }

        int above = 1;
        checkPos = pos.above();
        while (above < 16) {
            Block b = mc.level.getBlockState(checkPos).getBlock();
            if (!isSameTallCrop(block, b)) break;
            above++;
            checkPos = checkPos.above();
        }

        return below + above;
    }

    private boolean tryPlant(BlockPos pos, Block block) {
        if (!plant.get()) return false;
        if (!mc.level.isEmptyBlock(pos.above())) return false;
        if (!isFarmlandSoil(block) && !(block instanceof SoulSandBlock)) return false;

        FindItemResult findItemResult = null;

        if (onlyReplant.get()) {
            BlockPos cropPos = pos.above();
            if (replantMap.containsKey(cropPos)) {
                findItemResult = InvUtils.findInHotbar(replantMap.get(cropPos));
                if (findItemResult.found()) {
                    replantMap.remove(cropPos);
                }
            }
        } else {
            findItemResult = findPlantableItem(block);
        }

        if (findItemResult != null && findItemResult.found()) {
            performInteraction(pos.above(), findItemResult);
            actions++;
            return true;
        }

        return false;
    }

    private FindItemResult findPlantableItem(Block soilBlock) {
        if (isFarmlandSoil(soilBlock)) {
            return InvUtils.findInHotbar(stack -> {
                Item item = stack.getItem();
                return item != Items.NETHER_WART &&
                       item != Items.PITCHER_POD &&
                       plantItems.get().contains(item);
            });
        }

        if (soilBlock instanceof SoulSandBlock) {
            return InvUtils.findInHotbar(stack -> {
                Item item = stack.getItem();
                return item == Items.NETHER_WART && plantItems.get().contains(Items.NETHER_WART);
            });
        }

        return null;
    }


    private static boolean isFarmlandSoil(Block block) {
        if (block instanceof FarmlandBlock) return true;
        String name = block.getClass().getName();
        return name.endsWith(".FarmBlock") || name.endsWith(".FarmlandBlock");
    }

    private boolean tryBonemeal(BlockPos pos, BlockState state, Block block) {
        if (!bonemeal.get()) return false;
        if (!bonemealBlocks.get().contains(block)) return false;
        if (isMature(state, block)) return false;

        FindItemResult bonemealItem = InvUtils.findInHotbar(Items.BONE_MEAL);
        if (!bonemealItem.found()) return false;

        performInteraction(pos, bonemealItem);
        actions++;
        return true;
    }

    private void performInteraction(BlockPos pos, FindItemResult item) {
        Runnable action = () -> {
            boolean wasSneaking = mc.player.isShiftKeyDown();
            mc.player.setShiftKeyDown(false);
            InvUtils.swap(item.slot(), true);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            if (swingHand.get()) mc.player.swing(InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
            mc.player.setShiftKeyDown(wasSneaking);
        };

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), -100, action);
        } else {
            action.run();
        }
    }

    private boolean isWithinRange(BlockPos pos) {
        return getPlayerDistance(pos) <= range.get();
    }

    private double getPlayerDistance(BlockPos pos) {
        return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
    }

    private boolean isWaterNearby(LevelReader world, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (world.getFluidState(blockPos).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private void clearReplantMap() {
        replantMap.clear();
    }

    private void cleanupReplantMap() {
        if (mc.player == null || replantMap.isEmpty()) return;

        Vec3 playerPos = mc.player.position();

        replantMap.entrySet().removeIf(entry ->
            playerPos.distanceTo(Vec3.atCenterOf(entry.getKey())) > REPLANT_MAX_DISTANCE
        );
    }

    private boolean isMature(BlockState state, Block block) {
        if (state.is(BlockTags.CROPS)) {
            if (block instanceof CropBlock cropBlock) {
                return cropBlock.isMaxAge(state);
            }
        }

        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        } else if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= 2;
        } else if (block instanceof StemBlock) {
            return state.getValue(StemBlock.AGE) == StemBlock.MAX_AGE;
        } else if (block instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        } else if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        } else if (block instanceof PitcherCropBlock) {
            return state.getValue(PitcherCropBlock.AGE) >= 4;
        }

        return false;
    }

    private Item getCropSeed(Block block) {
        if (block == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (block == Blocks.CARROTS) return Items.CARROT;
        if (block == Blocks.POTATOES) return Items.POTATO;
        if (block == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (block == Blocks.NETHER_WART) return Items.NETHER_WART;
        if (block == Blocks.PITCHER_CROP) return Items.PITCHER_POD;
        if (block == Blocks.TORCHFLOWER) return Items.TORCHFLOWER_SEEDS;
        if (block == Blocks.TORCHFLOWER_CROP) return Items.TORCHFLOWER_SEEDS;
        return null;
    }

    private boolean bonemealFilter(Block block) {
        if (block instanceof CropBlock) return true;
        if (block instanceof StemBlock) return true;
        if (block instanceof SaplingBlock) return true;
        if (block instanceof MushroomBlock) return true;
        if (block instanceof AzaleaBlock) return true;

        return block == Blocks.COCOA ||
            block == Blocks.SWEET_BERRY_BUSH ||
            block == Blocks.PITCHER_CROP ||
            block == Blocks.TORCHFLOWER ||
            block == Blocks.TORCHFLOWER_CROP;
    }

    private boolean harvestFilter(Block block) {
        if (block instanceof CropBlock) return true;

        return block == Blocks.PUMPKIN ||
            block == Blocks.MELON ||
            block == Blocks.NETHER_WART ||
            block == Blocks.SWEET_BERRY_BUSH ||
            block == Blocks.COCOA ||
            block == Blocks.PITCHER_CROP ||
            block == Blocks.TORCHFLOWER;
    }

    private boolean plantFilter(Item item) {
        return item == Items.WHEAT_SEEDS ||
            item == Items.CARROT ||
            item == Items.POTATO ||
            item == Items.BEETROOT_SEEDS ||
            item == Items.PUMPKIN_SEEDS ||
            item == Items.MELON_SEEDS ||
            item == Items.NETHER_WART ||
            item == Items.PITCHER_POD ||
            item == Items.TORCHFLOWER_SEEDS;
    }
}
