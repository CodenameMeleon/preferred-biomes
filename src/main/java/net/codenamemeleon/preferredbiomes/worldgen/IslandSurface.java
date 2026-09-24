package net.codenamemeleon.preferredbiomes.worldgen;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.VerticalSurfaceType;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.YOffset;
import net.minecraft.world.gen.noise.NoiseParametersKeys;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

public final class IslandSurface {

	private IslandSurface() {
	}

	private static final int BAND_BOTTOM = 62;

	private static final int BAND_TOP = 63;


	private static final int SAND_DEPTH = 2;

	public static final Identifier RED_SAND_NOISE_ID = new Identifier("preferred-biomes", "red_sand");

	public static final RegistryKey<DoublePerlinNoiseSampler.NoiseParameters> RED_SAND_NOISE =
			RegistryKey.of(RegistryKeys.NOISE_PARAMETERS, RED_SAND_NOISE_ID);

	private static final double RED_SAND_THRESHOLD = 0.15;

	private static RegistryKey<Biome>[] shores(String island, String... bands) {
		@SuppressWarnings("unchecked")
		RegistryKey<Biome>[] keys = new RegistryKey[bands.length];
		for (int i = 0; i < bands.length; i++) {
			keys[i] = RegistryKey.of(RegistryKeys.BIOME, new Identifier("preferred-biomes",
					"island_shore_" + island + "_" + bands[i]));
		}
		return keys;
	}

	private static final RegistryKey<Biome>[] FROZEN_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_FROZEN);
	private static final RegistryKey<Biome>[] COLD_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_COLD);
	private static final RegistryKey<Biome>[] TEMPERATE_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_TEMPERATE);
	private static final RegistryKey<Biome>[] LUKEWARM_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_LUKEWARM);
	private static final RegistryKey<Biome>[] WARM_SHORES =
			IslandBiomes.shoresInBand(IslandBiomes.BAND_WARM);

	private static final RegistryKey<Biome>[] DESERT_SHORES = shores("desert_island", "warm");

	private static final RegistryKey<Biome>[] MUSHROOM_SHORES = shores("mushroom_fields",
			"frozen", "cold", "temperate", "lukewarm", "warm");

	private static final RegistryKey<Biome>[] MANGROVE_SHORES = shores("mangrove_island", "warm");

	private static final RegistryKey<Biome>[] LUSH_SHORES = shores("lush_island", "warm");

	private static final RegistryKey<Biome>[] ICE_SPIKE_SHORES =
			shores("ice_spikes_island", "frozen");

	public static MaterialRules.MaterialRule rule(MaterialRules.MaterialRule vanilla,
			int size, float frequency, int noise) {
		MaterialRules.MaterialCondition nearWater =
				new IslandShoreCondition(size, frequency, noise);
		MaterialRules.MaterialCondition atWaterline =
				MaterialRules.aboveY(YOffset.fixed(BAND_BOTTOM), 0);
		MaterialRules.MaterialCondition belowBandTop =
				MaterialRules.not(MaterialRules.aboveY(YOffset.fixed(BAND_TOP), 0));
		MaterialRules.MaterialCondition depth =
				MaterialRules.stoneDepth(0, true, SAND_DEPTH, VerticalSurfaceType.FLOOR);
		MaterialRules.MaterialCondition redSand =
				MaterialRules.noiseThreshold(RED_SAND_NOISE, RED_SAND_THRESHOLD);

		MaterialRules.MaterialRule redDesert = MaterialRules.sequence(
				MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH,
						MaterialRules.sequence(
								MaterialRules.condition(MaterialRules.STONE_DEPTH_CEILING,
										MaterialRules.block(Blocks.RED_SANDSTONE.getDefaultState())),
								MaterialRules.block(Blocks.RED_SAND.getDefaultState()))),
				MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH_RANGE_30,
						MaterialRules.block(Blocks.RED_SANDSTONE.getDefaultState())));

		MaterialRules.MaterialRule redDesertIsland = MaterialRules.condition(
				MaterialRules.biome(join(DESERT_SHORES, IslandBiomes.DESERT_ISLAND)),
				MaterialRules.condition(redSand, redDesert));

		MaterialRules.MaterialRule desertIsland = MaterialRules.condition(
				MaterialRules.biome(join(DESERT_SHORES, IslandBiomes.DESERT_ISLAND)),
				MaterialRules.sequence(
						MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR,
								MaterialRules.condition(MaterialRules.water(-1, 0),
										MaterialRules.sequence(
												MaterialRules.condition(MaterialRules.STONE_DEPTH_CEILING,
														MaterialRules.block(Blocks.SANDSTONE.getDefaultState())),
												MaterialRules.block(Blocks.SAND.getDefaultState())))),
						MaterialRules.condition(MaterialRules.waterWithStoneDepth(-6, -1),
								MaterialRules.condition(
										MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH_RANGE_30,
										MaterialRules.block(Blocks.SANDSTONE.getDefaultState())))));

		MaterialRules.MaterialRule mangroveIsland = MaterialRules.condition(
				MaterialRules.biome(join(MANGROVE_SHORES, IslandBiomes.MANGROVE_ISLAND)),
				MaterialRules.sequence(
						MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR,
								MaterialRules.condition(MaterialRules.aboveY(YOffset.fixed(60), 0),
										MaterialRules.condition(MaterialRules.not(
												MaterialRules.aboveY(YOffset.fixed(63), 0)),
												MaterialRules.condition(
														MaterialRules.noiseThreshold(
																NoiseParametersKeys.SURFACE_SWAMP, 0.0),
														MaterialRules.block(Blocks.WATER.getDefaultState()))))),
						MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR,
								MaterialRules.condition(MaterialRules.water(-1, 0),
										MaterialRules.block(Blocks.MUD.getDefaultState()))),
						MaterialRules.condition(MaterialRules.waterWithStoneDepth(-6, -1),
								MaterialRules.condition(
										MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH,
										MaterialRules.block(Blocks.MUD.getDefaultState())))));

		MaterialRules.MaterialRule lushLand = MaterialRules.condition(
				MaterialRules.biome(join(LUSH_SHORES, IslandBiomes.LUSH_ISLAND)),
				MaterialRules.condition(MaterialRules.aboveY(YOffset.fixed(63), 0),
						MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH,
								MaterialRules.block(Blocks.STONE.getDefaultState()))));

		MaterialRules.MaterialRule submergedFloor = submergedFloor();

		MaterialRules.MaterialRule shore = MaterialRules.condition(atWaterline,
				MaterialRules.condition(belowBandTop,
						MaterialRules.condition(nearWater,
								MaterialRules.condition(depth,
										MaterialRules.sequence(
												ring(join(LUSH_SHORES, IslandBiomes.LUSH_ISLAND), Blocks.MOSS_BLOCK),
												ring(join(MUSHROOM_SHORES, BiomeKeys.MUSHROOM_FIELDS), Blocks.MYCELIUM),
												ring(join(MANGROVE_SHORES, IslandBiomes.MANGROVE_ISLAND), Blocks.MUD),
												ring(join(ICE_SPIKE_SHORES, IslandBiomes.ICE_SPIKES_ISLAND), Blocks.SNOW_BLOCK),
												MaterialRules.condition(
														MaterialRules.biome(join(DESERT_SHORES, IslandBiomes.DESERT_ISLAND)),
														MaterialRules.condition(redSand,
																MaterialRules.block(Blocks.RED_SAND.getDefaultState()))),
												MaterialRules.block(Blocks.SAND.getDefaultState()))))));

		return MaterialRules.sequence(
				submergedFloor, redDesertIsland, desertIsland, mangroveIsland, lushLand,
				shore, vanilla);
	}

	private static MaterialRules.MaterialRule submergedFloor() {
		MaterialRules.MaterialCondition submerged =
				MaterialRules.not(MaterialRules.water(-1, 0));
		MaterialRules.MaterialCondition anyShore = MaterialRules.biome(
				join(FROZEN_SHORES, join(COLD_SHORES, join(TEMPERATE_SHORES,
						join(LUKEWARM_SHORES, WARM_SHORES)))));
		return MaterialRules.condition(anyShore, MaterialRules.condition(submerged,
				MaterialRules.sequence(
						MaterialRules.condition(MaterialRules.waterWithStoneDepth(-6, -1),
								MaterialRules.condition(MaterialRules.biome(WARM_SHORES),
										MaterialRules.condition(
												MaterialRules.stoneDepth(0, true, 6, VerticalSurfaceType.FLOOR),
												MaterialRules.block(Blocks.SANDSTONE.getDefaultState())))),
						MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR,
								MaterialRules.sequence(
										MaterialRules.condition(
												MaterialRules.biome(join(WARM_SHORES, LUKEWARM_SHORES)),
												MaterialRules.sequence(
														MaterialRules.condition(MaterialRules.STONE_DEPTH_CEILING,
																MaterialRules.block(Blocks.SANDSTONE.getDefaultState())),
														MaterialRules.block(Blocks.SAND.getDefaultState()))),
										MaterialRules.sequence(
												MaterialRules.condition(MaterialRules.STONE_DEPTH_CEILING,
														MaterialRules.block(Blocks.STONE.getDefaultState())),
												MaterialRules.block(Blocks.GRAVEL.getDefaultState())))))));
	}

	private static MaterialRules.MaterialRule ring(RegistryKey<Biome>[] biomes, Block block) {
		return MaterialRules.condition(MaterialRules.biome(biomes),
				MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR,
						MaterialRules.block(block.getDefaultState())));
	}

	@SafeVarargs
	private static RegistryKey<Biome>[] join(RegistryKey<Biome>[] first, RegistryKey<Biome>... rest) {
		RegistryKey<Biome>[] all = java.util.Arrays.copyOf(first, first.length + rest.length);
		System.arraycopy(rest, 0, all, first.length, rest.length);
		return all;
	}
}
