package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

public final class IslandBiomes {

	private IslandBiomes() {
	}

	private static final Climate.Parameter[] TEMPERATURES = {
			range(-1.0F, -0.45F),
			range(-0.45F, -0.15F),
			range(-0.15F, 0.2F),
			range(0.2F, 0.55F),
			range(0.55F, 1.0F),
	};

	private static final Climate.Parameter FULL = range(-1.0F, 1.0F);

	private static final Climate.Parameter SURFACE = range(-1.5F, 0.2F);

	private static final Climate.Parameter CAVES = range(0.2F, 0.65F);

	private static final Climate.Parameter BOTTOM = range(0.65F, 2.0F);

	private static final Climate.Parameter C_DEEP = range(-1.0F, -0.5F);

	private static final Climate.Parameter C_SHALLOW = range(-0.5F, -0.12F);

	private static final Climate.Parameter C_SHORE = range(-0.12F, 0.0F);

	private static final Climate.Parameter C_LAND = range(0.0F, 1.0F);

	public static final float LARGE_SIZE_MIN = 5.0F / 6.0F;

	public static final float MANGROVE_ROLL_MIN = 0.675F;
	public static final float MANGROVE_ROLL_MAX = 0.975F;

	public static final float PALE_GARDEN_ROLL_MIN = 0.725F;

	private static final Climate.Parameter S_SMALL = range(0.0F, 1.0F / 3.0F);
	private static final Climate.Parameter S_MEDIUM = range(1.0F / 3.0F, LARGE_SIZE_MIN);
	private static final Climate.Parameter S_LARGE = range(LARGE_SIZE_MIN, 1.0F);

	public static final ResourceKey<Biome> LUSH_ISLAND = islandBiome("lush_island");

	public static final ResourceKey<Biome> DESERT_ISLAND = islandBiome("desert_island");

	public static final ResourceKey<Biome> MANGROVE_ISLAND = islandBiome("mangrove_island");

	public static final ResourceKey<Biome> ICE_SPIKES_ISLAND = islandBiome("ice_spikes_island");

	public static final ResourceKey<Biome> DARK_FOREST_ISLAND = islandBiome("dark_forest_island");

	public static final ResourceKey<Biome> SNOWY_TAIGA_ISLAND = islandBiome("snowy_taiga_island");

	public static final ResourceKey<Biome> SNOWY_CHERRY_GROVE = islandBiome("snowy_cherry_grove");

	private static final ResourceKey<Biome>[] BARE = keys(
			islandBiome("bare_island_frozen_ocean"),
			islandBiome("bare_island_cold_ocean"),
			islandBiome("bare_island_ocean"),
			islandBiome("bare_island_lukewarm_ocean"),
			islandBiome("bare_island_warm_ocean"));

	private static ResourceKey<Biome> islandBiome(String path) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("preferred-biomes", path));
	}

	private static final ResourceKey<Biome>[] SHALLOW = keys(
			Biomes.FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.OCEAN,
			Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN);

	private static final ResourceKey<Biome>[] DEEP = keys(
			Biomes.DEEP_FROZEN_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_OCEAN,
			Biomes.DEEP_LUKEWARM_OCEAN, Biomes.WARM_OCEAN);

	private static final ResourceKey<Biome>[] COMMON = keys(
			SNOWY_TAIGA_ISLAND, Biomes.TAIGA, Biomes.MEADOW,
			Biomes.SAVANNA, Biomes.SAVANNA);

	private static final ResourceKey<Biome>[] UNCOMMON = keys(
			SNOWY_CHERRY_GROVE, Biomes.CHERRY_GROVE, Biomes.WINDSWEPT_FOREST,
			Biomes.SPARSE_JUNGLE, DESERT_ISLAND);

	private static final ResourceKey<Biome>[] RARE = keys(
			ICE_SPIKES_ISLAND, Biomes.OLD_GROWTH_PINE_TAIGA, DARK_FOREST_ISLAND,
			Biomes.BAMBOO_JUNGLE, MANGROVE_ISLAND);

	private static final String[] BAND_NAMES = {"frozen", "cold", "temperate", "lukewarm", "warm"};

	public static final int BAND_FROZEN = 0;
	public static final int BAND_COLD = 1;
	public static final int BAND_TEMPERATE = 2;
	public static final int BAND_LUKEWARM = 3;
	public static final int BAND_WARM = 4;

	@SuppressWarnings("unchecked")
	public static ResourceKey<Biome>[] shoresInBand(int band) {
		List<ResourceKey<Biome>> islands = new ArrayList<>(List.of(
				SHALLOW[band], COMMON[band], UNCOMMON[band], RARE[band],
				Biomes.MUSHROOM_FIELDS));
		if (band == BAND_TEMPERATE) {
			islands.add(Biomes.PALE_GARDEN);
		}
		if (band == BAND_WARM) {
			islands.add(LUSH_ISLAND);
		}
		ResourceKey<Biome>[] out = new ResourceKey[islands.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = shoreOf(islands.get(i), band);
		}
		return out;
	}

	public static int bandOf(float temperature) {
		for (int band = 0; band < TEMPERATURES.length; band++) {
			Climate.Parameter t = TEMPERATURES[band];
			if (temperature >= Climate.unquantizeCoord(t.min())
					&& temperature < Climate.unquantizeCoord(t.max())) {
				return band;
			}
		}
		return temperature >= Climate.unquantizeCoord(TEMPERATURES[BAND_WARM].max())
				&& temperature <= 1.0F ? BAND_WARM : -1;
	}

	public static boolean rollsMangrove(int band, float size, float roll) {
		return band == BAND_WARM && size >= LARGE_SIZE_MIN
				&& roll >= MANGROVE_ROLL_MIN && roll < MANGROVE_ROLL_MAX;
	}

	public static ResourceKey<Biome> shoreOf(ResourceKey<Biome> island, int band) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("preferred-biomes",
				"island_shore_" + island.identifier().getPath() + "_" + BAND_NAMES[band]));
	}

	public static Climate.ParameterList<Holder<Biome>> entries(
			HolderGetter<Biome> biomes) {
		List<Pair<Climate.ParameterPoint, Holder<Biome>>> out = new ArrayList<>();

		for (int band = 0; band < TEMPERATURES.length; band++) {
			Climate.Parameter t = TEMPERATURES[band];

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
			} else if (band == BAND_TEMPERATE) {
				island(out, biomes, t, band, S_LARGE, 0.475F, PALE_GARDEN_ROLL_MIN, RARE[band]);
				island(out, biomes, t, band, S_LARGE, PALE_GARDEN_ROLL_MIN, 0.975F, Biomes.PALE_GARDEN);
			} else {
				island(out, biomes, t, band, S_LARGE, 0.475F, 0.975F, RARE[band]);
			}
			island(out, biomes, t, band, S_LARGE, MANGROVE_ROLL_MAX, 1.0F, Biomes.MUSHROOM_FIELDS);

			add(out, biomes, t, range(0.7F, 1.0F), FULL, FULL, CAVES, FULL, Biomes.LUSH_CAVES);
			if (band >= 3) {
				add(out, biomes, t, range(-1.0F, -0.5F), FULL, FULL, CAVES, FULL,
						Biomes.DRIPSTONE_CAVES);
			} else {
				add(out, biomes, t, range(-1.0F, -0.5F), FULL, FULL, CAVES, FULL, SHALLOW[band]);
			}
			add(out, biomes, t, range(-0.5F, -0.2F), FULL, FULL, CAVES, FULL, Biomes.SULFUR_CAVES);
			add(out, biomes, t, range(-0.2F, 0.7F), FULL, FULL, CAVES, FULL, SHALLOW[band]);

			add(out, biomes, t, range(-1.0F, -0.1F), FULL, FULL, BOTTOM, FULL, Biomes.DEEP_DARK);
			add(out, biomes, t, range(-0.1F, 1.0F), FULL, FULL, BOTTOM, FULL, SHALLOW[band]);
		}

		return new Climate.ParameterList<>(List.copyOf(out));
	}

	private static void island(List<Pair<Climate.ParameterPoint, Holder<Biome>>> out,
			HolderGetter<Biome> biomes, Climate.Parameter temperature, int band,
			Climate.Parameter size, float rollMin, float rollMax,
			ResourceKey<Biome> biome) {
		Climate.Parameter roll = range(rollMin, rollMax);
		boolean bare = biome == SHALLOW[band];
		add(out, biomes, temperature, FULL, C_SHORE, size, SURFACE, roll, shoreOf(biome, band));
		add(out, biomes, temperature, FULL, C_LAND, size, SURFACE, roll,
				bare ? BARE[band] : biome);
	}

	private static void add(List<Pair<Climate.ParameterPoint, Holder<Biome>>> out,
			HolderGetter<Biome> biomes,
			Climate.Parameter temperature, Climate.Parameter humidity,
			Climate.Parameter continentalness, Climate.Parameter erosion,
			Climate.Parameter depth, Climate.Parameter weirdness,
			ResourceKey<Biome> biome) {
		out.add(Pair.of(
				Climate.parameters(temperature, humidity, continentalness,
						erosion, depth, weirdness, 0.0F),
				biomes.getOrThrow(biome)));
	}

	private static Climate.Parameter range(float min, float max) {
		return Climate.Parameter.span(min, max);
	}

	@SafeVarargs
	private static ResourceKey<Biome>[] keys(ResourceKey<Biome>... keys) {
		return keys;
	}
}
