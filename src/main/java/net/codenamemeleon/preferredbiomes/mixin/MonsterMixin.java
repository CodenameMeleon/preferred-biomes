package net.codenamemeleon.preferredbiomes.mixin;

import net.codenamemeleon.preferredbiomes.worldgen.IslandBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Monster.class)
public abstract class MonsterMixin {

	@Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
	private static void preferredBiomes$alwaysDark(ServerLevelAccessor world, BlockPos pos,
			RandomSource random, CallbackInfoReturnable<Boolean> info) {
		if (world.getBiome(pos).is(IslandBiomes.DARK_FOREST_ISLAND)) {
			info.setReturnValue(true);
		}
	}
}
