package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.ArrayList;
import java.util.List;

public final class IslandBiomes {

	private IslandBiomes() {
	}

	private static final MultiNoiseUtil.ParameterRange[] TEMPERATURES = {
			range(-1.0F, -0.45F),
			range(-0.45F, -0.15F),
			range(-0.15F, 0.2F),
			range(0.2F, 0.55F),
			range(0.55F, 1.0F),
	};

	private static final MultiNoiseUtil.ParameterRange FULL = range(-1.0F, 1.0F);

	private static final MultiNoiseUtil.ParameterRange SURFACE = range(-1.5F, 0.2F);

	private static final MultiNoiseUtil.ParameterRange CAVES = range(0.2F, 0.65F);

	private static final MultiNoiseUtil.ParameterRange BOTTOM = range(0.65F, 2.0F);

	private static final MultiNoiseUtil.ParameterRange C_DEEP = range(-1.0F, -0.5F);

	private static final MultiNoiseUtil.ParameterRange C_SHALLOW = range(-0.5F, -0.12F);

	private static final MultiNoiseUtil.ParameterRange C_SHORE = range(-0.12F, 0.0F);

	private static final MultiNoiseUtil.ParameterRange C_LAND = range(0.0F, 1.0F);

	public static final float LARGE_SIZE_MIN = 5.0F / 6.0F;

	public static final float MANGROVE_ROLL_MIN = 0.675F;
	public static final float MANGROVE_ROLL_MAX = 0.975F;

	private static final MultiNoiseUtil.ParameterRange S_SMALL = range(0.0F, 1.0F / 3.0F);
	private static final MultiNoiseUtil.ParameterRange S_MEDIUM = range(1.0F / 3.0F, LARGE_SIZE_MIN);
	private static final MultiNoiseUtil.ParameterRange S_LARGE = range(LARGE_SIZE_MIN, 1.0F);

	public static final RegistryKey<Biome> LUSH_ISLAND = islandBiome("lush_island");

	public static final RegistryKey<Biome> DESERT_ISLAND = islandBiome("desert_island");

	public static final RegistryKey<Biome> MANGROVE_ISLAND = islandBiome("mangrove_island");

	public static final RegistryKey<Biome> ICE_SPIKES_ISLAND = islandBiome("ice_spikes_island");

	public static final RegistryKey<Biome> DARK_FOREST_ISLAND = islandBiome("dark_forest_island");

	public static final RegistryKey<Biome> SNOWY_TAIGA_ISLAND = islandBiome("snowy_taiga_island");

	public static final RegistryKey<Biome> SNOWY_CHERRY_GROVE = islandBiome("snowy_cherry_grove");

	private static final RegistryKey<Biome>[] BARE = keys(
			islandBiome("bare_island_frozen_ocean"),
			islandBiome("bare_island_cold_ocean"),
			islandBiome("bare_island_ocean"),
			islandBiome("bare_island_lukewarm_ocean"),
			islandBiome("bare_island_warm_ocean"));

	private static RegistryKey<Biome> islandBiome(String path) {
		return RegistryKey.of(RegistryKeys.BIOME, new Identifier("preferred-biomes", path));
	}

	private static final RegistryKey<Biome>[] SHALLOW = keys(
			BiomeKeys.FROZEN_OCEAN, BiomeKeys.COLD_OCEAN, BiomeKeys.OCEAN,
			BiomeKeys.LUKEWARM_OCEAN, BiomeKeys.WARM_OCEAN);

	private static final RegistryKey<Biome>[] DEEP = keys(
			BiomeKeys.DEEP_FROZEN_OCEAN, BiomeKeys.DEEP_COLD_OCEAN, BiomeKeys.DEEP_OCEAN,
			BiomeKeys.DEEP_LUKEWARM_OCEAN, BiomeKeys.WARM_OCEAN);

	private static final RegistryKey<Biome>[] COMMON = keys(
			SNOWY_TAIGA_ISLAND, BiomeKeys.TAIGA, BiomeKeys.MEADOW,
			BiomeKeys.SAVANNA, BiomeKeys.SAVANNA);

	private static final RegistryKey<Biome>[] UNCOMMON = keys(
			SNOWY_CHERRY_GROVE, BiomeKeys.CHERRY_GROVE, BiomeKeys.WINDSWEPT_FOREST,
			BiomeKeys.SPARSE_JUNGLE, DESERT_ISLAND);

	private static final RegistryKey<Biome>[] RARE = keys(
			ICE_SPIKES_ISLAND, BiomeKeys.OLD_GROWTH_PINE_TAIGA, DARK_FOREST_ISLAND,
			BiomeKeys.BAMBOO_JUNGLE, MANGROVE_ISLAND);

	private static final String[] BAND_NAMES = {"frozen", "cold", "temperate", "lukewarm", "warm"};

	public static final int BAND_FROZEN = 0;
	public static final int BAND_COLD = 1;
	public static final int BAND_TEMPERATE = 2;
	public static final int BAND_LUKEWARM = 3;
	public static final int BAND_WARM = 4;

	@SuppressWarnings("unchecked")
	public static RegistryKey<Biome>[] shoresInBand(int band) {
		List<RegistryKey<Biome>> islands = new ArrayList<>(List.of(
				SHALLOW[band], COMMON[band], UNCOMMON[band], RARE[band],
				BiomeKeys.MUSHROOM_FIELDS));
		if (band == BAND_WARM) {
			islands.add(LUSH_ISLAND);
		}
		RegistryKey<Biome>[] out = new RegistryKey[islands.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = shoreOf(islands.get(i), band);
		}
		return out;
	}

	public static int bandOf(float temperature) {
		for (int band = 0; band < TEMPERATURES.length; band++) {
			MultiNoiseUtil.ParameterRange t = TEMPERATURES[band];
			if (temperature >= MultiNoiseUtil.toFloat(t.min())
					&& temperature < MultiNoiseUtil.toFloat(t.max())) {
				return band;
			}
		}
		return temperature >= MultiNoiseUtil.toFloat(TEMPERATURES[BAND_WARM].max())
				&& temperature <= 1.0F ? BAND_WARM : -1;
	}

	public static boolean rollsMangrove(int band, float size, float roll) {
		return band == BAND_WARM && size >= LARGE_SIZE_MIN
				&& roll >= MANGROVE_ROLL_MIN && roll < MANGROVE_ROLL_MAX;
	}

	public static RegistryKey<Biome> shoreOf(RegistryKey<Biome> island, int band) {
		return RegistryKey.of(RegistryKeys.BIOME, new Identifier("preferred-biomes",
				"island_shore_" + island.getValue().getPath() + "_" + BAND_NAMES[band]));
	}

	public static MultiNoiseUtil.Entries<RegistryEntry<Biome>> entries(
			RegistryEntryLookup<Biome> biomes) {
		List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> out = new ArrayList<>();

		for (int band = 0; band < TEMPERATURES.length; band++) {
			MultiNoiseUtil.ParameterRange t = TEMPERATURES[band];

			add(out, biomes, t, FULL, C_DEEP, FULL, SURFACE, FULL, DEEP[band]);
			add(out, biomes, t, FULL, C_SHALLOW, FULL, SURFACE, FULL, SHALLOW[band]);

			island(out, biomes, t, band, S_SMALL, 0.0F, 0.9F, SHALLOW[band]);
			island(out, biomes, t, band, S_SMALL, 0.9F, 1.0F, COMMON[band]);

			island(out, biomes, t, band, S_MEDIUM, 0.0F, 0.15F, SHALLOW[band]);
			island(out, biomes, t, band, S_MEDIUM, 0.15F, 0.67F, COMMON[band]);
			island(out, biomes, t, band, S_MEDIUM, 0.67F, 1.0F, UNCOMMON[band]);

			island(out, biomes, t, band, S_LARGE, 0.0F, 0.05F, SHALLOW[band]);
			island(out, biomes, t, band, S_LARGE, 0.05F, 0.175F, COMMON[band]);
			island(out, biomes, t, band, S_LARGE, 0.175F, 0.475F, UNCOMMON[band]);
			if (band == 4) {
				island(out, biomes, t, band, S_LARGE, 0.475F, MANGROVE_ROLL_MIN, LUSH_ISLAND);
				island(out, biomes, t, band, S_LARGE, MANGROVE_ROLL_MIN, MANGROVE_ROLL_MAX,
						MANGROVE_ISLAND);
			} else {
				island(out, biomes, t, band, S_LARGE, 0.475F, 0.975F, RARE[band]);
			}
			island(out, biomes, t, band, S_LARGE, MANGROVE_ROLL_MAX, 1.0F, BiomeKeys.MUSHROOM_FIELDS);

			add(out, biomes, t, range(0.7F, 1.0F), FULL, FULL, CAVES, FULL, BiomeKeys.LUSH_CAVES);
			if (band >= 3) {
				add(out, biomes, t, range(-1.0F, -0.5F), FULL, FULL, CAVES, FULL,
						BiomeKeys.DRIPSTONE_CAVES);
				add(out, biomes, t, range(-0.5F, 0.7F), FULL, FULL, CAVES, FULL, SHALLOW[band]);
			} else {
				add(out, biomes, t, range(-1.0F, 0.7F), FULL, FULL, CAVES, FULL, SHALLOW[band]);
			}

			add(out, biomes, t, range(-1.0F, -0.1F), FULL, FULL, BOTTOM, FULL, BiomeKeys.DEEP_DARK);
			add(out, biomes, t, range(-0.1F, 1.0F), FULL, FULL, BOTTOM, FULL, SHALLOW[band]);
		}

		return new MultiNoiseUtil.Entries<>(List.copyOf(out));
	}

	private static void island(List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> out,
			RegistryEntryLookup<Biome> biomes, MultiNoiseUtil.ParameterRange temperature, int band,
			MultiNoiseUtil.ParameterRange size, float rollMin, float rollMax,
			RegistryKey<Biome> biome) {
		MultiNoiseUtil.ParameterRange roll = range(rollMin, rollMax);
		boolean bare = biome == SHALLOW[band];
		add(out, biomes, temperature, FULL, C_SHORE, size, SURFACE, roll, shoreOf(biome, band));
		add(out, biomes, temperature, FULL, C_LAND, size, SURFACE, roll,
				bare ? BARE[band] : biome);
	}

	private static void add(List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> out,
			RegistryEntryLookup<Biome> biomes,
			MultiNoiseUtil.ParameterRange temperature, MultiNoiseUtil.ParameterRange humidity,
			MultiNoiseUtil.ParameterRange continentalness, MultiNoiseUtil.ParameterRange erosion,
			MultiNoiseUtil.ParameterRange depth, MultiNoiseUtil.ParameterRange weirdness,
			RegistryKey<Biome> biome) {
		out.add(Pair.of(
				MultiNoiseUtil.createNoiseHypercube(temperature, humidity, continentalness,
						erosion, depth, weirdness, 0.0F),
				biomes.getOrThrow(biome)));
	}

	private static MultiNoiseUtil.ParameterRange range(float min, float max) {
		return MultiNoiseUtil.ParameterRange.of(min, max);
	}

	@SafeVarargs
	private static RegistryKey<Biome>[] keys(RegistryKey<Biome>... keys) {
		return keys;
	}
}
