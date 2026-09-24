package net.codenamemeleon.preferredbiomes;

import net.codenamemeleon.preferredbiomes.worldgen.AzaleaHangingsFeature;
import net.codenamemeleon.preferredbiomes.worldgen.BareIslandTreeFeature;
import net.codenamemeleon.preferredbiomes.worldgen.IslandBiomes;
import net.codenamemeleon.preferredbiomes.worldgen.IslandField;
import net.codenamemeleon.preferredbiomes.worldgen.IslandShoreCondition;
import net.codenamemeleon.preferredbiomes.worldgen.LushIslandPoolFeature;
import net.codenamemeleon.preferredbiomes.worldgen.PreferredBiomeSource;
import net.fabricmc.api.ModInitializer;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreferredBiomes implements ModInitializer {
	public static final String MOD_ID = "preferred-biomes";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Registry.register(Registries.BIOME_SOURCE, id("preferred"), PreferredBiomeSource.CODEC);

		Registry.register(Registries.DENSITY_FUNCTION_TYPE, id("island_field"),
				IslandField.CODEC_HOLDER.codec());

		Registry.register(Registries.MATERIAL_CONDITION, id("island_shore"),
				IslandShoreCondition.CODEC_HOLDER.codec());

		Registry.register(Registries.FEATURE, id("azalea_hangings"),
				new AzaleaHangingsFeature(DefaultFeatureConfig.CODEC));
		Registry.register(Registries.FEATURE, id("bare_island_tree"),
				new BareIslandTreeFeature(DefaultFeatureConfig.CODEC));
		Registry.register(Registries.FEATURE, id("lush_island_pool_anchor"),
				new LushIslandPoolFeature(DefaultFeatureConfig.CODEC));

		SpawnRestriction.register(EntityType.ALLAY, SpawnRestriction.Location.NO_RESTRICTIONS,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				(type, world, reason, pos, random) ->
						world.getBiome(pos).matchesKey(IslandBiomes.DARK_FOREST_ISLAND)
								&& world.toServerWorld().isDay());

		if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			net.codenamemeleon.preferredbiomes.command.ColumnCommand.register();
		}

		LOGGER.info("Preferred Biomes initialized");
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
