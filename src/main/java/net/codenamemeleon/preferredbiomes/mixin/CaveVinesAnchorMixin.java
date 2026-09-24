package net.codenamemeleon.preferredbiomes.mixin;

import net.minecraft.block.AbstractPlantPartBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CaveVines;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractPlantPartBlock.class)
public abstract class CaveVinesAnchorMixin {
	@Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
	private void preferredBiomes$azalea(BlockState state, WorldView world, BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof CaveVines)) {
			return;
		}
		BlockState above = world.getBlockState(pos.up());
		if (above.isOf(Blocks.AZALEA_LEAVES) || above.isOf(Blocks.FLOWERING_AZALEA_LEAVES)) {
			cir.setReturnValue(true);
		}
	}
}
