package net.codenamemeleon.preferredbiomes.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrowingPlantBlock.class)
public abstract class CaveVinesAnchorMixin {
	@Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
	private void preferredBiomes$azalea(BlockState state, LevelReader world, BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof CaveVines)) {
			return;
		}
		BlockState above = world.getBlockState(pos.above());
		if (above.is(Blocks.AZALEA_LEAVES) || above.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
			cir.setReturnValue(true);
		}
	}
}
