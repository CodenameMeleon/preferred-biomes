package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.codenamemeleon.preferredbiomes.PreferredBiomes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

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

	public static final Codec<PreferredBiomeSource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.mapEither(MultiNoiseBiomeSource.CUSTOM_CODEC, MultiNoiseBiomeSource.PRESET_CODEC)
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
			Biome.REGISTRY_CODEC.listOf()
					.optionalFieldOf("declared_biomes", List.of())
					.forGetter(PreferredBiomeSource::declaredBiomes)
	).apply(instance, PreferredBiomeSource::new));

	private final Either<MultiNoiseUtil.Entries<RegistryEntry<Biome>>,
			RegistryEntry<MultiNoiseBiomeSourceParameterList>> biomeEntries;

	private final List<Identifier> excludedIds;
	private final Set<Identifier> excluded;

	private final boolean islandSurvivalChallenge;
	private final int islandSize;
	private final float islandFrequency;
	private final int islandNoise;

	private final List<RegistryEntry<Biome>> declaredBiomes;

	private volatile MultiNoiseUtil.Entries<RegistryEntry<Biome>> fallback;

	private volatile boolean fallbackComputed;

	public PreferredBiomeSource(
			Either<MultiNoiseUtil.Entries<RegistryEntry<Biome>>,
					RegistryEntry<MultiNoiseBiomeSourceParameterList>> biomeEntries,
			List<Identifier> excluded, boolean islandSurvivalChallenge, int islandSize,
			float islandFrequency) {
		this(biomeEntries, excluded, islandSurvivalChallenge, islandSize, islandFrequency,
				DEFAULT_ISLAND_NOISE, List.of());
	}

	public PreferredBiomeSource(
			Either<MultiNoiseUtil.Entries<RegistryEntry<Biome>>,
					RegistryEntry<MultiNoiseBiomeSourceParameterList>> biomeEntries,
			List<Identifier> excluded, boolean islandSurvivalChallenge, int islandSize,
			float islandFrequency, int islandNoise, List<RegistryEntry<Biome>> declaredBiomes) {
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

	public Either<MultiNoiseUtil.Entries<RegistryEntry<Biome>>,
			RegistryEntry<MultiNoiseBiomeSourceParameterList>> biomeEntries() {
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

	public List<RegistryEntry<Biome>> declaredBiomes() {
		return this.declaredBiomes;
	}

	@Override
	protected Codec<? extends BiomeSource> getCodec() {
		return CODEC;
	}

	private MultiNoiseUtil.Entries<RegistryEntry<Biome>> entries() {
		return this.biomeEntries.map(entries -> entries, listEntry -> listEntry.value().getEntries());
	}

	private boolean isExcluded(RegistryEntry<Biome> biome) {
		Optional<RegistryKey<Biome>> key = biome.getKey();
		return key.isPresent() && this.excluded.contains(key.get().getValue());
	}

	private MultiNoiseUtil.Entries<RegistryEntry<Biome>> fallback() {
		if (this.fallbackComputed) {
			return this.fallback;
		}
		synchronized (this) {
			if (!this.fallbackComputed) {
				List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> kept = new ArrayList<>();
				for (Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>> pair : entries().getEntries()) {
					if (!isExcluded(pair.getSecond())) {
						kept.add(pair);
					}
				}
				if (kept.isEmpty()) {
					PreferredBiomes.LOGGER.warn(
							"PreferredBiomeSource: every biome is excluded; leaving the world as vanilla generates it");
					this.fallback = null;
				} else {
					this.fallback = new MultiNoiseUtil.Entries<>(kept);
				}
				this.fallbackComputed = true;
			}
			return this.fallback;
		}
	}

	@Override
	public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
		RegistryEntry<Biome> biome = super.getBiome(x, y, z, noise);
		if (!isExcluded(biome)) {
			return biome;
		}
		MultiNoiseUtil.Entries<RegistryEntry<Biome>> remaining = fallback();
		return remaining == null ? biome : remaining.get(noise.sample(x, y, z));
	}

	@Override
	protected Stream<RegistryEntry<Biome>> biomeStream() {
		Stream<RegistryEntry<Biome>> selectable =
				super.biomeStream().filter(biome -> !isExcluded(biome));
		return this.islandSurvivalChallenge && !this.declaredBiomes.isEmpty()
				? Stream.concat(selectable, this.declaredBiomes.stream())
				: selectable;
	}
}
