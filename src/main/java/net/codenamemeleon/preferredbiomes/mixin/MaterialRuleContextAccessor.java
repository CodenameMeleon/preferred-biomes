package net.codenamemeleon.preferredbiomes.mixin;

import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SurfaceRules.Context.class)
public interface MaterialRuleContextAccessor {

	@Accessor("randomState")
	RandomState getNoiseConfig();

	@Accessor("blockX")
	int getBlockX();

	@Accessor("blockZ")
	int getBlockZ();
}
