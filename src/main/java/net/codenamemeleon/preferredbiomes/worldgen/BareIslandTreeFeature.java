package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class BareIslandTreeFeature extends Feature<NoneFeatureConfiguration> {

	private static final double TREE_CHANCE = 0.4;

	private static final int ROLL_HAS_TREE = 8;

	private static final int ROLL_SPECIES = 9;

	private static final int SEARCH_RADIUS = 6;

	private static final ResourceKey<ConfiguredFeature<?, ?>>[] SPECIES = species(
			"oak", "birch", "spruce");

	@SafeVarargs
	private static ResourceKey<ConfiguredFeature<?, ?>>[] species(String... names) {
		@SuppressWarnings("unchecked")
		ResourceKey<ConfiguredFeature<?, ?>>[] keys = new ResourceKey[names.length];
		for (int i = 0; i < names.length; i++) {
			keys[i] = ResourceKey.create(Registries.CONFIGURED_FEATURE, Identifier.parse(names[i]));
		}
		return keys;
	}

	public BareIslandTreeFeature(Codec<NoneFeatureConfiguration> codec) {
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
		int centreX = anchor.getX();
		int centreZ = anchor.getZ();
		if (field.rollAt(centreX, centreZ, ROLL_HAS_TREE) >= TREE_CHANCE) {
			return false;
		}

		BlockPos ground = findGround(world, centreX, centreZ);
		if (ground == null) {
			return false;
		}

		double pick = field.rollAt(centreX, centreZ, ROLL_SPECIES);
		ResourceKey<ConfiguredFeature<?, ?>> key =
				SPECIES[Math.min((int) (pick * SPECIES.length), SPECIES.length - 1)];
		return world.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE)
				.getOptional(key)
				.map(tree -> tree.place(world, context.chunkGenerator(), context.random(), ground))
				.orElse(false);
	}

	private static BlockPos findGround(WorldGenLevel world, int centreX, int centreZ) {
		for (int r = 0; r <= SEARCH_RADIUS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (r > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = centreX + dx;
					int z = centreZ + dz;
					int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					BlockState below = world.getBlockState(new BlockPos(x, y - 1, z));
					if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT)) {
						return new BlockPos(x, y, z);
					}
				}
			}
		}
		return null;
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
