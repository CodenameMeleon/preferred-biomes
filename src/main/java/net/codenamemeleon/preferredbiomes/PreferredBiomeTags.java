package net.codenamemeleon.preferredbiomes;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public final class PreferredBiomeTags {

	public static final TagKey<Biome> LUSH_ISLANDS = TagKey.create(Registries.BIOME,
			PreferredBiomes.id("lush_islands"));

	private PreferredBiomeTags() {
	}
}
