package net.codenamemeleon.preferredbiomes;

import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.biome.Biome;

public final class PreferredBiomeTags {

	public static final TagKey<Biome> LUSH_ISLANDS = TagKey.of(RegistryKeys.BIOME,
			PreferredBiomes.id("lush_islands"));

	private PreferredBiomeTags() {
	}
}
