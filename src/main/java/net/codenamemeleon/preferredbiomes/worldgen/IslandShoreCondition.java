package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.codenamemeleon.preferredbiomes.mixin.MaterialRuleContextAccessor;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.util.math.random.RandomSplitter;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseParametersKeys;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class IslandShoreCondition implements MaterialRules.MaterialCondition {

	private static final double RING_ALWAYS = 3.0;

	private static final double RING_FOUR = 4.0;

	private static final double RING_FIVE = 5.0;

	private static final float ODDS_FOUR = 0.70F;

	private static final float ODDS_FIVE = 0.20F;

	public static final Identifier SHORE_DITHER = new Identifier("preferred-biomes", "shore_dither");

	public static final CodecHolder<IslandShoreCondition> CODEC_HOLDER = CodecHolder.of(
			RecordCodecBuilder.mapCodec(instance -> instance.group(
					Codec.intRange(PreferredBiomeSource.MIN_ISLAND_SIZE, PreferredBiomeSource.MAX_ISLAND_SIZE)
							.fieldOf("size").forGetter(IslandShoreCondition::size),
					Codec.floatRange(0.0F, 1.0F).fieldOf("frequency")
							.forGetter(IslandShoreCondition::frequency),
					Codec.INT.optionalFieldOf("noise", PreferredBiomeSource.DEFAULT_ISLAND_NOISE)
							.forGetter(IslandShoreCondition::noise)
			).apply(instance, IslandShoreCondition::new)));

	private final int size;
	private final float frequency;
	private final int noise;

	private final Map<NoiseConfig, Samplers> cache =
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
	public CodecHolder<? extends MaterialRules.MaterialCondition> codec() {
		return CODEC_HOLDER;
	}

	private record Samplers(IslandField ring, RandomSplitter dither) {
	}

	private Samplers samplers(NoiseConfig noiseConfig) {
		return this.cache.computeIfAbsent(noiseConfig, config -> new Samplers(
				new IslandField(this.size, this.frequency, this.noise, IslandField.Channel.SHORE_RING,
						seeded(config, IslandTerrain.ISLAND_JITTER),
						seeded(config, IslandTerrain.ISLAND_SHAPE),
						seeded(config, NoiseParametersKeys.OFFSET.getValue()),
						seeded(config, NoiseParametersKeys.TEMPERATURE.getValue())),
				config.getOrCreateRandomDeriver(SHORE_DITHER)));
	}

	private static DensityFunction.Noise seeded(NoiseConfig noiseConfig, Identifier id) {
		RegistryKey<DoublePerlinNoiseSampler.NoiseParameters> key =
				RegistryKey.of(RegistryKeys.NOISE_PARAMETERS, id);
		return new DensityFunction.Noise(null, noiseConfig.getOrCreateSampler(key));
	}

	@Override
	public MaterialRules.BooleanSupplier apply(MaterialRules.MaterialRuleContext context) {
		MaterialRuleContextAccessor accessor = (MaterialRuleContextAccessor) (Object) context;
		Samplers samplers = samplers(accessor.getNoiseConfig());

		return new MaterialRules.BooleanSupplier() {
			private int lastX = Integer.MIN_VALUE;
			private int lastZ = Integer.MIN_VALUE;
			private boolean last;

			@Override
			public boolean get() {
				int x = accessor.getBlockX();
				int z = accessor.getBlockZ();
				if (x == this.lastX && z == this.lastZ) {
					return this.last;
				}
				this.lastX = x;
				this.lastZ = z;

				double ring = samplers.ring()
						.sample(new DensityFunction.UnblendedNoisePos(x, 0, z));
				if (ring <= IslandField.NO_SHORE) {
					this.last = false;
					return false;
				}

				if (ring <= RING_ALWAYS) {
					this.last = true;
				} else if (ring > RING_FIVE) {
					this.last = false;
				} else {
					float u = samplers.dither().split(x, 0, z).nextFloat();
					this.last = u < (ring <= RING_FOUR ? ODDS_FOUR : ODDS_FIVE);
				}
				return this.last;
			}
		};
	}
}
