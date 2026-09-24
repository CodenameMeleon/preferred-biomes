package net.codenamemeleon.preferredbiomes.client.mixin;

import net.codenamemeleon.preferredbiomes.client.gui.PreferredBiomesScreen;
import net.minecraft.client.gui.screen.world.LevelScreenProvider;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldCreator.class)
public abstract class WorldCreatorMixin {

	@Unique
	private static final RegistryKey<WorldPreset> PREFERRED_BIOMES = RegistryKey.of(
			RegistryKeys.WORLD_PRESET, new Identifier("preferred-biomes", "preferred_biomes"));

	@Shadow
	public abstract WorldCreator.WorldType getWorldType();

	@Inject(method = "getLevelScreenProvider", at = @At("RETURN"), cancellable = true)
	private void preferredBiomes$customize(CallbackInfoReturnable<LevelScreenProvider> cir) {
		RegistryEntry<WorldPreset> preset = this.getWorldType().preset();
		if (preset == null || !preset.getKey().filter(PREFERRED_BIOMES::equals).isPresent()) {
			return;
		}
		cir.setReturnValue(PreferredBiomesScreen::new);
	}
}
