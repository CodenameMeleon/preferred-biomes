package net.codenamemeleon.preferredbiomes.worldgen;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class StructureGate {

	private StructureGate() {
	}

	private static final Set<String> GATED = Set.of(
			"village_plains",
			"village_desert",
			"village_savanna",
			"village_taiga",
			"village_snowy",
			"desert_pyramid",
			"jungle_pyramid",
			"mansion",
			"mineshaft_mesa");

	private static final Map<String, Predicate<RegistryEntry<Biome>>> REASSIGNED = Map.of(
			"igloo", biome -> biome.matchesKey(BiomeKeys.FROZEN_OCEAN)
					|| biome.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN),
			"swamp_hut", biome -> biome.matchesKey(BiomeKeys.SPARSE_JUNGLE)
					|| biome.matchesKey(BiomeKeys.BAMBOO_JUNGLE)
					|| biome.matchesKey(IslandBiomes.MANGROVE_ISLAND),
			"buried_treasure", biome -> true,
			"trail_ruins", biome -> biome.isIn(BiomeTags.IS_OCEAN));

	public static boolean applies(ChunkGenerator generator) {
		return generator.getBiomeSource() instanceof PreferredBiomeSource source
				&& source.islandSurvivalChallenge();
	}

	public static String pathOf(DynamicRegistryManager registryManager, Structure structure) {
		Identifier id = registryManager.get(RegistryKeys.STRUCTURE).getId(structure);
		return id != null && Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())
				? id.getPath()
				: null;
	}

	public static boolean isGated(String path) {
		return GATED.contains(path);
	}

	public static boolean isReassigned(String path) {
		return REASSIGNED.containsKey(path);
	}

	private static final Set<String> NEEDS_LAND = Set.of("igloo", "swamp_hut");

	public static boolean needsLand(String path) {
		return NEEDS_LAND.contains(path);
	}

	public static boolean sinksToFloor(String path) {
		return "trail_ruins".equals(path);
	}

	public static Predicate<RegistryEntry<Biome>> widen(String path,
			Predicate<RegistryEntry<Biome>> original) {
		Predicate<RegistryEntry<Biome>> extra = REASSIGNED.get(path);
		return extra == null ? original : original.or(extra);
	}
}
