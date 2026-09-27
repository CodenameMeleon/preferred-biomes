package net.codenamemeleon.preferredbiomes.client.mixin;

import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Biome.class)
public interface BiomeWeatherAccessor {

	@Accessor("climateSettings")
	Biome.ClimateSettings getWeather();
}
