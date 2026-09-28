package net.codenamemeleon.preferredbiomes.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BiomeGen {

	private static final String MC = "minecraft:";
	private static final String PB = "preferred-biomes:";

	private static final int UNDERGROUND_ORES = 6;
	private static final int VEGETAL_DECORATION = 9;

	private static final double TURTLE_MIN_TEMPERATURE = 0.5;

	private static final List<String> TINT =
			List.of("grass_color", "foliage_color", "dry_foliage_color", "grass_color_modifier");
	private static final List<String> OCEAN_EFFECTS = List.of("sky_color", "water_color", "water_fog_color");
	private static final List<String> OCEAN_ATTRIBUTES =
			List.of("minecraft:visual/sky_color", "minecraft:visual/water_fog_color");

	private static final List<String> LUSH_CAVES_VEGETATION = List.of("patch_tall_grass_2",
			"lush_caves_ceiling_vegetation", "cave_vines", "lush_caves_clay", "lush_caves_vegetation",
			"rooted_azalea_tree", "spore_blossom");
	private static final List<String> LUSH_ISLAND_VEGETATION = List.of("lush_island_ceiling_vegetation",
			"lush_island_cave_vines", "lush_island_pool_anchor", "lush_island_clay_pool", "lush_island_clay",
			"lush_island_moss", "lush_island_moss_spots", "island_azalea_tree", "azalea_hangings",
			"lush_island_spore_blossom");

	private enum Band {
		FROZEN("frozen", "frozen_ocean"),
		COLD("cold", "cold_ocean"),
		TEMPERATE("temperate", "ocean"),
		LUKEWARM("lukewarm", "lukewarm_ocean"),
		WARM("warm", "warm_ocean");

		final String suffix;
		final String ocean;

		Band(String suffix, String ocean) {
			this.suffix = suffix;
			this.ocean = ocean;
		}
	}

	private record Shore(String island, Band band) {
		String name() {
			return "island_shore_" + this.island + "_" + this.band.suffix;
		}
	}

	private static final List<Shore> SHORES = List.of(
			new Shore("frozen_ocean", Band.FROZEN), new Shore("snowy_taiga_island", Band.FROZEN),
			new Shore("snowy_cherry_grove", Band.FROZEN), new Shore("ice_spikes_island", Band.FROZEN),
			new Shore("mushroom_fields", Band.FROZEN),
			new Shore("cold_ocean", Band.COLD), new Shore("taiga", Band.COLD),
			new Shore("cherry_grove", Band.COLD), new Shore("old_growth_pine_taiga", Band.COLD),
			new Shore("mushroom_fields", Band.COLD),
			new Shore("ocean", Band.TEMPERATE), new Shore("meadow", Band.TEMPERATE),
			new Shore("windswept_forest", Band.TEMPERATE), new Shore("dark_forest_island", Band.TEMPERATE),
			new Shore("mushroom_fields", Band.TEMPERATE), new Shore("pale_garden", Band.TEMPERATE),
			new Shore("lukewarm_ocean", Band.LUKEWARM), new Shore("savanna", Band.LUKEWARM),
			new Shore("sparse_jungle", Band.LUKEWARM), new Shore("bamboo_jungle", Band.LUKEWARM),
			new Shore("mushroom_fields", Band.LUKEWARM),
			new Shore("warm_ocean", Band.WARM), new Shore("savanna", Band.WARM),
			new Shore("desert_island", Band.WARM), new Shore("mangrove_island", Band.WARM),
			new Shore("mushroom_fields", Band.WARM), new Shore("lush_island", Band.WARM));

	private static final List<String> TAGS = List.of("is_beach", "has_structure/shipwreck_beached");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final Comparator<String> KEY_ORDER = Comparator
			.comparingInt((String key) -> key.equals("type") ? 0 : key.equals("parent") ? 1 : 2)
			.thenComparing(Comparator.naturalOrder());

	private final Vanilla vanilla;
	private final Map<String, Supplier<JsonObject>> islands = new LinkedHashMap<>();

	private BiomeGen(Vanilla vanilla) {
		this.vanilla = vanilla;
		this.islands.put("desert_island", this::desertIsland);
		this.islands.put("mangrove_island", this::mangroveIsland);
		this.islands.put("lush_island", this::lushIsland);
		this.islands.put("dark_forest_island", this::darkForestIsland);
		this.islands.put("ice_spikes_island", this::iceSpikesIsland);
		this.islands.put("snowy_taiga_island", this::snowyTaigaIsland);
		this.islands.put("snowy_cherry_grove", this::snowyCherryGrove);
		for (String ocean : List.of("cold_ocean", "frozen_ocean", "lukewarm_ocean", "ocean", "warm_ocean")) {
			this.islands.put("bare_island_" + ocean, () -> bareIsland(ocean));
		}
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 3) {
			throw new IllegalArgumentException("usage: BiomeGen <biome output dir> <biome tag dir> [vanilla jar]");
		}
		Path biomeDir = Path.of(args[0]);
		Path tagDir = Path.of(args[1]);
		try (Vanilla vanilla = args.length == 3 ? Vanilla.jar(Path.of(args[2])) : Vanilla.classpath()) {
			new BiomeGen(vanilla).run(biomeDir, tagDir);
		}
	}

	private void run(Path biomeDir, Path tagDir) throws IOException {
		Map<String, JsonObject> biomes = new LinkedHashMap<>();
		this.islands.forEach((name, build) -> biomes.put(name, build.get()));
		for (Shore shore : SHORES) {
			biomes.put(shore.name(), shore(shore));
		}

		Files.createDirectories(biomeDir);
		for (Map.Entry<String, JsonObject> biome : biomes.entrySet()) {
			write(biomeDir.resolve(biome.getKey() + ".json"), biome.getValue());
		}
		JsonArray shoreIds = new JsonArray();
		SHORES.forEach(shore -> shoreIds.add(PB + shore.name()));
		for (String tag : TAGS) {
			JsonObject json = new JsonObject();
			json.addProperty("replace", false);
			json.add("values", shoreIds);
			Path path = tagDir.resolve(tag + ".json");
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
		}
		System.out.println("BiomeGen: wrote " + biomes.size() + " biomes and " + TAGS.size() + " tags from "
				+ this.vanilla.describe());

		Set<String> unmanaged = new TreeSet<>();
		try (Stream<Path> files = Files.list(biomeDir)) {
			files.map(path -> path.getFileName().toString())
					.filter(file -> file.endsWith(".json"))
					.map(file -> file.substring(0, file.length() - ".json".length()))
					.filter(name -> !biomes.containsKey(name))
					.forEach(unmanaged::add);
		}
		if (!unmanaged.isEmpty()) {
			System.out.println("BiomeGen: not generated here, left as they are: " + unmanaged);
		}
	}


	private JsonObject desertIsland() {
		return this.vanilla.biome("desert");
	}

	private JsonObject mangroveIsland() {
		JsonObject biome = this.vanilla.biome("mangrove_swamp");
		JsonArray vegetation = step(biome, VEGETAL_DECORATION);
		vegetation.asList().add(indexOf(vegetation, MC + "trees_mangrove") + 1,
				new JsonPrimitive(PB + "island_blue_orchids"));
		spawn(biome, "monster", "witch").addProperty("weight", 15);
		return biome;
	}

	private JsonObject lushIsland() {
		JsonObject biome = this.vanilla.biome("lush_caves");
		remove(step(biome, UNDERGROUND_ORES), "disk_sand", "disk_clay", "disk_gravel");
		JsonArray vegetation = step(biome, VEGETAL_DECORATION);
		int at = indexOf(vegetation, MC + LUSH_CAVES_VEGETATION.get(0));
		remove(vegetation, LUSH_CAVES_VEGETATION.toArray(String[]::new));
		List<JsonElement> ours = new ArrayList<>();
		LUSH_ISLAND_VEGETATION.forEach(id -> ours.add(new JsonPrimitive(PB + id)));
		vegetation.asList().addAll(at, ours);
		return biome;
	}

	private JsonObject darkForestIsland() {
		JsonObject biome = this.vanilla.biome("dark_forest");
		addSpawn(biome, "creature", "allay", 10, 1, 2);
		addSpawn(biome, "monster", "ravager", 5, 1, 1);
		addSpawn(biome, "monster", "vex", 10, 1, 2);
		return biome;
	}

	private JsonObject iceSpikesIsland() {
		JsonObject biome = this.vanilla.biome("ice_spikes");
		remove(step(biome, VEGETAL_DECORATION), "patch_sugar_cane", "patch_pumpkin");
		return biome;
	}

	private JsonObject snowyTaigaIsland() {
		JsonObject biome = this.vanilla.biome("taiga");
		remove(step(biome, VEGETAL_DECORATION), "patch_sugar_cane");
		biome.addProperty("temperature", 0.0);
		return biome;
	}

	private JsonObject snowyCherryGrove() {
		JsonObject biome = this.vanilla.biome("cherry_grove");
		JsonArray vegetation = step(biome, VEGETAL_DECORATION);
		remove(vegetation, "flower_cherry", "trees_cherry");
		vegetation.add(PB + "trees_bare_cherry");
		biome.addProperty("temperature", 0.0);
		return biome;
	}

	private JsonObject bareIsland(String ocean) {
		JsonObject biome = this.vanilla.biome(ocean);
		JsonArray vegetation = step(biome, VEGETAL_DECORATION);
		if (ocean.equals("frozen_ocean")) {
			remove(vegetation, "patch_sugar_cane");
		}
		vegetation.add(PB + "bare_island_tree");
		return biome;
	}


	private JsonObject shore(Shore shore) {
		Supplier<JsonObject> ours = this.islands.get(shore.island());
		JsonObject island = ours != null ? ours.get() : this.vanilla.biome(shore.island());
		JsonObject ocean = this.vanilla.biome(shore.band().ocean);
		JsonObject biome = this.vanilla.biome("beach");

		copy(island, biome, List.of("temperature", "downfall", "has_precipitation"));
		copy(island.getAsJsonObject("effects"), biome.getAsJsonObject("effects"), TINT);
		copy(ocean.getAsJsonObject("effects"), biome.getAsJsonObject("effects"), OCEAN_EFFECTS);
		if (ocean.has("attributes") || biome.has("attributes")) {
			if (!biome.has("attributes")) {
				biome.add("attributes", new JsonObject());
			}
			JsonObject from = ocean.has("attributes") ? ocean.getAsJsonObject("attributes") : new JsonObject();
			copy(from, biome.getAsJsonObject("attributes"), OCEAN_ATTRIBUTES);
		}

		String features = shore.island().equals("mangrove_island") ? "mangrove_swamp" : shore.band().ocean;
		biome.add("features", this.vanilla.biome(features).get("features"));
		if (shore.band() == Band.FROZEN) {
			remove(step(biome, VEGETAL_DECORATION), "patch_sugar_cane");
		}
		if (shore.island().equals("lush_island")) {
			remove(step(biome, UNDERGROUND_ORES), "disk_sand", "disk_clay", "disk_gravel");
			JsonArray vegetation = step(biome, VEGETAL_DECORATION);
			remove(vegetation, "patch_grass_badlands");
			vegetation.asList().add(indexOf(vegetation, MC + "glow_lichen") + 1,
					new JsonPrimitive(PB + "lush_island_pool_anchor"));
		}

		if (island.get("temperature").getAsDouble() < TURTLE_MIN_TEMPERATURE) {
			biome.getAsJsonObject("spawners").add("creature", new JsonArray());
		}
		return biome;
	}


	private static JsonArray step(JsonObject biome, int step) {
		return biome.getAsJsonArray("features").get(step).getAsJsonArray();
	}

	private static int indexOf(JsonArray list, String id) {
		int at = list.asList().indexOf(new JsonPrimitive(id));
		if (at < 0) {
			throw new IllegalStateException("vanilla no longer lists " + id + ", which an edit is anchored to");
		}
		return at;
	}

	private static void remove(JsonArray list, String... ids) {
		for (String id : ids) {
			if (!list.remove(new JsonPrimitive(MC + id))) {
				throw new IllegalStateException("vanilla no longer lists " + MC + id + ", which an edit removes");
			}
		}
	}

	private static JsonObject spawn(JsonObject biome, String category, String mob) {
		for (JsonElement entry : biome.getAsJsonObject("spawners").getAsJsonArray(category)) {
			if (entry.getAsJsonObject().get("type").getAsString().equals(MC + mob)) {
				return entry.getAsJsonObject();
			}
		}
		throw new IllegalStateException("vanilla no longer spawns " + MC + mob + " as " + category);
	}

	private static void addSpawn(JsonObject biome, String category, String mob, int weight, int min, int max) {
		JsonObject entry = new JsonObject();
		entry.addProperty("type", MC + mob);
		entry.addProperty("weight", weight);
		entry.addProperty("minCount", min);
		entry.addProperty("maxCount", max);
		biome.getAsJsonObject("spawners").getAsJsonArray(category).add(entry);
	}

	private static void copy(JsonObject from, JsonObject to, List<String> keys) {
		for (String key : keys) {
			if (from.has(key)) {
				to.add(key, from.get(key).deepCopy());
			} else {
				to.remove(key);
			}
		}
	}

	private static void write(Path path, JsonObject biome) throws IOException {
		Files.writeString(path, GSON.toJson(sorted(biome)) + "\n", StandardCharsets.UTF_8);
	}

	private static JsonElement sorted(JsonElement element) {
		if (element.isJsonObject()) {
			JsonObject out = new JsonObject();
			element.getAsJsonObject().keySet().stream().sorted(KEY_ORDER)
					.forEach(key -> out.add(key, sorted(element.getAsJsonObject().get(key))));
			return out;
		}
		if (element.isJsonArray()) {
			JsonArray out = new JsonArray();
			element.getAsJsonArray().forEach(item -> out.add(sorted(item)));
			return out;
		}
		return element;
	}

	private interface Vanilla extends AutoCloseable {
		JsonObject biome(String name);

		String describe();

		@Override
		void close() throws IOException;

		static Vanilla classpath() {
			return new Vanilla() {
				@Override
				public JsonObject biome(String name) {
					String path = "data/minecraft/worldgen/biome/" + name + ".json";
					InputStream in = BiomeGen.class.getClassLoader().getResourceAsStream(path);
					if (in == null) {
						throw new IllegalStateException("no vanilla biome " + name + " on the classpath");
					}
					return parse(in);
				}

				@Override
				public String describe() {
					return "the Minecraft jar on the classpath";
				}

				@Override
				public void close() {
				}
			};
		}

		static Vanilla jar(Path jar) throws IOException {
			ZipFile zip = new ZipFile(jar.toFile());
			return new Vanilla() {
				@Override
				public JsonObject biome(String name) {
					ZipEntry entry = zip.getEntry("data/minecraft/worldgen/biome/" + name + ".json");
					if (entry == null) {
						throw new IllegalStateException("no vanilla biome " + name + " in " + jar);
					}
					try {
						return parse(zip.getInputStream(entry));
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				}

				@Override
				public String describe() {
					return jar.toString();
				}

				@Override
				public void close() throws IOException {
					zip.close();
				}
			};
		}

		private static JsonObject parse(InputStream in) {
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
