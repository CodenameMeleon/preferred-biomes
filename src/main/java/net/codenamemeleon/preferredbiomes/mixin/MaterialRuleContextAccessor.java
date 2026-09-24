package net.codenamemeleon.preferredbiomes.mixin;

import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MaterialRules.MaterialRuleContext.class)
public interface MaterialRuleContextAccessor {

	@Accessor("noiseConfig")
	NoiseConfig getNoiseConfig();

	@Accessor("blockX")
	int getBlockX();

	@Accessor("blockZ")
	int getBlockZ();
}
