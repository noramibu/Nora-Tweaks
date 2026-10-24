/*
 * This code partially adapted from Meteor Rejects
 * Original source: https://github.com/AntiCope/meteor-rejects/
 * Credit: Meteor Rejects contributors
 * If Meteor Rejects gets updated, adapted features will get removed.
 */
package me.noramibu.tweaks.utils;

import me.noramibu.tweaks.mixin.CountPlacementModifierAccessor;
import me.noramibu.tweaks.mixin.HeightRangePlacementModifierAccessor;
import me.noramibu.tweaks.mixin.RarityFilterPlacementModifierAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.util.*;

public class Ore {
    private static final Setting<Boolean> coal = new BoolSetting.Builder().name("Coal").build();
    private static final Setting<Boolean> iron = new BoolSetting.Builder().name("Iron").build();
    private static final Setting<Boolean> gold = new BoolSetting.Builder().name("Gold").build();
    private static final Setting<Boolean> redstone = new BoolSetting.Builder().name("Redstone").build();
    private static final Setting<Boolean> diamond = new BoolSetting.Builder().name("Diamond").build();
    private static final Setting<Boolean> lapis = new BoolSetting.Builder().name("Lapis").build();
    private static final Setting<Boolean> copper = new BoolSetting.Builder().name("Copper").build();
    private static final Setting<Boolean> emerald = new BoolSetting.Builder().name("Emerald").build();
    private static final Setting<Boolean> quartz = new BoolSetting.Builder().name("Quartz").build();
    private static final Setting<Boolean> debris = new BoolSetting.Builder().name("Ancient Debris").build();

    public static final List<Setting<Boolean>> oreSettings = List.of(
        coal, iron, gold, redstone, diamond, lapis, copper, emerald, quartz, debris
    );

    public int step;
    public int index;
    public Setting<Boolean> active;
    public IntProvider count = ConstantInt.of(1);
    public HeightProvider heightProvider;
    public WorldGenerationContext heightContext;
    public float rarity = 1.0F;
    public float discardOnAirChance;
    public int size;
    public Color color;
    public boolean scattered;

    private Ore(PlacedFeature feature, int step, int index, Setting<Boolean> active, Color color, WorldGenerationContext heightContext) {
        this.step = step;
        this.index = index;
        this.active = active;
        this.color = color;
        this.heightContext = heightContext;

        for (PlacementModifier modifier : feature.placement()) {
            if (modifier instanceof CountPlacement countPlacement) {
                this.count = ((CountPlacementModifierAccessor) (Object) countPlacement).getCount();
            } else if (modifier instanceof HeightRangePlacement heightRange) {
                this.heightProvider = ((HeightRangePlacementModifierAccessor) (Object) heightRange).getHeight();
            } else if (modifier instanceof RarityFilter rarityFilter) {
                this.rarity = ((RarityFilterPlacementModifierAccessor) (Object) rarityFilter).getChance();
            }
        }

        Feature rawFeature = feature.feature().value();
        if (rawFeature instanceof AbstractOreFeature oreFeature) {
            this.discardOnAirChance = oreFeature.discardChanceOnAirExposure();
            this.size = oreFeature.size();
        } else {
            throw new IllegalStateException("Feature for " + feature + " is not an AbstractOreFeature");
        }

        if (rawFeature instanceof ScatteredOreFeature) {
            this.scattered = true;
        }
    }

    public static Map<ResourceKey<Biome>, List<Ore>> getRegistry(Dimension dimension) {
        HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
        HolderLookup.RegistryLookup<PlacedFeature> features = lookup.lookupOrThrow(Registries.PLACED_FEATURE);
        var dimensionMap = lookup.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions().dimensions();

        var dimensionOptions = switch (dimension) {
            case Overworld -> dimensionMap.get(LevelStem.OVERWORLD);
            case Nether -> dimensionMap.get(LevelStem.NETHER);
            case End -> dimensionMap.get(LevelStem.END);
        };

        var biomes = new ArrayList<>(dimensionOptions.generator().getBiomeSource().possibleBiomes());

        // Build a valid HeightContext using the target dimension's chunk generator and current world's height limits
        WorldGenerationContext heightContext;
        if (Minecraft.getInstance().level != null) {
            int bottom = Minecraft.getInstance().level.getMinY();
            int logical = Minecraft.getInstance().level.dimensionType().logicalHeight();
            heightContext = new WorldGenerationContext(dimensionOptions.generator(), LevelHeightAccessor.create(bottom, logical));
        } else {
            // Fallback safe defaults matching typical world limits; avoids NPE before a world is loaded
            heightContext = new WorldGenerationContext(dimensionOptions.generator(), LevelHeightAccessor.create(-64, 384));
        }

        List<FeatureSorter.StepFeatureData> indexer = FeatureSorter.buildFeaturesPerStep(
            biomes,
            biomeEntry -> biomeEntry.value().getGenerationSettings().features(),
            true
        );

        Map<PlacedFeature, Ore> featureToOre = new HashMap<>();
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COAL_LOWER, 6, coal, new Color(47, 44, 54), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COAL_UPPER, 6, coal, new Color(47, 44, 54), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_IRON_MIDDLE, 6, iron, new Color(236, 173, 119), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_IRON_SMALL, 6, iron, new Color(236, 173, 119), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_IRON_UPPER, 6, iron, new Color(236, 173, 119), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD, 6, gold, new Color(247, 229, 30), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_LOWER, 6, gold, new Color(247, 229, 30), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_EXTRA, 6, gold, new Color(247, 229, 30), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_NETHER, 7, gold, new Color(247, 229, 30), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_DELTAS, 7, gold, new Color(247, 229, 30), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_REDSTONE, 6, redstone, new Color(245, 7, 23), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_REDSTONE_LOWER, 6, redstone, new Color(245, 7, 23), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND, 6, diamond, new Color(33, 244, 255), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND_BURIED, 6, diamond, new Color(33, 244, 255), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND_LARGE, 6, diamond, new Color(33, 244, 255), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND_MEDIUM, 6, diamond, new Color(33, 244, 255), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_LAPIS, 6, lapis, new Color(8, 26, 189), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_LAPIS_BURIED, 6, lapis, new Color(8, 26, 189), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COPPER, 6, copper, new Color(239, 151, 0), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COPPER_LARGE, 6, copper, new Color(239, 151, 0), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_EMERALD, 6, emerald, new Color(27, 209, 45), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_QUARTZ_NETHER, 7, quartz, new Color(205, 205, 205), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_QUARTZ_DELTAS, 7, quartz, new Color(205, 205, 205), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_ANCIENT_DEBRIS_SMALL, 7, debris, new Color(209, 27, 245), heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_ANCIENT_DEBRIS_LARGE, 7, debris, new Color(209, 27, 245), heightContext);

        Map<ResourceKey<Biome>, List<Ore>> biomeOreMap = new HashMap<>();
        for (Holder<Biome> biome : biomes) {
            biomeOreMap.put(biome.unwrapKey().get(), new ArrayList<>());
            biome.value().getGenerationSettings().features().stream()
                .flatMap(HolderSet::stream)
                .map(Holder::value)
                .filter(featureToOre::containsKey)
                .forEach(feature -> biomeOreMap.get(biome.unwrapKey().get()).add(featureToOre.get(feature)));
        }

        return biomeOreMap;
    }

    private static void registerOre(
        Map<PlacedFeature, Ore> map,
        List<FeatureSorter.StepFeatureData> indexer,
        HolderLookup.RegistryLookup<PlacedFeature> oreRegistry,
        ResourceKey<PlacedFeature> oreKey,
        int genStep,
        Setting<Boolean> active,
        Color color,
        WorldGenerationContext heightContext
    ) {
        PlacedFeature placedFeature = oreRegistry.getOrThrow(oreKey).value();
        int idx = indexer.get(genStep).indexMapping().applyAsInt(placedFeature);
        map.put(placedFeature, new Ore(placedFeature, genStep, idx, active, color, heightContext));
    }
}
