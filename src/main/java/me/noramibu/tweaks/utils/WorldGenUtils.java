/*
 * This code partially adapted from Meteor Rejects
 * Original source: https://github.com/AntiCope/meteor-rejects/
 * Credit: Meteor Rejects contributors
 * If Meteor Rejects gets updated, adapted features will get removed.
 */
package me.noramibu.tweaks.utils;

import dev.xpple.cubiomes.Cubiomes;
import dev.xpple.cubiomes.CubiomesInit;
import dev.xpple.cubiomes.Generator;
import dev.xpple.cubiomes.StrongholdIter;
import dev.xpple.cubiomes.StructureConfig;
import me.noramibu.tweaks.utils.Seeds.Seed;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class WorldGenUtils {
    private static final Logger LOG = LogManager.getLogger();
    private static final int MAX_SEARCH_RADIUS_REGIONS = 2048;
    private static final boolean CUBIOMES_NATIVE_LOADED = loadCubiomesNative();

    private static final Map<Feature, List<Class<? extends Entity>>> FEATURE_ENTITIES = new HashMap<>() {{
        put(Feature.ocean_monument, Arrays.asList(ElderGuardian.class, Guardian.class));
        put(Feature.nether_fortress, Arrays.asList(Blaze.class, WitherSkeleton.class));
        put(Feature.mansion, Collections.singletonList(Evoker.class));
        put(Feature.slime_chunk, Collections.singletonList(Slime.class));
        put(Feature.bastion_remnant, Collections.singletonList(PiglinBrute.class));
        put(Feature.end_city, Collections.singletonList(Shulker.class));
        put(Feature.village, Arrays.asList(Villager.class, IronGolem.class));
        put(Feature.mineshaft, Collections.singletonList(MinecartChest.class));
    }};

    private static final Map<String, Structure> STRUCTURES_BY_NAME = new HashMap<>();

    static {
        for (Structure structure : Structure.values()) {
            STRUCTURES_BY_NAME.put(structure.commandName, structure);
        }
    }

    public enum Feature {
        buried_treasure,
        mansion,
        stronghold,
        nether_fortress,
        ocean_monument,
        bastion_remnant,
        end_city,
        village,
        mineshaft,
        slime_chunk,
        desert_pyramid
    }

    public enum Structure {
        FEATURE("feature", Cubiomes.Feature(), null),
        DESERT_PYRAMID("desert_pyramid", Cubiomes.Desert_Pyramid(), Feature.desert_pyramid),
        JUNGLE_TEMPLE("jungle_temple", Cubiomes.Jungle_Temple(), null),
        SWAMP_HUT("swamp_hut", Cubiomes.Swamp_Hut(), null),
        IGLOO("igloo", Cubiomes.Igloo(), null),
        VILLAGE("village", Cubiomes.Village(), Feature.village),
        OCEAN_RUIN("ocean_ruin", Cubiomes.Ocean_Ruin(), null),
        SHIPWRECK("shipwreck", Cubiomes.Shipwreck(), null),
        MONUMENT("monument", Cubiomes.Monument(), Feature.ocean_monument),
        MANSION("mansion", Cubiomes.Mansion(), Feature.mansion),
        OUTPOST("outpost", Cubiomes.Outpost(), null),
        RUINED_PORTAL("ruined_portal", Cubiomes.Ruined_Portal(), null),
        RUINED_PORTAL_N("ruined_portal_n", Cubiomes.Ruined_Portal_N(), null),
        ANCIENT_CITY("ancient_city", Cubiomes.Ancient_City(), null),
        TREASURE("treasure", Cubiomes.Treasure(), Feature.buried_treasure),
        MINESHAFT("mineshaft", Cubiomes.Mineshaft(), Feature.mineshaft),
        DESERT_WELL("desert_well", Cubiomes.Desert_Well(), null),
        GEODE("geode", Cubiomes.Geode(), null),
        FORTRESS("fortress", Cubiomes.Fortress(), Feature.nether_fortress),
        BASTION("bastion", Cubiomes.Bastion(), Feature.bastion_remnant),
        END_CITY("end_city", Cubiomes.End_City(), Feature.end_city),
        END_GATEWAY("end_gateway", Cubiomes.End_Gateway(), null),
        TRAIL_RUIN("trail_ruin", Cubiomes.Trail_Ruins(), null),
        SLIME_CHUNK("slime_chunk", -1, Feature.slime_chunk),
        STRONGHOLD("stronghold", Cubiomes.Stronghold(), Feature.stronghold);

        public final String commandName;
        public final int nativeId;
        public final Feature fallbackFeature;

        Structure(String commandName, int nativeId, Feature fallbackFeature) {
            this.commandName = commandName;
            this.nativeId = nativeId;
            this.fallbackFeature = fallbackFeature;
        }
    }

    public static List<String> structureNames() {
        return Arrays.stream(Structure.values())
            .map(structure -> structure.commandName)
            .toList();
    }

    public static Structure parseStructure(String input) {
        if (input == null) return null;
        return STRUCTURES_BY_NAME.get(input.trim().toLowerCase(Locale.ROOT));
    }

    public static BlockPos locateFeature(Structure structure, BlockPos center) {
        if (structure == null || center == null) return null;

        Seed seed = Seeds.get().getSeed();
        if (seed != null) {
            try {
                BlockPos located = locateNearestStructure(structure, center, seed);
                if (located != null) return located;
            } catch (Exception | Error ex) {
                LOG.error("Failed to locate structure via seed", ex);
            }
        }

        Feature feature = structure.fallbackFeature;
        if (feature == null) return null;
        if (!isInDimension(getDimension(feature))) return null;

        if (mc.player != null) {
            ItemStack stack = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
            if (stack.isEmpty()) stack = mc.player.getItemInHand(InteractionHand.OFF_HAND);
            if (!stack.isEmpty()) {
                try {
                    BlockPos mapPos = locateFeatureMap(feature, stack);
                    if (mapPos != null) return mapPos;
                } catch (Exception | Error ex) {
                    LOG.error("Failed to locate feature via map", ex);
                }
            }
        }

        try {
            return locateFeatureEntities(feature);
        } catch (Exception | Error ex) {
            LOG.error("Failed to locate feature via entities", ex);
        }

        return null;
    }

    public static BlockPos locateNearestStructure(Structure structure, BlockPos center, Seed seed) {
        if (structure == null || center == null || seed == null) return null;
        return locateNearestStructure(structure.nativeId, center.getX(), center.getZ(), seed.seed, seed.cubiomesVersionId());
    }

    private static BlockPos locateNearestStructure(int structureId, int x, int z, long seed, int mcVersion) {
        if (structureId < 0) return null;
        if (!CUBIOMES_NATIVE_LOADED) return null;

        try {
            if (structureId == Cubiomes.Stronghold()) {
                return locateNearestStronghold(x, z, seed, mcVersion);
            }
            return locateNearestRegionStructure(structureId, x, z, seed, mcVersion);
        } catch (Throwable t) {
            LOG.debug("Cubiomes nearest structure lookup failed for structure {}.", structureId, t);
            return null;
        }
    }

    private static boolean loadCubiomesNative() {
        try {
            CubiomesInit.load();
            return true;
        } catch (Throwable t) {
            LOG.warn("Failed to load xpple cubiomes native library.", t);
            return false;
        }
    }

    private static BlockPos locateNearestStronghold(int x, int z, long seed, int mcVersion) {
        BlockPos nearest = null;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment strongholdIter = StrongholdIter.allocate(arena);
            Cubiomes.initFirstStronghold(arena, strongholdIter, mcVersion, seed);

            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, mcVersion, 0);
            Cubiomes.applySeed(generator, Cubiomes.DIM_OVERWORLD(), seed);

            int count = mcVersion <= Cubiomes.MC_1_8() ? 3 : 128;
            for (int i = 0; i < count; i++) {
                if (Cubiomes.nextStronghold(strongholdIter, generator) == 0) break;

                MemorySegment pos = StrongholdIter.pos(strongholdIter);
                int sx = dev.xpple.cubiomes.Pos.x(pos);
                int sz = dev.xpple.cubiomes.Pos.z(pos);
                double distanceSq = distSq(x, z, sx, sz);
                if (distanceSq < nearestDistanceSq) {
                    nearestDistanceSq = distanceSq;
                    nearest = new BlockPos(sx, 0, sz);
                }
            }
        }

        return nearest;
    }

    private static BlockPos locateNearestRegionStructure(int structureId, int x, int z, long seed, int mcVersion) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment structureConfig = StructureConfig.allocate(arena);
            if (Cubiomes.getStructureConfig(structureId, mcVersion, structureConfig) == 0) {
                return null;
            }

            int regionSizeBlocks = StructureConfig.regionSize(structureConfig) << 4;
            if (regionSizeBlocks <= 0) return null;

            int centerRegionX = Math.floorDiv(x, regionSizeBlocks);
            int centerRegionZ = Math.floorDiv(z, regionSizeBlocks);
            int dimension = StructureConfig.dim(structureConfig);

            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, mcVersion, 0);
            Cubiomes.applySeed(generator, dimension, seed);

            MemorySegment structurePos = dev.xpple.cubiomes.Pos.allocate(arena);
            Coordinate found = spiral(centerRegionX, centerRegionZ, MAX_SEARCH_RADIUS_REGIONS, (regionX, regionZ) -> {
                if (Cubiomes.getStructurePos(structureId, mcVersion, seed, regionX, regionZ, structurePos) == 0) {
                    return false;
                }

                int sx = dev.xpple.cubiomes.Pos.x(structurePos);
                int sz = dev.xpple.cubiomes.Pos.z(structurePos);
                if (Cubiomes.isViableStructurePos(structureId, generator, sx, sz, 0) == 0) {
                    return false;
                }
                return Cubiomes.isViableStructureTerrain(structureId, generator, sx, sz) != 0;
            });

            if (found == null) return null;
            return new BlockPos(dev.xpple.cubiomes.Pos.x(structurePos), 0, dev.xpple.cubiomes.Pos.z(structurePos));
        }
    }

    private static double distSq(int x1, int z1, int x2, int z2) {
        double dx = (double) x1 - x2;
        double dz = (double) z1 - z2;
        return dx * dx + dz * dz;
    }

    private static Coordinate spiral(int centerX, int centerZ, int radius, CoordinateCallback callback) {
        int x = centerX;
        int z = centerZ;
        int dx = 0;
        int dz = -1;

        long max = (2L * radius + 1L) * (2L * radius + 1L);
        for (long i = 0; i < max; i++) {
            if (Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius) {
                if (callback.consume(x, z)) return new Coordinate(x, z);
            }

            if ((x - centerX) == (z - centerZ)
                || ((x - centerX) < 0 && (x - centerX) == -(z - centerZ))
                || ((x - centerX) > 0 && (x - centerX) == 1 - (z - centerZ))) {
                int tmp = dx;
                dx = -dz;
                dz = tmp;
            }

            x += dx;
            z += dz;
        }

        return null;
    }

    private record Coordinate(int x, int z) {}

    @FunctionalInterface
    private interface CoordinateCallback {
        boolean consume(int x, int z);
    }

    private static BlockPos locateFeatureMap(Feature feature, ItemStack stack) {
        if (!isValidMap(feature, stack)) return null;
        return getMapMarker(stack);
    }

    private static BlockPos locateFeatureEntities(Feature feature) {
        List<Class<? extends Entity>> entities = FEATURE_ENTITIES.get(feature);
        if (entities == null || mc.level == null) return null;

        for (Entity entity : mc.level.entitiesForRendering()) {
            for (Class<? extends Entity> clazz : entities) {
                if (clazz.isInstance(entity)) return entity.blockPosition();
            }
        }
        return null;
    }

    private static boolean isInDimension(meteordevelopment.meteorclient.utils.world.Dimension dimension) {
        return PlayerUtils.getDimension() == dimension;
    }

    private static meteordevelopment.meteorclient.utils.world.Dimension getDimension(Feature feature) {
        return switch (feature) {
            case nether_fortress, bastion_remnant -> meteordevelopment.meteorclient.utils.world.Dimension.Nether;
            case end_city -> meteordevelopment.meteorclient.utils.world.Dimension.End;
            default -> meteordevelopment.meteorclient.utils.world.Dimension.Overworld;
        };
    }

    private static boolean isValidMap(Feature feature, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!stack.getComponents().has(DataComponents.MAP_DECORATIONS)) return false;
        MapDecorations component = stack.get(DataComponents.MAP_DECORATIONS);
        if (component == null || component.decorations().isEmpty()) return false;
        String name = component.toString();
        if (!name.contains("translate")) return false;
        return switch (feature) {
            case buried_treasure -> name.contains("filled_map.buried_treasure");
            case ocean_monument -> name.contains("filled_map.monument");
            case mansion -> name.contains("filled_map.mansion");
            default -> false;
        };
    }

    private static BlockPos getMapMarker(ItemStack stack) {
        if (!stack.getComponents().has(DataComponents.MAP_DECORATIONS)) return null;
        MapDecorations component = stack.get(DataComponents.MAP_DECORATIONS);
        if (component == null || component.decorations().isEmpty()) return null;
        MapDecorations.Entry decoration = component.decorations().get(0);
        return new BlockPos((int) decoration.x(), 0, (int) decoration.z());
    }
}
