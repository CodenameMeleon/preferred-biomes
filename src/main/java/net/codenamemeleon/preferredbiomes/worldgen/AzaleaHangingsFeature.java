package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class AzaleaHangingsFeature extends Feature<NoneFeatureConfiguration> {

	private static final float VINE_CHANCE = 0.35F;

	private static final float BERRY_CHANCE = 0.5F;

	private static final float BLOSSOM_CHANCE = 0.5F;

	private static final int VINE_MIN = 1;
	private static final int VINE_MAX = 2;

	public AzaleaHangingsFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel world = context.level();
		RandomSource random = context.random();
		ChunkPos chunk = new ChunkPos(context.origin());
		int floor = world.getSeaLevel();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;

		for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
			for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
				int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				for (int y = top; y >= floor; y--) {
					pos.set(x, y, z);
					BlockState state = world.getBlockState(pos);
					boolean plain = state.is(Blocks.AZALEA_LEAVES);
					if (!plain && !state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
						continue;
					}
					if (!state.getValue(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE) {
						continue;
					}
					if (!world.getBlockState(pos.below()).isAir()) {
						continue;
					}
					if (plain) {
						placed |= vine(world, random, pos.below());
					} else if (random.nextFloat() < BLOSSOM_CHANCE) {
						world.setBlock(pos.below(),
								Blocks.SPORE_BLOSSOM.defaultBlockState(), Block.UPDATE_CLIENTS);
						placed = true;
					}
				}
			}
		}
		return placed;
	}

	private static boolean vine(WorldGenLevel world, RandomSource random, BlockPos start) {
		if (random.nextFloat() >= VINE_CHANCE) {
			return false;
		}
		int length = VINE_MIN + random.nextInt(VINE_MAX - VINE_MIN + 1);
		BlockPos.MutableBlockPos pos = start.mutable();
		for (int i = 0; i < length; i++) {
			if (!world.getBlockState(pos).isAir()) {
				return i > 0;
			}
			boolean head = i == length - 1;
			BlockState state = head
					? Blocks.CAVE_VINES.defaultBlockState()
							.setValue(CaveVines.BERRIES, random.nextFloat() < BERRY_CHANCE)
					: Blocks.CAVE_VINES_PLANT.defaultBlockState();
			world.setBlock(pos, state, Block.UPDATE_CLIENTS);
			pos.move(0, -1, 0);
		}
		return true;
	}
}
