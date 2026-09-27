package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class LushIslandPoolFeature extends Feature<NoneFeatureConfiguration> {

	private static final ResourceKey<ConfiguredFeature<?, ?>> POOL = ResourceKey.create(
			Registries.CONFIGURED_FEATURE,
			Identifier.fromNamespaceAndPath("preferred-biomes", "lush_island_clay_pool"));

	public LushIslandPoolFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel world = context.level();
		if (!(world.getLevel().getChunkSource().getGenerator().getBiomeSource()
				instanceof PreferredBiomeSource source)) {
			return false;
		}
		RandomState noiseConfig = world.getLevel().getChunkSource().randomState();
		IslandField field = field(noiseConfig, source);

		BlockPos origin = context.origin();
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
		BlockPos pos = new BlockPos(x, world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
		return world.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
				.getOptional(POOL)
				.map(pool -> pool.place(world, context.chunkGenerator(), context.random(), pos))
				.orElse(false);
	}

	private static IslandField field(RandomState noiseConfig, PreferredBiomeSource source) {
		return new IslandField(source.islandSize(), source.islandFrequency(), source.islandNoise(),
				IslandField.Channel.HEIGHT,
				seeded(noiseConfig, IslandTerrain.ISLAND_JITTER),
				seeded(noiseConfig, IslandTerrain.ISLAND_SHAPE),
				seeded(noiseConfig, Noises.SHIFT.identifier()),
				seeded(noiseConfig, Noises.TEMPERATURE.identifier()));
	}

	private static DensityFunction.NoiseHolder seeded(RandomState noiseConfig, Identifier id) {
		ResourceKey<NormalNoise.NoiseParameters> key =
				ResourceKey.create(Registries.NOISE, id);
		return new DensityFunction.NoiseHolder(null, noiseConfig.getOrCreateNoise(key));
	}
}
