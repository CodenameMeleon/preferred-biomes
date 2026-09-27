package net.codenamemeleon.preferredbiomes.client.mixin;

import net.codenamemeleon.preferredbiomes.client.gui.PreferredBiomesScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldCreationUiState.class)
public abstract class WorldCreatorMixin {

	@Unique
	private static final ResourceKey<WorldPreset> PREFERRED_BIOMES = ResourceKey.create(
			Registries.WORLD_PRESET, Identifier.fromNamespaceAndPath("preferred-biomes", "preferred_biomes"));

	@Shadow
	public abstract WorldCreationUiState.WorldTypeEntry getWorldType();

	@Inject(method = "getPresetEditor", at = @At("RETURN"), cancellable = true)
	private void preferredBiomes$customize(CallbackInfoReturnable<PresetEditor> cir) {
		Holder<WorldPreset> preset = this.getWorldType().preset();
		if (preset == null || !preset.unwrapKey().filter(PREFERRED_BIOMES::equals).isPresent()) {
			return;
		}
		cir.setReturnValue(PreferredBiomesScreen::new);
	}
}
