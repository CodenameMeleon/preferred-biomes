package net.codenamemeleon.preferredbiomes.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.codenamemeleon.preferredbiomes.worldgen.IslandField;
import net.codenamemeleon.preferredbiomes.worldgen.IslandTerrain;
import net.codenamemeleon.preferredbiomes.worldgen.PreferredBiomeSource;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.StructureWeightSampler;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ColumnCommand {

	private ColumnCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("pb")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.literal("column")
								.then(CommandManager.argument("x", IntegerArgumentType.integer())
										.then(CommandManager.argument("z", IntegerArgumentType.integer())
												.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 256))
														.executes(context -> column(
																context.getSource(),
																IntegerArgumentType.getInteger(context, "x"),
																IntegerArgumentType.getInteger(context, "z"),
																IntegerArgumentType.getInteger(context, "count")))))))
						.then(CommandManager.literal("city")
								.executes(context -> city(context.getSource())))
						.then(CommandManager.literal("probe")
								.then(CommandManager.argument("x", IntegerArgumentType.integer())
										.then(CommandManager.argument("z", IntegerArgumentType.integer())
												.executes(context -> probe(
														context.getSource(),
														IntegerArgumentType.getInteger(context, "x"),
														IntegerArgumentType.getInteger(context, "z"))))))
						.then(CommandManager.literal("palette")
								.executes(context -> palette(context.getSource())))
						.then(CommandManager.literal("shape")
								.executes(context -> shape(context.getSource())))
						.then(CommandManager.literal("tree")
								.executes(context -> tree(context.getSource())))
						.then(CommandManager.literal("rolls")
								.then(CommandManager.argument("x", IntegerArgumentType.integer())
										.then(CommandManager.argument("z", IntegerArgumentType.integer())
												.executes(context -> rolls(
														context.getSource(),
														IntegerArgumentType.getInteger(context, "x"),
														IntegerArgumentType.getInteger(context, "z"))))))));
	}

	private static final int TREE_ROLL = 8;
	private static final double TREE_CHANCE = 0.4;
	private static final int TREE_SEARCH = 6;

	private static PreferredBiomeSource sourceOf(ServerCommandSource source) {
		ChunkGenerator generator = source.getWorld().getChunkManager().getChunkGenerator();
		if (generator.getBiomeSource() instanceof PreferredBiomeSource island
				&& island.islandSurvivalChallenge()) {
			return island;
		}
		source.sendError(Text.literal("Not an Island Survival Challenge world."));
		return null;
	}

	private static int tree(ServerCommandSource source) {
		PreferredBiomeSource biomeSource = sourceOf(source);
		if (biomeSource == null) {
			return 0;
		}
		ServerWorld world = source.getWorld();
		NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
		IslandField field = IslandTerrain.seededField(world.getRegistryManager(), noiseConfig,
				biomeSource.islandSize(), biomeSource.islandFrequency(), biomeSource.islandNoise(),
				IslandField.Channel.HEIGHT);

		BlockPos origin = BlockPos.ofFloored(source.getPosition());
		double g = field.cellSize();
		int cx = (int) Math.floor(origin.getX() / g);
		int cz = (int) Math.floor(origin.getZ() / g);

		IslandField.Shape shape = field.shapeAt(origin.getX(), origin.getZ());
		if (shape == null) {
			source.sendFeedback(() -> Text.literal("cell " + cx + "," + cz + ": no island"), false);
			return 0;
		}
		BlockPos anchor = field.anchorLand(cx, cz);
		if (anchor == null) {
			source.sendFeedback(() -> Text.literal(String.format(
					"cell %d,%d radius %.1f: NO ANCHOR (no column 2+ blocks inland)",
					cx, cz, shape.radius())), false);
			return 1;
		}
		int x = anchor.getX();
		int z = anchor.getZ();
		double roll = field.rollAt(x, z, TREE_ROLL);
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockState below = world.getBlockState(new BlockPos(x, top - 1, z));
		String biome = world.getBiome(new BlockPos(x, top, z)).getKey()
				.map(key -> key.getValue().toString()).orElse("?");

		String ground = "none within " + TREE_SEARCH;
		outer:
		for (int r = 0; r <= TREE_SEARCH; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (r > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int gx = x + dx;
					int gz = z + dz;
					int gy = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, gx, gz);
					BlockState under = world.getBlockState(new BlockPos(gx, gy - 1, gz));
					if (under.isOf(Blocks.GRASS_BLOCK) || under.isOf(Blocks.DIRT)) {
						ground = "found at r=" + r + " (" + gx + "," + gy + "," + gz + ")";
						break outer;
					}
				}
			}
		}

		String line = String.format(
				"cell %d,%d  radius %.1f  anchor %d,%d (chunk %d,%d)  roll %.3f %s  "
						+ "top %d below %s  biome %s  ground %s",
				cx, cz, shape.radius(), x, z, x >> 4, z >> 4, roll,
				roll < TREE_CHANCE ? "TREE" : "no tree", top,
				Registries.BLOCK.getId(below.getBlock()).getPath(), biome, ground);
		source.sendFeedback(() -> Text.literal(line), false);
		return 1;
	}

	private static int rolls(ServerCommandSource source, int x, int z) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerWorld world = source.getWorld();
		NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
		IslandField field = IslandTerrain.seededField(world.getRegistryManager(), noiseConfig,
				island.islandSize(), island.islandFrequency(), island.islandNoise(),
				IslandField.Channel.HEIGHT);

		double cell = field.cellSize();
		int cx = (int) Math.floor(x / cell);
		int cz = (int) Math.floor(z / cell);
		source.sendFeedback(() -> Text.literal(String.format(
				"cellSize=%.0f  cell=%d,%d  centre=%.0f,%.0f  frequency=%.2f",
				cell, cx, cz, cx * cell + cell / 2.0, cz * cell + cell / 2.0,
				island.islandFrequency())), false);

		boolean allHalf = true;
		StringBuilder line = new StringBuilder("rolls:");
		for (int k = 0; k < 8; k++) {
			double value = field.rollAt(x, z, k);
			if (Math.abs(value - 0.5) > 1.0e-9) {
				allHalf = false;
			}
			line.append(String.format(" k%d=%.6f", k, value));
		}
		String rendered = line.toString();
		source.sendFeedback(() -> Text.literal(rendered), false);
		boolean unseeded = allHalf;
		source.sendFeedback(() -> Text.literal(unseeded
				? "VERDICT: every roll is exactly 0.5 - the noise is UNSEEDED"
				: "VERDICT: rolls vary - the noise is seeded"), false);
		return 1;
	}

	private static int city(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		var structures = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
		var entry = structures.getEntry(
				RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier("ancient_city")));
		if (entry.isEmpty()) {
			source.sendError(Text.literal("ancient_city is not in the structure registry."));
			return 0;
		}
		BlockPos centre = BlockPos.ofFloored(source.getPosition());
		var found = world.getChunkManager().getChunkGenerator().locateStructure(
				world, RegistryEntryList.of(entry.get()), centre, 100, false);
		if (found == null) {
			source.sendError(Text.literal("No ancient city within 100 chunks of " + centre));
			return 0;
		}
		BlockPos at = found.getFirst();
		source.sendFeedback(() -> Text.literal("ancient_city at " + at.getX() + " " + at.getY()
				+ " " + at.getZ()), false);
		probe(source, at.getX(), at.getZ());
		probe(source, at.getX() + 300, at.getZ());
		return 1;
	}

	private static int probe(ServerCommandSource source, int x, int z) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerWorld world = source.getWorld();
		NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
		int size = island.islandSize();
		float frequency = island.islandFrequency();
		int noise = island.islandNoise();

		source.sendFeedback(() -> Text.literal("--- column " + x + " " + z + " ---"), false);

		StringBuilder blocks = new StringBuilder("  blocks y55-65:");
		for (int y = 55; y <= 65; y++) {
			BlockState state = world.getBlockState(new BlockPos(x, y, z));
			Identifier id = Registries.BLOCK.getId(state.getBlock());
			String name = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
			if (!state.getFluidState().isEmpty() && !state.getFluidState().isStill()) {
				name = name + "(flowing)";
			}
			blocks.append(' ').append(y).append('=').append(name);
		}
		String blockLine = blocks.toString();
		source.sendFeedback(() -> Text.literal(blockLine), false);

		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		ChunkGeneratorSettings settings = ((NoiseChunkGenerator) generator).getSettings().value();
		int seaLevel = settings.seaLevel();
		AquiferSampler.FluidLevel lava =
				new AquiferSampler.FluidLevel(-54, Blocks.LAVA.getDefaultState());
		AquiferSampler.FluidLevel sea =
				new AquiferSampler.FluidLevel(seaLevel, Blocks.WATER.getDefaultState());
		AquiferSampler.FluidLevelSampler fluids =
				(fx, fy, fz) -> fy < Math.min(-54, seaLevel) ? lava : sea;
		ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
		StructureWeightSampler beard = StructureWeightSampler.createStructureWeightSampler(
				world.getStructureAccessor(), chunkPos);
		ChunkNoiseSampler sampler = ChunkNoiseSampler.create(
				world.getChunk(chunkPos.x, chunkPos.z), noiseConfig, beard, settings, fluids,
				Blender.getNoBlending());
		int preliminary = sampler.estimateSurfaceHeight(x, z);
		source.sendFeedback(() -> Text.literal("  preliminarySurfaceLevel = " + preliminary), false);

		DensityFunction field = IslandTerrain.seededField(world.getRegistryManager(), noiseConfig,
				size, frequency, noise, IslandField.Channel.HEIGHT);
		DensityFunction vanillaOffset = IslandTerrain.seededVanillaOffset(
				world.getRegistryManager(), noiseConfig);
		DensityFunction mask = IslandTerrain.seededMask(world.getRegistryManager(), noiseConfig,
				size, frequency, noise);
		DensityFunction.NoisePos flat = new DensityFunction.UnblendedNoisePos(x, 0, z);
		String fieldLine = String.format("  fieldY = %.2f   mask = %.3f   vanillaFloorY = %.2f",
				128.0 + 128.0 * field.sample(flat), mask.sample(flat),
				128.0 + 128.0 * vanillaOffset.sample(flat));
		source.sendFeedback(() -> Text.literal(fieldLine), false);

		StringBuilder beards = new StringBuilder("  beardifier:");
		for (int y : new int[]{50, 55, 60}) {
			beards.append(String.format(" y%d=%.4f", y,
					beard.sample(new DensityFunction.UnblendedNoisePos(x, y, z))));
		}
		String beardLine = beards.toString();
		source.sendFeedback(() -> Text.literal(beardLine), false);
		return 1;
	}


	private static int palette(ServerCommandSource source) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerWorld world = source.getWorld();
		NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
		int size = island.islandSize();
		float frequency = island.islandFrequency();
		int noise = island.islandNoise();
		IslandField shore = IslandTerrain.seededField(world.getRegistryManager(), noiseConfig,
				size, frequency, noise, IslandField.Channel.SHORE);
		IslandField ring = IslandTerrain.seededField(world.getRegistryManager(), noiseConfig,
				size, frequency, noise, IslandField.Channel.SHORE_RING);

		BlockPos origin = BlockPos.ofFloored(source.getPosition());
		double g = shore.cellSize();
		int cx = (int) Math.floor(origin.getX() / g);
		int cz = (int) Math.floor(origin.getZ() / g);
		IslandField.Shape geometry = shore.shapeAt((int) (cx * g + g / 2.0), (int) (cz * g + g / 2.0));
		if (geometry == null) {
			source.sendFeedback(() -> Text.literal("no island in this cell"), false);
			return 0;
		}

		int reach = (int) Math.ceil(geometry.radius() * shore.maskReach() + IslandField.FACE_WIDTH + 8.0);
		int centreX = (int) Math.round(geometry.centreX());
		int centreZ = (int) Math.round(geometry.centreZ());

		Map<String, Object2IntOpenHashMap<String>> tallies = new LinkedHashMap<>();
		for (String key : new String[] {"land", "ring", "shallows", "shelf"}) {
			tallies.put(key, new Object2IntOpenHashMap<>());
		}
		int skipped = 0;
		double shoreMin = Double.POSITIVE_INFINITY;
		double shoreMax = Double.NEGATIVE_INFINITY;
		double shoreSum = 0.0;
		double shoreSq = 0.0;
		double shelfMin = Double.POSITIVE_INFINITY;
		double shelfMax = Double.NEGATIVE_INFINITY;
		double shelfSum = 0.0;
		double shelfSq = 0.0;
		int n = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int z = centreZ - reach; z <= centreZ + reach; z++) {
			for (int x = centreX - reach; x <= centreX + reach; x++) {
				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					skipped++;
					continue;
				}
				int surface = world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z) - 1;
				int floor = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
				double s = shore.sample(new DensityFunction.UnblendedNoisePos(x, 0, z));
				double r = ring.sample(new DensityFunction.UnblendedNoisePos(x, 0, z));
				String cls;
				int top;
				if (s == IslandField.NO_SHORE) {
					continue;
				} else if (s >= 0.0 && surface >= 63) {
					cls = "land";
					top = surface;
				} else if (r > 0.0 && r <= 5.0 && surface == 62) {
					cls = "ring";
					top = surface;
				} else if (floor < 62 && floor >= 58) {
					cls = "shallows";
					top = floor;
				} else {
					cls = "shelf";
					top = floor;
				}
				pos.set(x, top, z);
				BlockState state = world.getBlockState(pos);
				String name = Registries.BLOCK.getId(state.getBlock()).getPath();
				tallies.get(cls).addTo(name, 1);
				if (!cls.equals("shelf")) {
					double sn = shore.shoreNoiseAt(x, z);
					double fn = shore.shelfNoiseAt(x, z);
					shoreMin = Math.min(shoreMin, sn);
					shoreMax = Math.max(shoreMax, sn);
					shoreSum += sn;
					shoreSq += sn * sn;
					shelfMin = Math.min(shelfMin, fn);
					shelfMax = Math.max(shelfMax, fn);
					shelfSum += fn;
					shelfSq += fn * fn;
					n++;
				}
			}
		}

		for (Map.Entry<String, Object2IntOpenHashMap<String>> entry : tallies.entrySet()) {
			Object2IntOpenHashMap<String> tally = entry.getValue();
			int total = tally.values().intStream().sum();
			if (total == 0) {
				continue;
			}
			StringBuilder line = new StringBuilder(String.format("%-8s %5d  ", entry.getKey(), total));
			tally.object2IntEntrySet().stream()
					.sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
					.limit(8)
					.forEach(e -> line.append(String.format("%s %.0f%%  ",
							e.getKey(), 100.0 * e.getIntValue() / total)));
			int shown = Math.min(8, tally.size());
			if (tally.size() > shown) {
				int rest = total - tally.object2IntEntrySet().stream()
						.sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
						.limit(shown)
						.mapToInt(Object2IntOpenHashMap.Entry::getIntValue)
						.sum();
				line.append(String.format("other %.0f%%", 100.0 * rest / total));
			}
			String text = line.toString();
			source.sendFeedback(() -> Text.literal(text), false);
		}
		String settings = String.format(
				"noise %d  gain %.2f  reach %.2fx  skipped %d unloaded columns",
				noise, shore.maskGain(), shore.maskReach(), skipped);
		source.sendFeedback(() -> Text.literal(settings), false);
		if (n > 0) {
			int count = n;
			String noiseLine = String.format(
					"shoreNoise min %.2f max %.2f std %.2f   shelfNoise min %.2f max %.2f std %.2f",
					shoreMin, shoreMax,
					Math.sqrt(shoreSq / count - (shoreSum / count) * (shoreSum / count)),
					shelfMin, shelfMax,
					Math.sqrt(shelfSq / count - (shelfSum / count) * (shelfSum / count)));
			source.sendFeedback(() -> Text.literal(noiseLine), false);
		}
		return 1;
	}

	private static int shape(ServerCommandSource source) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerWorld world = source.getWorld();
		NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
		int size = island.islandSize();
		float frequency = island.islandFrequency();
		int noise = island.islandNoise();
		IslandField shore = IslandTerrain.seededField(world.getRegistryManager(), noiseConfig,
				size, frequency, noise, IslandField.Channel.SHORE);
		IslandField height = IslandTerrain.seededField(world.getRegistryManager(), noiseConfig,
				size, frequency, noise, IslandField.Channel.HEIGHT);

		BlockPos origin = BlockPos.ofFloored(source.getPosition());
		double g = shore.cellSize();
		int baseX = (int) Math.floor(origin.getX() / g);
		int baseZ = (int) Math.floor(origin.getZ() / g);
		int found = 0;

		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int probeX = (int) ((baseX + dx) * g + g / 2.0);
				int probeZ = (int) ((baseZ + dz) * g + g / 2.0);
				IslandField.Shape geometry = shore.shapeAt(probeX, probeZ);
				if (geometry == null) {
					continue;
				}
				found++;
				String line = measure(shore, height, geometry, baseX + dx, baseZ + dz);
				source.sendFeedback(() -> Text.literal(line), false);
			}
		}
		if (found == 0) {
			source.sendFeedback(() -> Text.literal("no islands in the nine cells here"), false);
		}
		return found;
	}

	private static String measure(IslandField shore, IslandField height,
			IslandField.Shape geometry, int cellX, int cellZ) {
		int centreX = (int) Math.floor(geometry.centreX());
		int centreZ = (int) Math.floor(geometry.centreZ());
		int reach = (int) Math.ceil(geometry.radius()) + RING_MARGIN;

		List<int[]> land = new ArrayList<>();
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		int span = 2 * reach + 1;
		double[] heights = new double[span * span];
		boolean[] isLand = new boolean[span * span];

		for (int ix = 0; ix < span; ix++) {
			for (int iz = 0; iz < span; iz++) {
				int x = centreX - reach + ix;
				int z = centreZ - reach + iz;
				DensityFunction.NoisePos pos = new DensityFunction.UnblendedNoisePos(x, 0, z);
				double h = height.sample(pos);
				heights[ix * span + iz] = h;
				if (shore.sample(pos) >= 0.0) {
					isLand[ix * span + iz] = true;
					land.add(new int[]{x, z});
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
				}
			}
		}

		double maxStep = 0.0;
		for (int ix = 0; ix < span; ix++) {
			for (int iz = 0; iz < span; iz++) {
				double h = heights[ix * span + iz];
				if (h == NO_ISLAND_HEIGHT) {
					continue;
				}
				if (ix + 1 < span) {
					maxStep = step(maxStep, h, heights[(ix + 1) * span + iz]);
				}
				if (iz + 1 < span) {
					maxStep = step(maxStep, h, heights[ix * span + iz + 1]);
				}
			}
		}

		int visMinX = Integer.MAX_VALUE;
		int visMaxX = Integer.MIN_VALUE;
		int visMinZ = Integer.MAX_VALUE;
		int visMaxZ = Integer.MIN_VALUE;
		boolean anyVisible = false;
		for (int ix = 0; ix < span; ix++) {
			for (int iz = 0; iz < span; iz++) {
				double h = heights[ix * span + iz];
				if (h == NO_ISLAND_HEIGHT || 128.0 + 128.0 * h < VISIBLE_Y) {
					continue;
				}
				anyVisible = true;
				int x = centreX - reach + ix;
				int z = centreZ - reach + iz;
				visMinX = Math.min(visMinX, x);
				visMaxX = Math.max(visMaxX, x);
				visMinZ = Math.min(visMinZ, z);
				visMaxZ = Math.max(visMaxZ, z);
			}
		}
		int visibleWidest = anyVisible
				? Math.max(visMaxX - visMinX + 1, visMaxZ - visMinZ + 1)
				: 0;

		int pieces = countPieces(isLand, span);

		int widest = land.isEmpty() ? 0 : Math.max(maxX - minX + 1, maxZ - minZ + 1);
		return String.format(
				"cell %d,%d  centre %d,%d  radius %.1f  rolled %d  land %d  widest %d  "
						+ "visibleWidest %d  pieces %d  maxStep %.2f",
				cellX, cellZ, centreX, centreZ, geometry.radius(),
				(int) Math.round(2.0 * geometry.radius()), land.size(), widest, visibleWidest,
				pieces, maxStep);
	}

	private static final int MIN_PIECE = 6;

	private static int countPieces(boolean[] isLand, int span) {
		boolean[] seen = new boolean[isLand.length];
		int[] queue = new int[isLand.length];
		int pieces = 0;
		for (int start = 0; start < isLand.length; start++) {
			if (!isLand[start] || seen[start]) {
				continue;
			}
			int head = 0;
			int tail = 0;
			queue[tail++] = start;
			seen[start] = true;
			int size = 0;
			while (head < tail) {
				int cell = queue[head++];
				size++;
				int ix = cell / span;
				int iz = cell % span;
				for (int step = 0; step < 4; step++) {
					int nx = ix + (step == 0 ? 1 : step == 1 ? -1 : 0);
					int nz = iz + (step == 2 ? 1 : step == 3 ? -1 : 0);
					if (nx < 0 || nz < 0 || nx >= span || nz >= span) {
						continue;
					}
					int next = nx * span + nz;
					if (isLand[next] && !seen[next]) {
						seen[next] = true;
						queue[tail++] = next;
					}
				}
			}
			if (size >= MIN_PIECE) {
				pieces++;
			}
		}
		return pieces;
	}

	private static final double NO_ISLAND_HEIGHT = -1.0;

	private static final int RING_MARGIN = 8;

	private static final double VISIBLE_Y = 62.0;

	private static double step(double worst, double a, double b) {
		if (b == NO_ISLAND_HEIGHT) {
			return worst;
		}
		return Math.max(worst, Math.abs(128.0 * a - 128.0 * b));
	}

	private static int column(ServerCommandSource source, int x, int z, int count) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerWorld world = source.getWorld();
		NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
		int size = island.islandSize();
		float frequency = island.islandFrequency();
		int noise = island.islandNoise();
		DensityFunction field = IslandTerrain.seededField(world.getRegistryManager(), noiseConfig,
				size, frequency, noise, IslandField.Channel.HEIGHT);
		DensityFunction vanillaOffset = IslandTerrain.seededVanillaOffset(
				world.getRegistryManager(), noiseConfig);
		DensityFunction mask = IslandTerrain.seededMask(world.getRegistryManager(), noiseConfig,
				size, frequency, noise);

		source.sendFeedback(() -> Text.literal(String.format(
				"size=%d frequency=%.2f noise=%d   x z fieldY mask vanillaFloorY topSolidY",
				size, frequency, noise)), false);

		for (int i = 0; i < count; i++) {
			int px = x + i;
			DensityFunction.NoisePos pos = new DensityFunction.UnblendedNoisePos(px, 0, z);
			world.getChunk(px >> 4, z >> 4);
			int topSolidY = world.getTopY(Heightmap.Type.OCEAN_FLOOR, px, z) - 1;
			String line = String.format("%d %d %.2f %.3f %.2f %d%s",
					px, z, 128.0 + 128.0 * field.sample(pos), mask.sample(pos),
					128.0 + 128.0 * vanillaOffset.sample(pos), topSolidY,
					(px & 15) == 0 ? "   <- chunk boundary" : "");
			source.sendFeedback(() -> Text.literal(line), false);
		}
		return count;
	}
}
