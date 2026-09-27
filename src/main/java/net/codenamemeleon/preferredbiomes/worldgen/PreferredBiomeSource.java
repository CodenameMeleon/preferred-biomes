package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.codenamemeleon.preferredbiomes.PreferredBiomes;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class PreferredBiomeSource extends MultiNoiseBiomeSource {

	public static final int MIN_ISLAND_SIZE = 10;

	public static final int MAX_ISLAND_SIZE = 100;

	public static final int DEFAULT_ISLAND_SIZE = 60;
	public static final float DEFAULT_ISLAND_FREQUENCY = 0.5F;

	public static final int MIN_ISLAND_NOISE = 1;

	public static final int MAX_ISLAND_NOISE = 20;

	public static final int DEFAULT_ISLAND_NOISE = 10;

	public static final MapCodec<PreferredBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.mapEither(MultiNoiseBiomeSource.DIRECT_CODEC, MultiNoiseBiomeSource.PRESET_CODEC)
					.forGetter(PreferredBiomeSource::biomeEntries),
			Identifier.CODEC.listOf().optionalFieldOf("excluded", List.of())
					.forGetter(PreferredBiomeSource::excludedIds),
			Codec.BOOL.optionalFieldOf("island_survival_challenge", false)
					.forGetter(PreferredBiomeSource::islandSurvivalChallenge),
			Codec.intRange(MIN_ISLAND_SIZE, MAX_ISLAND_SIZE)
					.optionalFieldOf("island_size", DEFAULT_ISLAND_SIZE)
					.forGetter(PreferredBiomeSource::islandSize),
			Codec.floatRange(0.0F, 1.0F)
					.optionalFieldOf("island_frequency", DEFAULT_ISLAND_FREQUENCY)
					.forGetter(PreferredBiomeSource::islandFrequency),
			Codec.intRange(MIN_ISLAND_NOISE, MAX_ISLAND_NOISE)
					.optionalFieldOf("island_noise", DEFAULT_ISLAND_NOISE)
					.forGetter(PreferredBiomeSource::islandNoise),
			Biome.CODEC.listOf()
					.optionalFieldOf("declared_biomes", List.of())
					.forGetter(PreferredBiomeSource::declaredBiomes)
	).apply(instance, PreferredBiomeSource::new));

	private final Either<Climate.ParameterList<Holder<Biome>>,
			Holder<MultiNoiseBiomeSourceParameterList>> biomeEntries;

	private final List<Identifier> excludedIds;
	private final Set<Identifier> excluded;

	private final boolean islandSurvivalChallenge;
	private final int islandSize;
	private final float islandFrequency;
	private final int islandNoise;

	private final List<Holder<Biome>> declaredBiomes;

	private volatile Climate.ParameterList<Holder<Biome>> fallback;

	private volatile boolean fallbackComputed;

	public PreferredBiomeSource(
			Either<Climate.ParameterList<Holder<Biome>>,
					Holder<MultiNoiseBiomeSourceParameterList>> biomeEntries,
			List<Identifier> excluded, boolean islandSurvivalChallenge, int islandSize,
			float islandFrequency) {
		this(biomeEntries, excluded, islandSurvivalChallenge, islandSize, islandFrequency,
				DEFAULT_ISLAND_NOISE, List.of());
	}

	public PreferredBiomeSource(
			Either<Climate.ParameterList<Holder<Biome>>,
					Holder<MultiNoiseBiomeSourceParameterList>> biomeEntries,
			List<Identifier> excluded, boolean islandSurvivalChallenge, int islandSize,
			float islandFrequency, int islandNoise, List<Holder<Biome>> declaredBiomes) {
		super(biomeEntries);
		this.declaredBiomes = List.copyOf(declaredBiomes);
		this.biomeEntries = biomeEntries;
		this.excludedIds = List.copyOf(excluded);
		this.excluded = Set.copyOf(excluded);
		this.islandSurvivalChallenge = islandSurvivalChallenge;
		this.islandSize = islandSize;
		this.islandFrequency = islandFrequency;
		this.islandNoise = islandNoise;
		PreferredBiomes.LOGGER.info(
				"PreferredBiomeSource: {} excluded, island_survival_challenge={}, island_size={}, "
						+ "island_frequency={}, island_noise={}",
				this.excluded.size(), islandSurvivalChallenge, islandSize, islandFrequency,
				islandNoise);
	}

	public Either<Climate.ParameterList<Holder<Biome>>,
			Holder<MultiNoiseBiomeSourceParameterList>> biomeEntries() {
		return this.biomeEntries;
	}

	public List<Identifier> excludedIds() {
		return this.excludedIds;
	}

	public boolean islandSurvivalChallenge() {
		return this.islandSurvivalChallenge;
	}

	public int islandSize() {
		return this.islandSize;
	}

	public float islandFrequency() {
		return this.islandFrequency;
	}

	public int islandNoise() {
		return this.islandNoise;
	}

	public List<Holder<Biome>> declaredBiomes() {
		return this.declaredBiomes;
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	private Climate.ParameterList<Holder<Biome>> entries() {
		return this.biomeEntries.map(entries -> entries, listEntry -> listEntry.value().parameters());
	}

	private boolean isExcluded(Holder<Biome> biome) {
		Optional<ResourceKey<Biome>> key = biome.unwrapKey();
		return key.isPresent() && this.excluded.contains(key.get().identifier());
	}

	private Climate.ParameterList<Holder<Biome>> fallback() {
		if (this.fallbackComputed) {
			return this.fallback;
		}
		synchronized (this) {
			if (!this.fallbackComputed) {
				List<Pair<Climate.ParameterPoint, Holder<Biome>>> kept = new ArrayList<>();
				for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : entries().values()) {
					if (!isExcluded(pair.getSecond())) {
						kept.add(pair);
					}
				}
				if (kept.isEmpty()) {
					PreferredBiomes.LOGGER.warn(
							"PreferredBiomeSource: every biome is excluded; leaving the world as vanilla generates it");
					this.fallback = null;
				} else {
					this.fallback = new Climate.ParameterList<>(kept);
				}
				this.fallbackComputed = true;
			}
			return this.fallback;
		}
	}

	@Override
	public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler noise) {
		Holder<Biome> biome = super.getNoiseBiome(x, y, z, noise);
		if (!isExcluded(biome)) {
			return biome;
		}
		Climate.ParameterList<Holder<Biome>> remaining = fallback();
		return remaining == null ? biome : remaining.findValue(noise.sample(x, y, z));
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		Stream<Holder<Biome>> selectable =
				super.collectPossibleBiomes().filter(biome -> !isExcluded(biome));
		return this.islandSurvivalChallenge && !this.declaredBiomes.isEmpty()
				? Stream.concat(selectable, this.declaredBiomes.stream())
				: selectable;
	}
}
