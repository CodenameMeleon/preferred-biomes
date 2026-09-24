package net.codenamemeleon.preferredbiomes.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SporeBlossomBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SporeBlossomBlock.class)
public abstract class SporeBlossomAnchorMixin {
	@Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
	private void preferredBiomes$flowering(BlockState state, WorldView world, BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		if (world.getBlockState(pos.up()).isOf(Blocks.FLOWERING_AZALEA_LEAVES)
				&& world.getFluidState(pos).isEmpty()) {
			cir.setReturnValue(true);
		}
	}
}
