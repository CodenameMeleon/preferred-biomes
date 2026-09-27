package net.codenamemeleon.preferredbiomes.mixin;

import com.mojang.datafixers.util.Either;
import net.codenamemeleon.preferredbiomes.worldgen.StructureGate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
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

	@Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void preferredBiomes$gate(Structure.GenerationContext context,
			CallbackInfoReturnable<Optional<Structure.GenerationStub>> info) {
		if (!StructureGate.applies(context.chunkGenerator())) {
			return;
		}
		String path = StructureGate.pathOf(context.registryAccess(), (Structure) (Object) this);
		if (path != null && StructureGate.isGated(path)) {
			info.setReturnValue(Optional.empty());
		}
	}

	@Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
	private void preferredBiomes$requireLand(Structure.GenerationContext context,
			CallbackInfoReturnable<Optional<Structure.GenerationStub>> info) {
		String path = preferredBiomes$gatedPath(context);
		if (path == null || !StructureGate.needsLand(path)) {
			return;
		}
		Optional<Structure.GenerationStub> found = info.getReturnValue();
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

	@Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
	private void preferredBiomes$sinkToFloor(Structure.GenerationContext context,
			CallbackInfoReturnable<Optional<Structure.GenerationStub>> info) {
		String path = preferredBiomes$gatedPath(context);
		if (path == null || !StructureGate.sinksToFloor(path)) {
			return;
		}
		Optional<Structure.GenerationStub> found = info.getReturnValue();
		if (found.isEmpty()) {
			return;
		}
		Structure.GenerationStub position = found.get();
		StructurePiecesBuilder pieces = position.getPiecesBuilder();
		if (pieces.isEmpty()) {
			return;
		}
		BoundingBox box = pieces.getBoundingBox();
		BlockPos centre = box.getCenter();
		pieces.offsetPiecesVertically(preferredBiomes$floorAt(context, centre.getX(), centre.getZ()) - box.maxY());
		info.setReturnValue(Optional.of(
				new Structure.GenerationStub(position.position(), Either.right(pieces))));
	}

	private static int preferredBiomes$floorAt(Structure.GenerationContext context, int x, int z) {
		return context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
				context.heightAccessor(), context.randomState()) - 1;
	}

	private String preferredBiomes$gatedPath(Structure.GenerationContext context) {
		return StructureGate.applies(context.chunkGenerator())
				? StructureGate.pathOf(context.registryAccess(), (Structure) (Object) this)
				: null;
	}

	@ModifyVariable(method = "findValidGenerationPoint", at = @At("HEAD"), argsOnly = true)
	private Structure.GenerationContext preferredBiomes$widen(Structure.GenerationContext context) {
		if (!StructureGate.applies(context.chunkGenerator())) {
			return context;
		}
		String path = StructureGate.pathOf(context.registryAccess(), (Structure) (Object) this);
		if (path == null || !StructureGate.isReassigned(path)) {
			return context;
		}
		return new Structure.GenerationContext(
				context.registryAccess(),
				context.chunkGenerator(),
				context.biomeSource(),
				context.randomState(),
				context.structureTemplateManager(),
				context.random(),
				context.seed(),
				context.chunkPos(),
				context.heightAccessor(),
				StructureGate.widen(path, context.validBiome()));
	}
}
