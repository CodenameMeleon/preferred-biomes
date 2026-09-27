package net.codenamemeleon.preferredbiomes.worldgen;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;

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

	private static final Map<String, Predicate<Holder<Biome>>> REASSIGNED = Map.of(
			"igloo", biome -> biome.is(Biomes.FROZEN_OCEAN)
					|| biome.is(Biomes.DEEP_FROZEN_OCEAN),
			"swamp_hut", biome -> biome.is(Biomes.SPARSE_JUNGLE)
					|| biome.is(Biomes.BAMBOO_JUNGLE)
					|| biome.is(IslandBiomes.MANGROVE_ISLAND),
			"buried_treasure", biome -> true,
			"trail_ruins", biome -> biome.is(BiomeTags.IS_OCEAN));

	public static boolean applies(ChunkGenerator generator) {
		return generator.getBiomeSource() instanceof PreferredBiomeSource source
				&& source.islandSurvivalChallenge();
	}

	public static String pathOf(RegistryAccess registryManager, Structure structure) {
		Identifier id = registryManager.lookupOrThrow(Registries.STRUCTURE).getKey(structure);
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

	public static Predicate<Holder<Biome>> widen(String path,
			Predicate<Holder<Biome>> original) {
		Predicate<Holder<Biome>> extra = REASSIGNED.get(path);
		return extra == null ? original : original.or(extra);
	}
}
