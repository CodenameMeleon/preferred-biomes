package net.codenamemeleon.preferredbiomes.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class IslandSurface {

	private IslandSurface() {
	}

	private static final int BAND_BOTTOM = 62;

	private static final int BAND_TOP = 63;


	private static final int SAND_DEPTH = 2;

	public static final Identifier RED_SAND_NOISE_ID = Identifier.fromNamespaceAndPath("preferred-biomes", "red_sand");

	public static final ResourceKey<NormalNoise.NoiseParameters> RED_SAND_NOISE =
			ResourceKey.create(Registries.NOISE, RED_SAND_NOISE_ID);

	private static final double RED_SAND_THRESHOLD = 0.15;

	private static ResourceKey<Biome>[] shores(String island, String... bands) {
		@SuppressWarnings("unchecked")
		ResourceKey<Biome>[] keys = new ResourceKey[bands.length];
		for (int i = 0; i < bands.length; i++) {
			keys[i] = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("preferred-biomes",
					"island_shore_" + island + "_" + bands[i]));
		}
		return keys;
	}

	private static final ResourceKey<Biome>[] FROZEN_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_FROZEN);
	private static final ResourceKey<Biome>[] COLD_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_COLD);
	private static final ResourceKey<Biome>[] TEMPERATE_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_TEMPERATE);
	private static final ResourceKey<Biome>[] LUKEWARM_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_LUKEWARM);
	private static final ResourceKey<Biome>[] WARM_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_WARM);

	private static final ResourceKey<Biome>[] DESERT_SHORES = shores("desert_island", "warm");

	private static final ResourceKey<Biome>[] MUSHROOM_SHORES = shores("mushroom_fields",
			"frozen", "cold", "temperate", "lukewarm", "warm");

	private static final ResourceKey<Biome>[] MANGROVE_SHORES = shores("mangrove_island", "warm");

	private static final ResourceKey<Biome>[] LUSH_SHORES = shores("lush_island", "warm");

	private static final ResourceKey<Biome>[] ICE_SPIKE_SHORES =
			shores("ice_spikes_island", "frozen");

	public static SurfaceRules.RuleSource rule(SurfaceRules.RuleSource vanilla,
			int size, float frequency, int noise) {
		SurfaceRules.ConditionSource nearWater =
				new IslandShoreCondition(size, frequency, noise);
		SurfaceRules.ConditionSource atWaterline =
				SurfaceRules.yBlockCheck(VerticalAnchor.absolute(BAND_BOTTOM), 0);
		SurfaceRules.ConditionSource belowBandTop =
				SurfaceRules.not(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(BAND_TOP), 0));
		SurfaceRules.ConditionSource depth =
				SurfaceRules.stoneDepthCheck(0, true, SAND_DEPTH, CaveSurface.FLOOR);
		SurfaceRules.ConditionSource redSand =
				SurfaceRules.noiseCondition(RED_SAND_NOISE, RED_SAND_THRESHOLD);

		SurfaceRules.RuleSource redDesert = SurfaceRules.sequence(
				SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR,
						SurfaceRules.sequence(
								SurfaceRules.ifTrue(SurfaceRules.ON_CEILING,
										SurfaceRules.state(Blocks.RED_SANDSTONE.defaultBlockState())),
								SurfaceRules.state(Blocks.RED_SAND.defaultBlockState()))),
				SurfaceRules.ifTrue(SurfaceRules.VERY_DEEP_UNDER_FLOOR,
						SurfaceRules.state(Blocks.RED_SANDSTONE.defaultBlockState())));

		SurfaceRules.RuleSource redDesertIsland = SurfaceRules.ifTrue(
				SurfaceRules.isBiome(join(DESERT_SHORES, IslandBiomes.DESERT_ISLAND)),
				SurfaceRules.ifTrue(redSand, redDesert));

		SurfaceRules.RuleSource desertIsland = SurfaceRules.ifTrue(
				SurfaceRules.isBiome(join(DESERT_SHORES, IslandBiomes.DESERT_ISLAND)),
				SurfaceRules.sequence(
						SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
								SurfaceRules.ifTrue(SurfaceRules.waterBlockCheck(-1, 0),
										SurfaceRules.sequence(
												SurfaceRules.ifTrue(SurfaceRules.ON_CEILING,
														SurfaceRules.state(Blocks.SANDSTONE.defaultBlockState())),
												SurfaceRules.state(Blocks.SAND.defaultBlockState())))),
						SurfaceRules.ifTrue(SurfaceRules.waterStartCheck(-6, -1),
								SurfaceRules.ifTrue(
										SurfaceRules.VERY_DEEP_UNDER_FLOOR,
										SurfaceRules.state(Blocks.SANDSTONE.defaultBlockState())))));

		SurfaceRules.RuleSource mangroveIsland = SurfaceRules.ifTrue(
				SurfaceRules.isBiome(join(MANGROVE_SHORES, IslandBiomes.MANGROVE_ISLAND)),
				SurfaceRules.sequence(
						SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
								SurfaceRules.ifTrue(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(60), 0),
										SurfaceRules.ifTrue(SurfaceRules.not(
												SurfaceRules.yBlockCheck(VerticalAnchor.absolute(63), 0)),
												SurfaceRules.ifTrue(
														SurfaceRules.noiseCondition(
																Noises.SWAMP, 0.0),
														SurfaceRules.state(Blocks.WATER.defaultBlockState()))))),
						SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
								SurfaceRules.ifTrue(SurfaceRules.waterBlockCheck(-1, 0),
										SurfaceRules.state(Blocks.MUD.defaultBlockState()))),
						SurfaceRules.ifTrue(SurfaceRules.waterStartCheck(-6, -1),
								SurfaceRules.ifTrue(
										SurfaceRules.UNDER_FLOOR,
										SurfaceRules.state(Blocks.MUD.defaultBlockState())))));

		SurfaceRules.RuleSource lushLand = SurfaceRules.ifTrue(
				SurfaceRules.isBiome(join(LUSH_SHORES, IslandBiomes.LUSH_ISLAND)),
				SurfaceRules.ifTrue(SurfaceRules.yBlockCheck(VerticalAnchor.absolute(63), 0),
						SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR,
								SurfaceRules.state(Blocks.STONE.defaultBlockState()))));

		SurfaceRules.RuleSource submergedFloor = submergedFloor();

		SurfaceRules.RuleSource shore = SurfaceRules.ifTrue(atWaterline,
				SurfaceRules.ifTrue(belowBandTop,
						SurfaceRules.ifTrue(nearWater,
								SurfaceRules.ifTrue(depth,
										SurfaceRules.sequence(
												ring(join(LUSH_SHORES, IslandBiomes.LUSH_ISLAND), Blocks.MOSS_BLOCK),
												ring(join(MUSHROOM_SHORES, Biomes.MUSHROOM_FIELDS), Blocks.MYCELIUM),
												ring(join(MANGROVE_SHORES, IslandBiomes.MANGROVE_ISLAND), Blocks.MUD),
												ring(join(ICE_SPIKE_SHORES, IslandBiomes.ICE_SPIKES_ISLAND), Blocks.SNOW_BLOCK),
												SurfaceRules.ifTrue(
														SurfaceRules.isBiome(join(DESERT_SHORES, IslandBiomes.DESERT_ISLAND)),
														SurfaceRules.ifTrue(redSand,
																SurfaceRules.state(Blocks.RED_SAND.defaultBlockState()))),
												SurfaceRules.state(Blocks.SAND.defaultBlockState()))))));

		return SurfaceRules.sequence(
				submergedFloor, redDesertIsland, desertIsland, mangroveIsland, lushLand,
				shore, vanilla);
	}

	private static SurfaceRules.RuleSource submergedFloor() {
		SurfaceRules.ConditionSource submerged =
				SurfaceRules.not(SurfaceRules.waterBlockCheck(-1, 0));
		SurfaceRules.ConditionSource anyShore = SurfaceRules.isBiome(
				join(FROZEN_SHORES, join(COLD_SHORES, join(TEMPERATE_SHORES,
						join(LUKEWARM_SHORES, WARM_SHORES)))));
		return SurfaceRules.ifTrue(anyShore, SurfaceRules.ifTrue(submerged,
				SurfaceRules.sequence(
						SurfaceRules.ifTrue(SurfaceRules.waterStartCheck(-6, -1),
								SurfaceRules.ifTrue(SurfaceRules.isBiome(WARM_SHORES),
										SurfaceRules.ifTrue(
												SurfaceRules.stoneDepthCheck(0, true, 6, CaveSurface.FLOOR),
												SurfaceRules.state(Blocks.SANDSTONE.defaultBlockState())))),
						SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
								SurfaceRules.sequence(
										SurfaceRules.ifTrue(
												SurfaceRules.isBiome(join(WARM_SHORES, LUKEWARM_SHORES)),
												SurfaceRules.sequence(
														SurfaceRules.ifTrue(SurfaceRules.ON_CEILING,
																SurfaceRules.state(Blocks.SANDSTONE.defaultBlockState())),
														SurfaceRules.state(Blocks.SAND.defaultBlockState()))),
										SurfaceRules.sequence(
												SurfaceRules.ifTrue(SurfaceRules.ON_CEILING,
														SurfaceRules.state(Blocks.STONE.defaultBlockState())),
												SurfaceRules.state(Blocks.GRAVEL.defaultBlockState())))))));
	}

	private static SurfaceRules.RuleSource ring(ResourceKey<Biome>[] biomes, Block block) {
		return SurfaceRules.ifTrue(SurfaceRules.isBiome(biomes),
				SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
						SurfaceRules.state(block.defaultBlockState())));
	}

	@SafeVarargs
	private static ResourceKey<Biome>[] join(ResourceKey<Biome>[] first, ResourceKey<Biome>... rest) {
		ResourceKey<Biome>[] all = java.util.Arrays.copyOf(first, first.length + rest.length);
		System.arraycopy(rest, 0, all, first.length, rest.length);
		return all;
	}
}
