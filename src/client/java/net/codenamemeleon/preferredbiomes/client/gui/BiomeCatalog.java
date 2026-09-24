package net.codenamemeleon.preferredbiomes.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.codenamemeleon.preferredbiomes.client.mixin.BiomeWeatherAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AttachedStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.PropaguleBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.TallFlowerBlock;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public record BiomeCatalog(List<BiomeCatalog.Entry> entries) {

	public record Entry(Identifier id, String namespace, String modName, String displayName,
			float temperature, float downfall, List<String> plants) {
	}

	public enum Temperature {
		FROZEN, COLD, TEMPERATE, LUKEWARM, WARM
	}

	public enum Humidity {
		ARID, DRY, MODERATE, HUMID
	}

	private static final float FROZEN_MAX = 0.15F;
	private static final float COLD_MAX = 0.45F;
	private static final float TEMPERATE_MAX = 0.85F;
	private static final float LUKEWARM_MAX = 1.1F;

	private static final float ARID_MAX = 0.05F;
	private static final float DRY_MAX = 0.35F;
	private static final float MODERATE_MAX = 0.65F;

	public static Temperature temperatureBand(float temperature) {
		if (temperature < FROZEN_MAX) {
			return Temperature.FROZEN;
		}
		if (temperature < COLD_MAX) {
			return Temperature.COLD;
		}
		if (temperature < TEMPERATE_MAX) {
			return Temperature.TEMPERATE;
		}
		return temperature < LUKEWARM_MAX ? Temperature.LUKEWARM : Temperature.WARM;
	}

	public static Humidity humidityBand(float downfall) {
		if (downfall < ARID_MAX) {
			return Humidity.ARID;
		}
		if (downfall < DRY_MAX) {
			return Humidity.DRY;
		}
		return downfall < MODERATE_MAX ? Humidity.MODERATE : Humidity.HUMID;
	}

	private static final Identifier OVERWORLD_PARAMETERS = new Identifier("overworld");
	private static final Identifier IS_OVERWORLD = new Identifier("is_overworld");

	public static BiomeCatalog build(DynamicRegistryManager registryManager) {
		Registry<Biome> biomes = registryManager.get(RegistryKeys.BIOME);
		Set<Identifier> ids = new LinkedHashSet<>();

		Registry<MultiNoiseBiomeSourceParameterList> parameterLists =
				registryManager.get(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
		MultiNoiseBiomeSourceParameterList overworld = parameterLists.get(
				RegistryKey.of(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, OVERWORLD_PARAMETERS));
		if (overworld != null) {
			overworld.getEntries().getEntries().forEach(pair ->
					pair.getSecond().getKey().ifPresent(key -> ids.add(key.getValue())));
		}

		TagKey<Biome> isOverworld = TagKey.of(RegistryKeys.BIOME, IS_OVERWORLD);
		for (RegistryEntry.Reference<Biome> entry : biomes.streamEntries().toList()) {
			if (entry.isIn(isOverworld)) {
				ids.add(entry.registryKey().getValue());
			}
		}

		PlantScanner scanner = new PlantScanner(registryManager);
		List<Entry> catalog = new ArrayList<>(ids.size());
		for (Identifier id : ids) {
			Biome biome = biomes.get(id);
			float temperature = biome == null ? 0.0F : biome.getTemperature();
			float downfall = biome == null ? 0.0F
					: ((BiomeWeatherAccessor) (Object) biome).getWeather().downfall();
			List<String> plants = biome == null ? List.of() : scanner.plantsOf(biome);
			catalog.add(new Entry(id, id.getNamespace(), modNameOf(id.getNamespace()),
					displayNameOf(id), temperature, downfall, plants));
		}
		return new BiomeCatalog(List.copyOf(catalog));
	}

	private static String modNameOf(String namespace) {
		return FabricLoader.getInstance().getModContainer(namespace)
				.map(container -> container.getMetadata().getName())
				.orElse(namespace);
	}

	private static String displayNameOf(Identifier id) {
		String key = id.toTranslationKey("biome");
		return I18n.hasTranslation(key) ? I18n.translate(key) : id.toString();
	}

	public int size() {
		return this.entries.size();
	}

	private static final class PlantScanner {

		private final RegistryOps<JsonElement> ops;
		private final Registry<ConfiguredFeature<?, ?>> configured;
		private final Registry<PlacedFeature> placed;
		private final Map<Identifier, List<Block>> byConfiguredId = new HashMap<>();
		private final Map<Identifier, List<Block>> byPlacedId = new HashMap<>();

		PlantScanner(DynamicRegistryManager registryManager) {
			this.ops = RegistryOps.of(JsonOps.INSTANCE, registryManager);
			this.configured = registryManager.get(RegistryKeys.CONFIGURED_FEATURE);
			this.placed = registryManager.get(RegistryKeys.PLACED_FEATURE);
		}

		List<String> plantsOf(Biome biome) {
			Set<Block> blocks = new LinkedHashSet<>();
			for (RegistryEntryList<PlacedFeature> step : biome.getGenerationSettings().getFeatures()) {
				for (RegistryEntry<PlacedFeature> entry : step) {
					blocks.addAll(blocksOf(entry));
				}
			}
			return name(blocks);
		}

		private List<Block> blocksOf(RegistryEntry<PlacedFeature> entry) {
			Identifier id = entry.getKey().map(RegistryKey::getValue).orElse(null);
			if (id != null) {
				return placedById(id);
			}
			Set<Block> out = new LinkedHashSet<>();
			walk(encodePlaced(entry.value()), out);
			return List.copyOf(out);
		}

		private List<Block> placedById(Identifier id) {
			List<Block> cached = this.byPlacedId.get(id);
			if (cached != null) {
				return cached;
			}
			this.byPlacedId.put(id, List.of());
			PlacedFeature feature = this.placed.get(id);
			if (feature == null) {
				return List.of();
			}
			Set<Block> out = new LinkedHashSet<>();
			walk(encodePlaced(feature), out);
			List<Block> blocks = List.copyOf(out);
			this.byPlacedId.put(id, blocks);
			return blocks;
		}

		private List<Block> configuredById(Identifier id) {
			List<Block> cached = this.byConfiguredId.get(id);
			if (cached != null) {
				return cached;
			}
			this.byConfiguredId.put(id, List.of());
			ConfiguredFeature<?, ?> feature = this.configured.get(id);
			if (feature == null) {
				return List.of();
			}
			Set<Block> out = new LinkedHashSet<>();
			walk(ConfiguredFeature.CODEC.encodeStart(this.ops, feature).result().orElse(null), out);
			List<Block> blocks = List.copyOf(out);
			this.byConfiguredId.put(id, blocks);
			return blocks;
		}

		private JsonElement encodePlaced(PlacedFeature feature) {
			return PlacedFeature.CODEC.encodeStart(this.ops, feature).result().orElse(null);
		}

		private void walk(JsonElement json, Set<Block> out) {
			if (json == null) {
				return;
			}
			if (json.isJsonArray()) {
				for (JsonElement element : json.getAsJsonArray()) {
					walk(element, out);
				}
				return;
			}
			if (json.isJsonObject()) {
				JsonObject object = json.getAsJsonObject();
				JsonElement name = object.get("Name");
				if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
					block(name.getAsString(), out);
				}
				for (Map.Entry<String, JsonElement> field : object.entrySet()) {
					walk(field.getValue(), out);
				}
				return;
			}
			if (!json.isJsonPrimitive()) {
				return;
			}
			JsonPrimitive primitive = json.getAsJsonPrimitive();
			if (!primitive.isString()) {
				return;
			}
			if (block(primitive.getAsString(), out)) {
				return;
			}
			Identifier id = Identifier.tryParse(primitive.getAsString());
			if (id == null) {
				return;
			}
			if (this.configured.containsId(id)) {
				out.addAll(configuredById(id));
			}
			if (this.placed.containsId(id)) {
				out.addAll(placedById(id));
			}
		}

		private static boolean block(String text, Set<Block> out) {
			Identifier id = Identifier.tryParse(text);
			if (id == null || !Registries.BLOCK.containsId(id)) {
				return false;
			}
			out.add(Registries.BLOCK.get(id));
			return true;
		}

		private static List<String> name(Set<Block> blocks) {
			Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			for (Block block : blocks) {
				if (isSapling(block)) {
					names.add(saplingName(block));
				} else if (isFlower(block) || isCrop(block)) {
					names.add(plantName(block));
				}
			}
			return List.copyOf(names);
		}

		private static boolean isSapling(Block block) {
			if (block.getDefaultState().isIn(BlockTags.SAPLINGS)) {
				return true;
			}
			if (block instanceof SaplingBlock || block instanceof PropaguleBlock) {
				return true;
			}
			String path = Registries.BLOCK.getId(block).getPath();
			return path.endsWith("_sapling") || path.endsWith("_propagule");
		}

		private static boolean isFlower(Block block) {
			return block.getDefaultState().isIn(BlockTags.FLOWERS)
					|| block instanceof FlowerBlock || block instanceof TallFlowerBlock;
		}

		private static boolean isCrop(Block block) {
			return block.getDefaultState().isIn(BlockTags.CROPS)
					|| block instanceof CropBlock || block instanceof StemBlock
					|| block instanceof AttachedStemBlock;
		}

		private static String saplingName(Block sapling) {
			String name = plantName(sapling);
			for (String suffix : new String[] { " Sapling", " Propagule" }) {
				if (name.endsWith(suffix)) {
					return name.substring(0, name.length() - suffix.length());
				}
			}
			return name;
		}

		private static String plantName(Block block) {
			String key = block.getTranslationKey();
			String name = Text.translatable(key).getString();
			if (!name.equals(key)) {
				return name;
			}
			String path = Registries.BLOCK.getId(block).getPath();
			StringBuilder out = new StringBuilder();
			for (String word : path.split("_")) {
				if (word.isEmpty()) {
					continue;
				}
				if (out.length() > 0) {
					out.append(' ');
				}
				out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
			return out.toString();
		}
	}
}
