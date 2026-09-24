package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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

public class BareIslandTreeFeature extends Feature<DefaultFeatureConfig> {

	private static final double TREE_CHANCE = 0.4;

	private static final int ROLL_HAS_TREE = 8;

	private static final int ROLL_SPECIES = 9;

	private static final int SEARCH_RADIUS = 6;

	private static final RegistryKey<ConfiguredFeature<?, ?>>[] SPECIES = species(
			"oak", "birch", "spruce");

	@SafeVarargs
	private static RegistryKey<ConfiguredFeature<?, ?>>[] species(String... names) {
		@SuppressWarnings("unchecked")
		RegistryKey<ConfiguredFeature<?, ?>>[] keys = new RegistryKey[names.length];
		for (int i = 0; i < names.length; i++) {
			keys[i] = RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, new Identifier(names[i]));
		}
		return keys;
	}

	public BareIslandTreeFeature(Codec<DefaultFeatureConfig> codec) {
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
		RegistryKey<ConfiguredFeature<?, ?>> key =
				SPECIES[Math.min((int) (pick * SPECIES.length), SPECIES.length - 1)];
		return world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE)
				.getOrEmpty(key)
				.map(tree -> tree.generate(world, context.getGenerator(), context.getRandom(), ground))
				.orElse(false);
	}

	private static BlockPos findGround(StructureWorldAccess world, int centreX, int centreZ) {
		for (int r = 0; r <= SEARCH_RADIUS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (r > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = centreX + dx;
					int z = centreZ + dz;
					int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
					BlockState below = world.getBlockState(new BlockPos(x, y - 1, z));
					if (below.isOf(Blocks.GRASS_BLOCK) || below.isOf(Blocks.DIRT)) {
						return new BlockPos(x, y, z);
					}
				}
			}
		}
		return null;
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
