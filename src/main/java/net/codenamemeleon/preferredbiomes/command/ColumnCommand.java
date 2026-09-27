package net.codenamemeleon.preferredbiomes.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.codenamemeleon.preferredbiomes.worldgen.IslandField;
import net.codenamemeleon.preferredbiomes.worldgen.IslandTerrain;
import net.codenamemeleon.preferredbiomes.worldgen.PreferredBiomeSource;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ColumnCommand {

	private ColumnCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("pb")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("column")
								.then(Commands.argument("x", IntegerArgumentType.integer())
										.then(Commands.argument("z", IntegerArgumentType.integer())
												.then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
														.executes(context -> column(
																context.getSource(),
																IntegerArgumentType.getInteger(context, "x"),
																IntegerArgumentType.getInteger(context, "z"),
																IntegerArgumentType.getInteger(context, "count")))))))
						.then(Commands.literal("city")
								.executes(context -> city(context.getSource())))
						.then(Commands.literal("probe")
								.then(Commands.argument("x", IntegerArgumentType.integer())
										.then(Commands.argument("z", IntegerArgumentType.integer())
												.executes(context -> probe(
														context.getSource(),
														IntegerArgumentType.getInteger(context, "x"),
														IntegerArgumentType.getInteger(context, "z"))))))
						.then(Commands.literal("palette")
								.executes(context -> palette(context.getSource())))
						.then(Commands.literal("shape")
								.executes(context -> shape(context.getSource())))
						.then(Commands.literal("tree")
								.executes(context -> tree(context.getSource())))
						.then(Commands.literal("rolls")
								.then(Commands.argument("x", IntegerArgumentType.integer())
										.then(Commands.argument("z", IntegerArgumentType.integer())
												.executes(context -> rolls(
														context.getSource(),
														IntegerArgumentType.getInteger(context, "x"),
														IntegerArgumentType.getInteger(context, "z"))))))));
	}

	private static final int TREE_ROLL = 8;
	private static final double TREE_CHANCE = 0.4;
	private static final int TREE_SEARCH = 6;

	private static PreferredBiomeSource sourceOf(CommandSourceStack source) {
		ChunkGenerator generator = source.getLevel().getChunkSource().getGenerator();
		if (generator.getBiomeSource() instanceof PreferredBiomeSource island
				&& island.islandSurvivalChallenge()) {
			return island;
		}
		source.sendFailure(Component.literal("Not an Island Survival Challenge world."));
		return null;
	}

	private static int tree(CommandSourceStack source) {
		PreferredBiomeSource biomeSource = sourceOf(source);
		if (biomeSource == null) {
			return 0;
		}
		ServerLevel world = source.getLevel();
		RandomState noiseConfig = world.getChunkSource().randomState();
		IslandField field = IslandTerrain.seededField(world.registryAccess(), noiseConfig,
				biomeSource.islandSize(), biomeSource.islandFrequency(), biomeSource.islandNoise(),
				IslandField.Channel.HEIGHT);

		BlockPos origin = BlockPos.containing(source.getPosition());
		double g = field.cellSize();
		int cx = (int) Math.floor(origin.getX() / g);
		int cz = (int) Math.floor(origin.getZ() / g);

		IslandField.Shape shape = field.shapeAt(origin.getX(), origin.getZ());
		if (shape == null) {
			source.sendSuccess(() -> Component.literal("cell " + cx + "," + cz + ": no island"), false);
			return 0;
		}
		BlockPos anchor = field.anchorLand(cx, cz);
		if (anchor == null) {
			source.sendSuccess(() -> Component.literal(String.format(
					"cell %d,%d radius %.1f: NO ANCHOR (no column 2+ blocks inland)",
					cx, cz, shape.radius())), false);
			return 1;
		}
		int x = anchor.getX();
		int z = anchor.getZ();
		double roll = field.rollAt(x, z, TREE_ROLL);
		int top = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockState below = world.getBlockState(new BlockPos(x, top - 1, z));
		String biome = world.getBiome(new BlockPos(x, top, z)).unwrapKey()
				.map(key -> key.identifier().toString()).orElse("?");

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
					int gy = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
					BlockState under = world.getBlockState(new BlockPos(gx, gy - 1, gz));
					if (under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.DIRT)) {
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
				BuiltInRegistries.BLOCK.getKey(below.getBlock()).getPath(), biome, ground);
		source.sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static int rolls(CommandSourceStack source, int x, int z) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerLevel world = source.getLevel();
		RandomState noiseConfig = world.getChunkSource().randomState();
		IslandField field = IslandTerrain.seededField(world.registryAccess(), noiseConfig,
				island.islandSize(), island.islandFrequency(), island.islandNoise(),
				IslandField.Channel.HEIGHT);

		double cell = field.cellSize();
		int cx = (int) Math.floor(x / cell);
		int cz = (int) Math.floor(z / cell);
		source.sendSuccess(() -> Component.literal(String.format(
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
		source.sendSuccess(() -> Component.literal(rendered), false);
		boolean unseeded = allHalf;
		source.sendSuccess(() -> Component.literal(unseeded
				? "VERDICT: every roll is exactly 0.5 - the noise is UNSEEDED"
				: "VERDICT: rolls vary - the noise is seeded"), false);
		return 1;
	}

	private static int city(CommandSourceStack source) {
		ServerLevel world = source.getLevel();
		var structures = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var entry = structures.get(
				ResourceKey.create(Registries.STRUCTURE, Identifier.parse("ancient_city")));
		if (entry.isEmpty()) {
			source.sendFailure(Component.literal("ancient_city is not in the structure registry."));
			return 0;
		}
		BlockPos centre = BlockPos.containing(source.getPosition());
		var found = world.getChunkSource().getGenerator().findNearestMapStructure(
				world, HolderSet.direct(entry.get()), centre, 100, false);
		if (found == null) {
			source.sendFailure(Component.literal("No ancient city within 100 chunks of " + centre));
			return 0;
		}
		BlockPos at = found.getFirst();
		source.sendSuccess(() -> Component.literal("ancient_city at " + at.getX() + " " + at.getY()
				+ " " + at.getZ()), false);
		probe(source, at.getX(), at.getZ());
		probe(source, at.getX() + 300, at.getZ());
		return 1;
	}

	private static int probe(CommandSourceStack source, int x, int z) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerLevel world = source.getLevel();
		RandomState noiseConfig = world.getChunkSource().randomState();
		int size = island.islandSize();
		float frequency = island.islandFrequency();
		int noise = island.islandNoise();

		source.sendSuccess(() -> Component.literal("--- column " + x + " " + z + " ---"), false);

		StringBuilder blocks = new StringBuilder("  blocks y55-65:");
		for (int y = 55; y <= 65; y++) {
			BlockState state = world.getBlockState(new BlockPos(x, y, z));
			Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			String name = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
			if (!state.getFluidState().isEmpty() && !state.getFluidState().isSource()) {
				name = name + "(flowing)";
			}
			blocks.append(' ').append(y).append('=').append(name);
		}
		String blockLine = blocks.toString();
		source.sendSuccess(() -> Component.literal(blockLine), false);

		ChunkGenerator generator = world.getChunkSource().getGenerator();
		NoiseGeneratorSettings settings = ((NoiseBasedChunkGenerator) generator).generatorSettings().value();
		int seaLevel = settings.seaLevel();
		Aquifer.FluidStatus lava =
				new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
		Aquifer.FluidStatus sea =
				new Aquifer.FluidStatus(seaLevel, Blocks.WATER.defaultBlockState());
		Aquifer.FluidPicker fluids =
				(fx, fy, fz) -> fy < Math.min(-54, seaLevel) ? lava : sea;
		ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
		Beardifier beard = Beardifier.forStructuresInChunk(
				world.structureManager(), chunkPos);
		NoiseChunk sampler = NoiseChunk.forChunk(
				world.getChunk(chunkPos.x(), chunkPos.z()), noiseConfig, beard, settings, fluids,
				Blender.empty());
		int preliminary = sampler.preliminarySurfaceLevel(x, z);
		source.sendSuccess(() -> Component.literal("  preliminarySurfaceLevel = " + preliminary), false);

		DensityFunction field = IslandTerrain.seededField(world.registryAccess(), noiseConfig,
				size, frequency, noise, IslandField.Channel.HEIGHT);
		DensityFunction vanillaOffset = IslandTerrain.seededVanillaOffset(
				world.registryAccess(), noiseConfig);
		DensityFunction mask = IslandTerrain.seededMask(world.registryAccess(), noiseConfig,
				size, frequency, noise);
		DensityFunction.FunctionContext flat = new DensityFunction.SinglePointContext(x, 0, z);
		String fieldLine = String.format("  fieldY = %.2f   mask = %.3f   vanillaFloorY = %.2f",
				128.0 + 128.0 * field.compute(flat), mask.compute(flat),
				128.0 + 128.0 * vanillaOffset.compute(flat));
		source.sendSuccess(() -> Component.literal(fieldLine), false);

		StringBuilder beards = new StringBuilder("  beardifier:");
		for (int y : new int[]{50, 55, 60}) {
			beards.append(String.format(" y%d=%.4f", y,
					beard.compute(new DensityFunction.SinglePointContext(x, y, z))));
		}
		String beardLine = beards.toString();
		source.sendSuccess(() -> Component.literal(beardLine), false);
		return 1;
	}


	private static int palette(CommandSourceStack source) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerLevel world = source.getLevel();
		RandomState noiseConfig = world.getChunkSource().randomState();
		int size = island.islandSize();
		float frequency = island.islandFrequency();
		int noise = island.islandNoise();
		IslandField shore = IslandTerrain.seededField(world.registryAccess(), noiseConfig,
				size, frequency, noise, IslandField.Channel.SHORE);
		IslandField ring = IslandTerrain.seededField(world.registryAccess(), noiseConfig,
				size, frequency, noise, IslandField.Channel.SHORE_RING);

		BlockPos origin = BlockPos.containing(source.getPosition());
		double g = shore.cellSize();
		int cx = (int) Math.floor(origin.getX() / g);
		int cz = (int) Math.floor(origin.getZ() / g);
		IslandField.Shape geometry = shore.shapeAt((int) (cx * g + g / 2.0), (int) (cz * g + g / 2.0));
		if (geometry == null) {
			source.sendSuccess(() -> Component.literal("no island in this cell"), false);
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
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int z = centreZ - reach; z <= centreZ + reach; z++) {
			for (int x = centreX - reach; x <= centreX + reach; x++) {
				if (!world.hasChunk(x >> 4, z >> 4)) {
					skipped++;
					continue;
				}
				int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
				int floor = world.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
				double s = shore.compute(new DensityFunction.SinglePointContext(x, 0, z));
				double r = ring.compute(new DensityFunction.SinglePointContext(x, 0, z));
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
				String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
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
			source.sendSuccess(() -> Component.literal(text), false);
		}
		String settings = String.format(
				"noise %d  gain %.2f  reach %.2fx  skipped %d unloaded columns",
				noise, shore.maskGain(), shore.maskReach(), skipped);
		source.sendSuccess(() -> Component.literal(settings), false);
		if (n > 0) {
			int count = n;
			String noiseLine = String.format(
					"shoreNoise min %.2f max %.2f std %.2f   shelfNoise min %.2f max %.2f std %.2f",
					shoreMin, shoreMax,
					Math.sqrt(shoreSq / count - (shoreSum / count) * (shoreSum / count)),
					shelfMin, shelfMax,
					Math.sqrt(shelfSq / count - (shelfSum / count) * (shelfSum / count)));
			source.sendSuccess(() -> Component.literal(noiseLine), false);
		}
		return 1;
	}

	private static int shape(CommandSourceStack source) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerLevel world = source.getLevel();
		RandomState noiseConfig = world.getChunkSource().randomState();
		int size = island.islandSize();
		float frequency = island.islandFrequency();
		int noise = island.islandNoise();
		IslandField shore = IslandTerrain.seededField(world.registryAccess(), noiseConfig,
				size, frequency, noise, IslandField.Channel.SHORE);
		IslandField height = IslandTerrain.seededField(world.registryAccess(), noiseConfig,
				size, frequency, noise, IslandField.Channel.HEIGHT);

		BlockPos origin = BlockPos.containing(source.getPosition());
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
				source.sendSuccess(() -> Component.literal(line), false);
			}
		}
		if (found == 0) {
			source.sendSuccess(() -> Component.literal("no islands in the nine cells here"), false);
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
				DensityFunction.FunctionContext pos = new DensityFunction.SinglePointContext(x, 0, z);
				double h = height.compute(pos);
				heights[ix * span + iz] = h;
				if (shore.compute(pos) >= 0.0) {
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

	private static int column(CommandSourceStack source, int x, int z, int count) {
		PreferredBiomeSource island = sourceOf(source);
		if (island == null) {
			return 0;
		}
		ServerLevel world = source.getLevel();
		RandomState noiseConfig = world.getChunkSource().randomState();
		int size = island.islandSize();
		float frequency = island.islandFrequency();
		int noise = island.islandNoise();
		DensityFunction field = IslandTerrain.seededField(world.registryAccess(), noiseConfig,
				size, frequency, noise, IslandField.Channel.HEIGHT);
		DensityFunction vanillaOffset = IslandTerrain.seededVanillaOffset(
				world.registryAccess(), noiseConfig);
		DensityFunction mask = IslandTerrain.seededMask(world.registryAccess(), noiseConfig,
				size, frequency, noise);

		source.sendSuccess(() -> Component.literal(String.format(
				"size=%d frequency=%.2f noise=%d   x z fieldY mask vanillaFloorY topSolidY",
				size, frequency, noise)), false);

		for (int i = 0; i < count; i++) {
			int px = x + i;
			DensityFunction.FunctionContext pos = new DensityFunction.SinglePointContext(px, 0, z);
			world.getChunk(px >> 4, z >> 4);
			int topSolidY = world.getHeight(Heightmap.Types.OCEAN_FLOOR, px, z) - 1;
			String line = String.format("%d %d %.2f %.3f %.2f %d%s",
					px, z, 128.0 + 128.0 * field.compute(pos), mask.compute(pos),
					128.0 + 128.0 * vanillaOffset.compute(pos), topSolidY,
					(px & 15) == 0 ? "   <- chunk boundary" : "");
			source.sendSuccess(() -> Component.literal(line), false);
		}
		return count;
	}
}
