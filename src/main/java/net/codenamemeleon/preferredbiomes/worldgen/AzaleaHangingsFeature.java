package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CaveVines;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

public class AzaleaHangingsFeature extends Feature<DefaultFeatureConfig> {

	private static final float VINE_CHANCE = 0.35F;

	private static final float BERRY_CHANCE = 0.5F;

	private static final float BLOSSOM_CHANCE = 0.5F;

	private static final int VINE_MIN = 1;
	private static final int VINE_MAX = 2;

	public AzaleaHangingsFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		ChunkPos chunk = new ChunkPos(context.getOrigin());
		int floor = world.getSeaLevel();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		boolean placed = false;

		for (int x = chunk.getStartX(); x <= chunk.getEndX(); x++) {
			for (int z = chunk.getStartZ(); z <= chunk.getEndZ(); z++) {
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				for (int y = top; y >= floor; y--) {
					pos.set(x, y, z);
					BlockState state = world.getBlockState(pos);
					boolean plain = state.isOf(Blocks.AZALEA_LEAVES);
					if (!plain && !state.isOf(Blocks.FLOWERING_AZALEA_LEAVES)) {
						continue;
					}
					if (!state.get(LeavesBlock.PERSISTENT) && state.get(LeavesBlock.DISTANCE) >= LeavesBlock.MAX_DISTANCE) {
						continue;
					}
					if (!world.getBlockState(pos.down()).isAir()) {
						continue;
					}
					if (plain) {
						placed |= vine(world, random, pos.down());
					} else if (random.nextFloat() < BLOSSOM_CHANCE) {
						world.setBlockState(pos.down(),
								Blocks.SPORE_BLOSSOM.getDefaultState(), Block.NOTIFY_LISTENERS);
						placed = true;
					}
				}
			}
		}
		return placed;
	}

	private static boolean vine(StructureWorldAccess world, Random random, BlockPos start) {
		if (random.nextFloat() >= VINE_CHANCE) {
			return false;
		}
		int length = VINE_MIN + random.nextInt(VINE_MAX - VINE_MIN + 1);
		BlockPos.Mutable pos = start.mutableCopy();
		for (int i = 0; i < length; i++) {
			if (!world.getBlockState(pos).isAir()) {
				return i > 0;
			}
			boolean head = i == length - 1;
			BlockState state = head
					? Blocks.CAVE_VINES.getDefaultState()
							.with(CaveVines.BERRIES, random.nextFloat() < BERRY_CHANCE)
					: Blocks.CAVE_VINES_PLANT.getDefaultState();
			world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
			pos.move(0, -1, 0);
		}
		return true;
	}
}
