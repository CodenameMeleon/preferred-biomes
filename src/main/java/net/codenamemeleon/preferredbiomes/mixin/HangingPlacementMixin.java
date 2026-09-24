package net.codenamemeleon.preferredbiomes.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class HangingPlacementMixin {
	@Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
	private void preferredBiomes$noHandPlacing(ItemPlacementContext context, BlockState state,
			CallbackInfoReturnable<Boolean> cir) {
		if (!state.isOf(Blocks.SPORE_BLOSSOM) && !state.isOf(Blocks.CAVE_VINES)) {
			return;
		}
		BlockState above = context.getWorld().getBlockState(context.getBlockPos().up());
		if (above.isOf(Blocks.AZALEA_LEAVES) || above.isOf(Blocks.FLOWERING_AZALEA_LEAVES)) {
			cir.setReturnValue(false);
		}
	}
}
