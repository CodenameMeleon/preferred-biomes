package net.codenamemeleon.preferredbiomes;

import net.codenamemeleon.preferredbiomes.worldgen.AzaleaHangingsFeature;
import net.codenamemeleon.preferredbiomes.worldgen.BareIslandTreeFeature;
import net.codenamemeleon.preferredbiomes.worldgen.IslandBiomes;
import net.codenamemeleon.preferredbiomes.worldgen.IslandField;
import net.codenamemeleon.preferredbiomes.worldgen.IslandShoreCondition;
import net.codenamemeleon.preferredbiomes.worldgen.LushIslandPoolFeature;
import net.codenamemeleon.preferredbiomes.worldgen.PreferredBiomeSource;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreferredBiomes implements ModInitializer {
	public static final String MOD_ID = "preferred-biomes";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.BIOME_SOURCE, id("preferred"), PreferredBiomeSource.CODEC);

		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("island_field"),
				IslandField.CODEC_HOLDER.codec());

		Registry.register(BuiltInRegistries.MATERIAL_CONDITION, id("island_shore"),
				IslandShoreCondition.CODEC_HOLDER.codec());

		Registry.register(BuiltInRegistries.FEATURE, id("azalea_hangings"),
				new AzaleaHangingsFeature(NoneFeatureConfiguration.CODEC));
		Registry.register(BuiltInRegistries.FEATURE, id("bare_island_tree"),
				new BareIslandTreeFeature(NoneFeatureConfiguration.CODEC));
		Registry.register(BuiltInRegistries.FEATURE, id("lush_island_pool_anchor"),
				new LushIslandPoolFeature(NoneFeatureConfiguration.CODEC));

		SpawnPlacements.register(EntityType.ALLAY, SpawnPlacementTypes.NO_RESTRICTIONS,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(type, world, reason, pos, random) ->
						world.getBiome(pos).is(IslandBiomes.DARK_FOREST_ISLAND)
								&& world.getLevel().isBrightOutside());

		if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			net.codenamemeleon.preferredbiomes.command.ColumnCommand.register();
		}

		LOGGER.info("Preferred Biomes initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
