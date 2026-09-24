package net.codenamemeleon.preferredbiomes.mixin;

import net.codenamemeleon.preferredbiomes.worldgen.IslandBiomes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HostileEntity.class)
public abstract class MonsterMixin {

	@Inject(method = "isSpawnDark", at = @At("HEAD"), cancellable = true)
	private static void preferredBiomes$alwaysDark(ServerWorldAccess world, BlockPos pos,
			Random random, CallbackInfoReturnable<Boolean> info) {
		if (world.getBiome(pos).matchesKey(IslandBiomes.DARK_FOREST_ISLAND)) {
			info.setReturnValue(true);
		}
	}
}
