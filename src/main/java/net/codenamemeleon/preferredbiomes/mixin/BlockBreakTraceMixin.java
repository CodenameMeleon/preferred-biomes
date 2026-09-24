package net.codenamemeleon.preferredbiomes.mixin;

import net.codenamemeleon.preferredbiomes.PreferredBiomes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class BlockBreakTraceMixin {
	private static final boolean TRACE = Boolean.getBoolean("pb.traceBreaks");

	@Inject(method = "onBlockChanged", at = @At("HEAD"))
	private void preferredBiomes$traceBreak(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		if (!TRACE || !newState.isAir()) {
			return;
		}
		boolean watched = oldState.isOf(Blocks.SPORE_BLOSSOM)
				|| oldState.isOf(Blocks.CAVE_VINES) || oldState.isOf(Blocks.CAVE_VINES_PLANT)
				|| oldState.isOf(Blocks.AZALEA_LEAVES) || oldState.isOf(Blocks.FLOWERING_AZALEA_LEAVES);
		if (!watched) {
			return;
		}
		ServerWorld world = (ServerWorld) (Object) this;
		BlockState above = world.getBlockState(pos.up());
		BlockState below = world.getBlockState(pos.down());
		String leaf = oldState.getBlock() instanceof LeavesBlock
				? String.format(" distance=%d persistent=%s",
						oldState.get(LeavesBlock.DISTANCE), oldState.get(LeavesBlock.PERSISTENT))
				: "";
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		StringBuilder who = new StringBuilder();
		for (int i = 3; i < Math.min(stack.length, 9); i++) {
			who.append("\n      ").append(stack[i]);
		}
		PreferredBiomes.LOGGER.warn("[PB-BREAK] {} at {} {} {} biome={}{} above={} below={} tick={}{}",
				Registries.BLOCK.getId(oldState.getBlock()).getPath(),
				pos.getX(), pos.getY(), pos.getZ(),
				world.getBiome(pos).getKey().map(k -> k.getValue().toString()).orElse("?"),
				leaf,
				Registries.BLOCK.getId(above.getBlock()).getPath(),
				Registries.BLOCK.getId(below.getBlock()).getPath(),
				world.getTime(), who);
	}
}
