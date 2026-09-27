package net.codenamemeleon.preferredbiomes.client;

import com.mojang.datafixers.util.Either;
import net.codenamemeleon.preferredbiomes.PreferredBiomes;
import net.codenamemeleon.preferredbiomes.worldgen.IslandTerrain;
import net.codenamemeleon.preferredbiomes.worldgen.PreferredBiomeSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.util.ArrayList;
import java.util.List;

public final class DebugWorldRunner {

	private static final java.nio.file.Path TRIGGER =
			java.nio.file.Path.of("pb-autorun.properties");

	private static final int CHUNK_TIMEOUT_TICKS = 600;

	private static final ResourceKey<net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList>
			OVERWORLD_PARAMETERS = ResourceKey.create(
					Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, Identifier.parse("overworld"));

	private static String tpArgument;
	private static final List<String> commands = new ArrayList<>();

	private static int islandSize = PreferredBiomeSource.DEFAULT_ISLAND_SIZE;
	private static float islandFrequency = PreferredBiomeSource.DEFAULT_ISLAND_FREQUENCY;
	private static int islandNoise = PreferredBiomeSource.DEFAULT_ISLAND_NOISE;

	private static boolean created;
	private static boolean teleported;
	private static boolean ran;
	private static int settleTicks;
	private static int waitTicks;
	private static int targetX;
	private static int targetZ;

	private DebugWorldRunner() {
	}

	public static void register() {
		if (!Boolean.getBoolean("pb.autorun")) {
			return;
		}
		if (!java.nio.file.Files.isRegularFile(TRIGGER)) {
			return;
		}
		java.util.Properties properties = new java.util.Properties();
		try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(TRIGGER)) {
			properties.load(reader);
			java.nio.file.Files.delete(TRIGGER);
		} catch (java.io.IOException e) {
			PreferredBiomes.LOGGER.warn("[PB-AUTORUN] could not read {}", TRIGGER, e);
			return;
		}
		String seedText = properties.getProperty("seed");
		if (seedText == null) {
			return;
		}
		long seed = Long.parseLong(seedText.trim());
		tpArgument = properties.getProperty("tp");
		if (properties.getProperty("size") != null) {
			islandSize = Integer.parseInt(properties.getProperty("size").trim());
		}
		if (properties.getProperty("frequency") != null) {
			islandFrequency = Float.parseFloat(properties.getProperty("frequency").trim());
		}
		if (properties.getProperty("noise") != null) {
			islandNoise = Integer.parseInt(properties.getProperty("noise").trim());
		}
		for (int i = 1; i <= 9; i++) {
			String key = i == 1 ? "command" : "command" + i;
			String value = properties.getProperty(key);
			if (value != null) {
				commands.add(value);
			}
		}
		PreferredBiomes.LOGGER.info(
				"[PB-AUTORUN] armed, seed {}, size {}, frequency {}, noise {}, tp {}, {} command(s)",
				seed, islandSize, islandFrequency, islandNoise, tpArgument, commands.size());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!created && client.screen instanceof TitleScreen) {
				created = true;
				createWorld(client, seed);
				return;
			}
			if (created && !ran && client.getSingleplayerServer() != null && client.player != null) {
				if (!teleported) {
					if (++settleTicks < 40) {
						return;
					}
					teleported = true;
					teleport(client.getSingleplayerServer());
					return;
				}
				waitTicks++;
				MinecraftServer server = client.getSingleplayerServer();
				if (chunksReady(server)) {
					ran = true;
					PreferredBiomes.LOGGER.info("[PB-AUTORUN] chunks ready after {} ticks", waitTicks);
					runCommands(server);
				} else if (waitTicks >= CHUNK_TIMEOUT_TICKS) {
					ran = true;
					PreferredBiomes.LOGGER.warn(
							"[PB-AUTORUN] TIMED OUT after {} ticks waiting for chunks around {} {} -"
									+ " results below are not trustworthy", waitTicks, targetX, targetZ);
					runCommands(server);
				}
			}
		});
	}

	private static void createWorld(Minecraft client, long seed) {
		PreferredBiomes.LOGGER.info("[PB-AUTORUN] creating world");
		LevelSettings levelInfo = new LevelSettings("pb-autorun", GameType.CREATIVE,
				new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
				WorldDataConfiguration.DEFAULT);
		client.createWorldOpenFlows().createFreshLevel("pb-autorun", levelInfo,
				new WorldOptions(seed, true, false), DebugWorldRunner::dimensions, client.screen);
	}

	private static WorldDimensions dimensions(HolderLookup.Provider registryManager) {
		WorldPreset normal = registryManager.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value();
		WorldDimensions base = normal.createWorldDimensions();

		int size = islandSize;
		float frequency = islandFrequency;
		int noise = islandNoise;
		var parameters = registryManager.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
				.getOrThrow(OVERWORLD_PARAMETERS);
		var settings = Holder.direct(
				IslandTerrain.createSettings(registryManager, size, frequency, noise));
		var source = new PreferredBiomeSource(
				Either.left(net.codenamemeleon.preferredbiomes.worldgen.IslandBiomes.entries(
						registryManager.lookupOrThrow(Registries.BIOME))),
				List.of(), true, size, frequency, noise, List.of());
		return base.replaceOverworldGenerator(registryManager, new NoiseBasedChunkGenerator(source, settings));
	}

	private static void teleport(MinecraftServer server) {
		if (tpArgument == null) {
			return;
		}
		String[] parts = tpArgument.trim().split("\\s+");
		if (parts.length >= 3) {
			targetX = (int) Math.floor(Double.parseDouble(parts[0]));
			targetZ = (int) Math.floor(Double.parseDouble(parts[2]));
		}
		server.execute(() -> dispatch(server, "tp @a " + tpArgument));
	}

	private static boolean chunksReady(MinecraftServer server) {
		ServerLevel world = server.overworld();
		int cx = targetX >> 4;
		int cz = targetZ >> 4;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (world.getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false) == null) {
					return false;
				}
			}
		}
		return true;
	}

	private static void runCommands(MinecraftServer server) {
		server.execute(() -> {
			for (String command : commands) {
				dispatch(server, command);
			}
			PreferredBiomes.LOGGER.info("[PB-AUTORUN] done");
		});
	}

	private static void dispatch(MinecraftServer server, String command) {
		PreferredBiomes.LOGGER.info("[PB-AUTORUN] > {}", command);
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}
}
