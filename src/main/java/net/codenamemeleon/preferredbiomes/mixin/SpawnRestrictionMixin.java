package net.codenamemeleon.preferredbiomes.mixin;

import net.codenamemeleon.preferredbiomes.worldgen.IslandBiomes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnRestriction.class)
public abstract class SpawnRestrictionMixin {

	@Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true)
	private static void preferredBiomes$vexAtNight(EntityType<?> type, ServerWorldAccess world,
			SpawnReason spawnReason, BlockPos pos, Random random,
			CallbackInfoReturnable<Boolean> info) {
		if (type == EntityType.VEX
				&& world.getBiome(pos).matchesKey(IslandBiomes.DARK_FOREST_ISLAND)) {
			info.setReturnValue(!world.toServerWorld().isDay());
		}
	}
}
