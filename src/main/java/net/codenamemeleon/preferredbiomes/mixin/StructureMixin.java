package net.codenamemeleon.preferredbiomes.mixin;

import com.mojang.datafixers.util.Either;
import net.codenamemeleon.preferredbiomes.worldgen.StructureGate;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Structure.class)
public abstract class StructureMixin {

	private static final int LAND_RADIUS = 6;

	private static final int SEA_LEVEL = 63;

	@Inject(method = "getValidStructurePosition", at = @At("HEAD"), cancellable = true)
	private void preferredBiomes$gate(Structure.Context context,
			CallbackInfoReturnable<Optional<Structure.StructurePosition>> info) {
		if (!StructureGate.applies(context.chunkGenerator())) {
			return;
		}
		String path = StructureGate.pathOf(context.dynamicRegistryManager(), (Structure) (Object) this);
		if (path != null && StructureGate.isGated(path)) {
			info.setReturnValue(Optional.empty());
		}
	}

	@Inject(method = "getValidStructurePosition", at = @At("RETURN"), cancellable = true)
	private void preferredBiomes$requireLand(Structure.Context context,
			CallbackInfoReturnable<Optional<Structure.StructurePosition>> info) {
		String path = preferredBiomes$gatedPath(context);
		if (path == null || !StructureGate.needsLand(path)) {
			return;
		}
		Optional<Structure.StructurePosition> found = info.getReturnValue();
		if (found.isEmpty()) {
			return;
		}
		BlockPos pos = found.get().position();
		for (int dx = -LAND_RADIUS; dx <= LAND_RADIUS; dx += LAND_RADIUS) {
			for (int dz = -LAND_RADIUS; dz <= LAND_RADIUS; dz += LAND_RADIUS) {
				if ((dx == 0) != (dz == 0)) {
					continue;
				}
				if (preferredBiomes$floorAt(context, pos.getX() + dx, pos.getZ() + dz) <= SEA_LEVEL) {
					info.setReturnValue(Optional.empty());
					return;
				}
			}
		}
	}

	@Inject(method = "getValidStructurePosition", at = @At("RETURN"), cancellable = true)
	private void preferredBiomes$sinkToFloor(Structure.Context context,
			CallbackInfoReturnable<Optional<Structure.StructurePosition>> info) {
		String path = preferredBiomes$gatedPath(context);
		if (path == null || !StructureGate.sinksToFloor(path)) {
			return;
		}
		Optional<Structure.StructurePosition> found = info.getReturnValue();
		if (found.isEmpty()) {
			return;
		}
		Structure.StructurePosition position = found.get();
		StructurePiecesCollector pieces = position.generate();
		if (pieces.isEmpty()) {
			return;
		}
		BlockBox box = pieces.getBoundingBox();
		BlockPos centre = box.getCenter();
		pieces.shift(preferredBiomes$floorAt(context, centre.getX(), centre.getZ()) - box.getMaxY());
		info.setReturnValue(Optional.of(
				new Structure.StructurePosition(position.position(), Either.right(pieces))));
	}

	private static int preferredBiomes$floorAt(Structure.Context context, int x, int z) {
		return context.chunkGenerator().getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG,
				context.world(), context.noiseConfig()) - 1;
	}

	private String preferredBiomes$gatedPath(Structure.Context context) {
		return StructureGate.applies(context.chunkGenerator())
				? StructureGate.pathOf(context.dynamicRegistryManager(), (Structure) (Object) this)
				: null;
	}

	@ModifyVariable(method = "getValidStructurePosition", at = @At("HEAD"), argsOnly = true)
	private Structure.Context preferredBiomes$widen(Structure.Context context) {
		if (!StructureGate.applies(context.chunkGenerator())) {
			return context;
		}
		String path = StructureGate.pathOf(context.dynamicRegistryManager(), (Structure) (Object) this);
		if (path == null || !StructureGate.isReassigned(path)) {
			return context;
		}
		return new Structure.Context(
				context.dynamicRegistryManager(),
				context.chunkGenerator(),
				context.biomeSource(),
				context.noiseConfig(),
				context.structureTemplateManager(),
				context.random(),
				context.seed(),
				context.chunkPos(),
				context.world(),
				StructureGate.widen(path, context.biomePredicate()));
	}
}
