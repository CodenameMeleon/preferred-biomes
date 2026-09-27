package net.codenamemeleon.preferredbiomes.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SporeBlossomBlock.class)
public abstract class SporeBlossomAnchorMixin {
	@Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
	private void preferredBiomes$flowering(BlockState state, LevelReader world, BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		if (world.getBlockState(pos.above()).is(Blocks.FLOWERING_AZALEA_LEAVES)
				&& world.getFluidState(pos).isEmpty()) {
			cir.setReturnValue(true);
		}
	}
}
