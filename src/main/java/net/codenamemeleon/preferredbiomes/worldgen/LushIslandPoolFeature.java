package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import net.codenamemeleon.preferredbiomes.PreferredBiomeTags;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseParametersKeys;

public class LushIslandPoolFeature extends Feature<DefaultFeatureConfig> {

	private static final RegistryKey<ConfiguredFeature<?, ?>> POOL = RegistryKey.of(
			RegistryKeys.CONFIGURED_FEATURE,
			new Identifier("preferred-biomes", "lush_island_clay_pool"));

	public LushIslandPoolFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		if (!(world.toServerWorld().getChunkManager().getChunkGenerator().getBiomeSource()
				instanceof PreferredBiomeSource source)) {
			return false;
		}
		NoiseConfig noiseConfig = world.toServerWorld().getChunkManager().getNoiseConfig();
		IslandField field = field(noiseConfig, source);

		BlockPos origin = context.getOrigin();
		double g = field.cellSize();
		int cx = (int) Math.floor(origin.getX() / g);
		int cz = (int) Math.floor(origin.getZ() / g);
		BlockPos anchor = field.anchorLand(cx, cz);
		if (anchor == null) {
			return false;
		}
		ChunkPos chunk = new ChunkPos(origin);
		if (!new ChunkPos(anchor).equals(chunk)) {
			return false;
		}

		int x = anchor.getX();
		int z = anchor.getZ();
		int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
		if (!world.getBiome(new BlockPos(anchor.getX(), surfaceY, anchor.getZ()))
				.isIn(PreferredBiomeTags.LUSH_ISLANDS)) {
			return false;
		}
		BlockPos pos = new BlockPos(x, surfaceY, z);
		return world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE)
				.getOrEmpty(POOL)
				.map(pool -> pool.generate(world, context.getGenerator(), context.getRandom(), pos))
				.orElse(false);
	}

	private static IslandField field(NoiseConfig noiseConfig, PreferredBiomeSource source) {
		return new IslandField(source.islandSize(), source.islandFrequency(), source.islandNoise(),
				IslandField.Channel.HEIGHT,
				seeded(noiseConfig, IslandTerrain.ISLAND_JITTER),
				seeded(noiseConfig, IslandTerrain.ISLAND_SHAPE),
				seeded(noiseConfig, NoiseParametersKeys.OFFSET.getValue()),
				seeded(noiseConfig, NoiseParametersKeys.TEMPERATURE.getValue()));
	}

	private static DensityFunction.Noise seeded(NoiseConfig noiseConfig, Identifier id) {
		RegistryKey<DoublePerlinNoiseSampler.NoiseParameters> key =
				RegistryKey.of(RegistryKeys.NOISE_PARAMETERS, id);
		return new DensityFunction.Noise(null, noiseConfig.getOrCreateSampler(key));
	}
}
