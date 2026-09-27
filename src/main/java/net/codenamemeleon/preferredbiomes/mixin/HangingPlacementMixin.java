package net.codenamemeleon.preferredbiomes.mixin;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class HangingPlacementMixin {
	@Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
	private void preferredBiomes$noHandPlacing(BlockPlaceContext context, BlockState state,
			CallbackInfoReturnable<Boolean> cir) {
		if (!state.is(Blocks.SPORE_BLOSSOM) && !state.is(Blocks.CAVE_VINES)) {
			return;
		}
		BlockState above = context.getLevel().getBlockState(context.getClickedPos().above());
		if (above.is(Blocks.AZALEA_LEAVES) || above.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
			cir.setReturnValue(false);
		}
	}
}
