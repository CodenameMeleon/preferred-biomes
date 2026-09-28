package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.codenamemeleon.preferredbiomes.mixin.MaterialRuleContextAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class IslandShoreCondition implements SurfaceRules.ConditionSource {

	private static final double RING_ALWAYS = 3.0;

	private static final double RING_FOUR = 4.0;

	private static final double RING_FIVE = 5.0;

	private static final float ODDS_FOUR = 0.70F;

	private static final float ODDS_FIVE = 0.20F;

	public static final Identifier SHORE_DITHER = Identifier.fromNamespaceAndPath("preferred-biomes", "shore_dither");

	public static final MapCodec<IslandShoreCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(PreferredBiomeSource.MIN_ISLAND_SIZE, PreferredBiomeSource.MAX_ISLAND_SIZE)
					.fieldOf("size").forGetter(IslandShoreCondition::size),
			Codec.floatRange(0.0F, 1.0F).fieldOf("frequency")
					.forGetter(IslandShoreCondition::frequency),
			Codec.INT.optionalFieldOf("noise", PreferredBiomeSource.DEFAULT_ISLAND_NOISE)
					.forGetter(IslandShoreCondition::noise)
	).apply(instance, IslandShoreCondition::new));

	private final int size;
	private final float frequency;
	private final int noise;

	private final Map<RandomState, Samplers> cache =
			Collections.synchronizedMap(new WeakHashMap<>());

	public IslandShoreCondition(int size, float frequency, int noise) {
		this.size = size;
		this.frequency = frequency;
		this.noise = noise;
	}

	public int size() {
		return this.size;
	}

	public float frequency() {
		return this.frequency;
	}

	public int noise() {
		return this.noise;
	}

	@Override
	public MapCodec<? extends SurfaceRules.ConditionSource> codec() {
		return MAP_CODEC;
	}

	private record Samplers(IslandField ring, PositionalRandomFactory dither) {
	}

	private Samplers samplers(RandomState noiseConfig) {
		return this.cache.computeIfAbsent(noiseConfig, config -> new Samplers(
				new IslandField(this.size, this.frequency, this.noise, IslandField.Channel.SHORE_RING,
						seeded(config, IslandTerrain.ISLAND_JITTER),
						seeded(config, IslandTerrain.ISLAND_SHAPE),
						seeded(config, Noises.SHIFT.identifier()),
						seeded(config, Noises.TEMPERATURE.identifier())),
				config.getOrCreateRandomFactory(SHORE_DITHER)));
	}

	private static DensityFunction.NoiseHolder seeded(RandomState noiseConfig, Identifier id) {
		ResourceKey<NormalNoise.NoiseParameters> key =
				ResourceKey.create(Registries.NOISE, id);
		return new DensityFunction.NoiseHolder(null, noiseConfig.getOrCreateNoise(key));
	}

	@Override
	public SurfaceRules.Condition apply(SurfaceRules.Context context) {
		MaterialRuleContextAccessor accessor = (MaterialRuleContextAccessor) (Object) context;
		Samplers samplers = samplers(accessor.getNoiseConfig());

		return new SurfaceRules.Condition() {
			private int lastX = Integer.MIN_VALUE;
			private int lastZ = Integer.MIN_VALUE;
			private boolean last;

			@Override
			public boolean test() {
				int x = accessor.getBlockX();
				int z = accessor.getBlockZ();
				if (x == this.lastX && z == this.lastZ) {
					return this.last;
				}
				this.lastX = x;
				this.lastZ = z;

				double ring = samplers.ring()
						.compute(new DensityFunction.SinglePointContext(x, 0, z));
				if (ring <= IslandField.NO_SHORE) {
					this.last = false;
					return false;
				}

				if (ring <= RING_ALWAYS) {
					this.last = true;
				} else if (ring > RING_FIVE) {
					this.last = false;
				} else {
					float u = samplers.dither().at(x, 0, z).nextFloat();
					this.last = u < (ring <= RING_FOUR ? ODDS_FOUR : ODDS_FIVE);
				}
				return this.last;
			}
		};
	}
}
