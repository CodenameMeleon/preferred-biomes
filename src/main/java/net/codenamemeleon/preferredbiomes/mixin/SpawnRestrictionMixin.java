package net.codenamemeleon.preferredbiomes.mixin;

import net.codenamemeleon.preferredbiomes.worldgen.IslandBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnPlacements.class)
public abstract class SpawnRestrictionMixin {

	@Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
	private static void preferredBiomes$vexAtNight(EntityType<?> type, ServerLevelAccessor world,
			EntitySpawnReason spawnReason, BlockPos pos, RandomSource random,
			CallbackInfoReturnable<Boolean> info) {
		if (type == EntityTypes.VEX
				&& world.getBiome(pos).is(IslandBiomes.DARK_FOREST_ISLAND)) {
			info.setReturnValue(!world.getLevel().isBrightOutside());
		}
	}
}
