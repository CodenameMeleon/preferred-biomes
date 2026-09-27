package net.codenamemeleon.preferredbiomes.client.gui;

import com.mojang.datafixers.util.Either;
import net.codenamemeleon.preferredbiomes.worldgen.IslandBiomes;
import net.codenamemeleon.preferredbiomes.worldgen.IslandTerrain;
import net.codenamemeleon.preferredbiomes.worldgen.PreferredBiomeSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class PreferredBiomesScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int SPACING = 6;
	private static final int WIDGET_H = 20;
	private static final int LINE_H = 9;
	private static final int HALF_W = 150;

	private static final int SORT_W = 54;

	private static final int FILTER_W = (HALF_W * 2 - SPACING * 2) / 3;

	private static final Component FILTER_ALL = Component.translatable("preferred-biomes.screen.filter.all");

	private static final int LABEL_PADDING = 8;

	private static final String ELLIPSIS = "\u2026";

	private static final int TOOLTIP_PAD = 4;

	private static final int COLLAPSED = 12;

	private static final int ROWS_PER_COLUMN = 16;

	private static final int MAX_COLUMNS = 3;

	private static final int GUTTER = 12;

	private static final ResourceKey<MultiNoiseBiomeSourceParameterList> OVERWORLD_PARAMETERS =
			ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
					Identifier.parse("overworld"));

	private final CreateWorldScreen parent;
	private final WorldCreationContext generatorOptionsHolder;
	private final BiomeCatalog catalog;

	private final Set<Identifier> selected = new LinkedHashSet<>();

	private boolean islandChallenge;
	private int islandSize = PreferredBiomeSource.DEFAULT_ISLAND_SIZE;
	private float islandFrequency = PreferredBiomeSource.DEFAULT_ISLAND_FREQUENCY;
	private int islandNoise = PreferredBiomeSource.DEFAULT_ISLAND_NOISE;

	private BiomeListWidget.Sort sort = BiomeListWidget.Sort.BY_MOD;
	private Optional<BiomeCatalog.Temperature> temperatureFilter = Optional.empty();
	private Optional<BiomeCatalog.Humidity> humidityFilter = Optional.empty();
	private Optional<String> modFilter = Optional.empty();

	private BiomeListWidget list;
	private EditBox search;
	private CycleButton<BiomeListWidget.Sort> sortButton;
	private CycleButton<Optional<BiomeCatalog.Temperature>> temperatureButton;
	private CycleButton<Optional<BiomeCatalog.Humidity>> humidityButton;
	private CycleButton<Optional<String>> modButton;
	private Button selectAll;
	private Button selectNone;
	private Button islandSettings;
	private Button done;
	private int counterY;
	private int challengeNoteY;

	public PreferredBiomesScreen(CreateWorldScreen parent, WorldCreationContext generatorOptionsHolder) {
		super(Component.translatable("preferred-biomes.screen.title"));
		this.parent = parent;
		this.generatorOptionsHolder = generatorOptionsHolder;
		this.catalog = BiomeCatalog.build(generatorOptionsHolder.worldgenLoadContext());
		readCurrentSettings();
	}

	private void readCurrentSettings() {
		ChunkGenerator generator = this.generatorOptionsHolder.selectedDimensions().overworld();
		if (!(generator.getBiomeSource() instanceof PreferredBiomeSource source)) {
			return;
		}
		this.islandChallenge = source.islandSurvivalChallenge();
		this.islandSize = source.islandSize();
		this.islandFrequency = source.islandFrequency();
		this.islandNoise = source.islandNoise();
		Set<Identifier> excluded = Set.copyOf(source.excludedIds());
		for (BiomeCatalog.Entry entry : this.catalog.entries()) {
			if (!excluded.contains(entry.id())) {
				this.selected.add(entry.id());
			}
		}
	}

	@Override
	protected void init() {
		int centreX = this.width / 2;

		int y = MARGIN + LINE_H + SPACING;
		String previousFilter = this.search == null ? "" : this.search.getValue();
		int searchW = HALF_W * 2 - SORT_W - SPACING;
		this.search = new EditBox(this.font, centreX - HALF_W, y, searchW, WIDGET_H,
				Component.translatable("preferred-biomes.screen.search"));
		this.search.setHint(Component.translatable("preferred-biomes.screen.search")
				.copy().withStyle(ChatFormatting.DARK_GRAY));
		this.search.setResponder(text -> {
			if (this.list != null) {
				this.list.setFilter(text);
			}
		});
		this.search.setValue(previousFilter);
		addRenderableWidget(this.search);

		this.sortButton = addRenderableWidget(CycleButton
				.<BiomeListWidget.Sort>builder(value ->
						Component.translatable("preferred-biomes.screen.sort." + key(value.name())), this.sort)
				.withValues(BiomeListWidget.Sort.values())
				.displayOnlyValue()
				.create(centreX - HALF_W + searchW + SPACING, y, SORT_W, WIDGET_H,
						Component.translatable("preferred-biomes.screen.sort"),
						(button, value) -> {
							this.sort = value;
							this.list.setSort(value);
						}));
		y += WIDGET_H + SPACING;

		this.temperatureButton = addRenderableWidget(CycleButton
				.<Optional<BiomeCatalog.Temperature>>builder(value -> value
						.<Component>map(band -> Component.translatable("preferred-biomes.screen.filter." + key(band.name())))
						.orElse(FILTER_ALL), this.temperatureFilter)
				.withValues(withAll(List.of(BiomeCatalog.Temperature.values())))
				.create(centreX - HALF_W, y, FILTER_W, WIDGET_H,
						Component.translatable("preferred-biomes.screen.filter.temperature"),
						(button, value) -> {
							this.temperatureFilter = value;
							this.list.setTemperature(value.orElse(null));
						}));
		this.humidityButton = addRenderableWidget(CycleButton
				.<Optional<BiomeCatalog.Humidity>>builder(value -> value
						.<Component>map(band -> Component.translatable("preferred-biomes.screen.filter." + key(band.name())))
						.orElse(FILTER_ALL), this.humidityFilter)
				.withValues(withAll(List.of(BiomeCatalog.Humidity.values())))
				.create(centreX - HALF_W + FILTER_W + SPACING, y, FILTER_W, WIDGET_H,
						Component.translatable("preferred-biomes.screen.filter.humidity"),
						(button, value) -> {
							this.humidityFilter = value;
							this.list.setHumidity(value.orElse(null));
						}));
		this.modButton = addRenderableWidget(CycleButton
				.<Optional<String>>builder(this::modLabel, this.modFilter)
				.withValues(withAll(BiomeListWidget.modNames(this.catalog)))
				.displayOnlyValue()
				.create(centreX - HALF_W + (FILTER_W + SPACING) * 2, y, FILTER_W, WIDGET_H,
						Component.translatable("preferred-biomes.screen.filter.mod"),
						(button, value) -> {
							this.modFilter = value;
							this.list.setMod(value.orElse(null));
							updateModTooltip();
						}));
		updateModTooltip();
		y += WIDGET_H + SPACING;

		this.selectAll = addRenderableWidget(Button.builder(
						Component.translatable("preferred-biomes.screen.select_all"),
						button -> this.list.setVisibleSelected(true))
				.bounds(centreX - HALF_W, y, HALF_W - SPACING / 2, WIDGET_H).build());
		this.selectNone = addRenderableWidget(Button.builder(
						Component.translatable("preferred-biomes.screen.select_none"),
						button -> this.list.setVisibleSelected(false))
				.bounds(centreX + SPACING / 2, y, HALF_W - SPACING / 2, WIDGET_H).build());
		y += WIDGET_H + SPACING;

		int b = this.height - MARGIN - WIDGET_H;
		this.done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> apply())
				.bounds(centreX - HALF_W, b, HALF_W - SPACING / 2, WIDGET_H).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
				.bounds(centreX + SPACING / 2, b, HALF_W - SPACING / 2, WIDGET_H).build());

		b -= SPACING + LINE_H;
		this.challengeNoteY = b;

		b -= SPACING + WIDGET_H;
		addRenderableWidget(CycleButton.onOffBuilder(this.islandChallenge)
				.create(centreX - HALF_W, b, HALF_W - SPACING / 2, WIDGET_H,
						Component.translatable("preferred-biomes.screen.challenge"),
						(button, value) -> {
							this.islandChallenge = value;
							refresh();
						}));
		this.islandSettings = addRenderableWidget(Button.builder(
						Component.translatable("preferred-biomes.screen.island_settings"),
						button -> this.minecraft.setScreen(new IslandSettingsScreen(this,
								this.islandSize, this.islandFrequency, this.islandNoise,
								(size, frequency, noise) -> {
									this.islandSize = size;
									this.islandFrequency = frequency;
									this.islandNoise = noise;
								})))
				.bounds(centreX + SPACING / 2, b, HALF_W - SPACING / 2, WIDGET_H).build());

		b -= SPACING + LINE_H;
		this.counterY = b;
		b -= SPACING;

		this.list = new BiomeListWidget(this.minecraft, this.width, y, b,
				this.catalog, this.selected, this::refresh);
		this.list.setSort(this.sort);
		this.list.setTemperature(this.temperatureFilter.orElse(null));
		this.list.setHumidity(this.humidityFilter.orElse(null));
		this.list.setMod(this.modFilter.orElse(null));
		this.list.setFilter(previousFilter);
		addWidget(this.list);

		refresh();
	}

	private Component modLabel(Optional<String> value) {
		MutableComponent label = Component.translatable("preferred-biomes.screen.filter.mod").append(": ");
		if (value.isEmpty()) {
			return label.append(FILTER_ALL);
		}
		String name = value.get();
		int room = FILTER_W - LABEL_PADDING - this.font.width(label);
		String trimmed = this.font.plainSubstrByWidth(name, room);
		if (trimmed.length() < name.length()) {
			trimmed = this.font.plainSubstrByWidth(name, room - this.font.width(ELLIPSIS))
					+ ELLIPSIS;
		}
		return label.append(trimmed);
	}

	private void updateModTooltip() {
		if (this.modButton != null) {
			this.modButton.setTooltip(this.modFilter
					.map(name -> Tooltip.create(Component.literal(name)))
					.orElse(null));
		}
	}

	private List<Component> tooltipLines(BiomeCatalog.Entry entry) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(entry.modName()).withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE));
		lines.addAll(plantLines(entry.plants(), this.minecraft.hasShiftDown()));
		return lines;
	}

	private List<Component> plantLines(List<String> plants, boolean expanded) {
		List<Component> lines = new ArrayList<>();
		if (plants.isEmpty()) {
			return lines;
		}
		if (!expanded && plants.size() > COLLAPSED) {
			for (String plant : plants.subList(0, COLLAPSED)) {
				lines.add(Component.literal(cell(plant)));
			}
			lines.add(Component.literal(ELLIPSIS + " and " + (plants.size() - COLLAPSED)
					+ " more (hold Shift)").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		int rows = Math.min(plants.size(), ROWS_PER_COLUMN);
		int columns = Math.min((plants.size() + rows - 1) / rows, MAX_COLUMNS);
		int shown = Math.min(plants.size(), rows * columns);

		int[] start = new int[columns + 1];
		for (int c = 0; c < columns; c++) {
			int widest = 0;
			for (int r = 0; r < rows; r++) {
				int i = c * rows + r;
				if (i < shown) {
					widest = Math.max(widest, this.font.width(cell(plants.get(i))));
				}
			}
			start[c + 1] = start[c] + widest + GUTTER;
		}

		for (int r = 0; r < rows; r++) {
			StringBuilder line = new StringBuilder();
			for (int c = 0; c < columns; c++) {
				int i = c * rows + r;
				if (i >= shown) {
					break;
				}
				line.append(cell(plants.get(i)));
				if (c < columns - 1 && i + rows < shown) {
					while (this.font.width(line.toString()) < start[c + 1]) {
						line.append(' ');
					}
				}
			}
			lines.add(Component.literal(line.toString()));
		}
		if (shown < plants.size()) {
			lines.add(Component.literal(ELLIPSIS + " and " + (plants.size() - shown) + " more")
					.withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	private static String cell(String plant) {
		return "- " + plant;
	}

	private void drawPlantTooltip(GuiGraphicsExtractor context, List<Component> lines, int mouseX, int mouseY) {
		Font font = this.font;
		int linePitch = font.lineHeight + 1;
		int textW = 0;
		for (Component line : lines) {
			int w = font.width(line);
			if (line.getStyle().isBold()) {
				w += line.getString().length();
			}
			textW = Math.max(textW, w);
		}
		int textH = lines.size() * linePitch - 1;
		int boxW = textW + 2 * TOOLTIP_PAD;
		int boxH = textH + 2 * TOOLTIP_PAD;

		int x = mouseX + 12;
		int y = mouseY - 12;
		if (x + boxW + 1 > this.width) {
			x = mouseX - 12 - boxW;
		}
		y = Mth.clamp(y, 4, Math.max(4, this.height - boxH - 4));

		context.nextStratum();

		int x1 = x - 1;
		int y1 = y - 1;
		int x2 = x + boxW + 1;
		int y2 = y + boxH + 1;
		context.fill(x1, y1, x2, y2, 0xF0100010);
		context.fillGradient(x1, y1, x2, y1 + 1, 0x505000FF, 0x505000FF);
		context.fillGradient(x1, y2 - 1, x2, y2, 0x5028007F, 0x5028007F);
		context.fillGradient(x1, y1 + 1, x1 + 1, y2 - 1, 0x505000FF, 0x5028007F);
		context.fillGradient(x2 - 1, y1 + 1, x2, y2 - 1, 0x505000FF, 0x5028007F);

		int textX = x + TOOLTIP_PAD;
		int textY = y + TOOLTIP_PAD;
		for (Component line : lines) {
			context.text(font, line, textX, textY, 0xFFFFFFFF);
			textY += linePitch;
		}
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private static <T> List<Optional<T>> withAll(List<T> values) {
		List<Optional<T>> out = new ArrayList<>();
		out.add(Optional.empty());
		for (T value : values) {
			out.add(Optional.of(value));
		}
		return List.copyOf(out);
	}

	private void refresh() {
		boolean checklistLive = !this.islandChallenge;
		if (this.list != null) {
			this.list.setEnabled(checklistLive);
		}
		if (this.search != null) {
			this.search.setEditable(checklistLive);
		}
		if (this.selectAll != null) {
			this.selectAll.active = checklistLive;
		}
		if (this.selectNone != null) {
			this.selectNone.active = checklistLive;
		}
		for (CycleButton<?> button : new CycleButton<?>[] {
				this.sortButton, this.temperatureButton, this.humidityButton, this.modButton }) {
			if (button != null) {
				button.active = checklistLive;
			}
		}
		if (this.islandSettings != null) {
			this.islandSettings.visible = this.islandChallenge;
		}
		if (this.done != null) {
			this.done.active = this.islandChallenge || !this.selected.isEmpty();
		}
	}

	private void apply() {
		List<Identifier> excluded = new ArrayList<>();
		for (BiomeCatalog.Entry entry : this.catalog.entries()) {
			if (!this.selected.contains(entry.id())) {
				excluded.add(entry.id());
			}
		}
		boolean challenge = this.islandChallenge;
		int size = this.islandSize;
		float frequency = this.islandFrequency;
		int noise = this.islandNoise;
		this.parent.getUiState().updateDimensions((registryManager, dimensions) -> {
			var settings = challenge
					? Holder.direct(
							IslandTerrain.createSettings(registryManager, size, frequency, noise))
					: registryManager.lookupOrThrow(Registries.NOISE_SETTINGS)
							.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
			PreferredBiomeSource source;
			if (challenge) {
				var biomeLookup = registryManager.lookupOrThrow(Registries.BIOME);
				List<Holder<Biome>> declared = List.of(
						biomeLookup.getOrThrow(Biomes.SNOWY_PLAINS),
						biomeLookup.getOrThrow(Biomes.SWAMP),
						biomeLookup.getOrThrow(Biomes.BEACH));
				source = new PreferredBiomeSource(
						Either.left(IslandBiomes.entries(biomeLookup)),
						List.of(), true, size, frequency, noise, declared);
			} else {
				source = new PreferredBiomeSource(
						Either.right(registryManager
								.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
								.getOrThrow(OVERWORLD_PARAMETERS)),
						excluded, false, size, frequency);
			}
			return dimensions.replaceOverworldGenerator(registryManager, new NoiseBasedChunkGenerator(source, settings));
		});
		this.onClose();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		this.list.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(this.font, this.title, this.width / 2, MARGIN, 0xFFFFFFFF);
		context.centeredText(this.font,
				Component.translatable("preferred-biomes.screen.counter",
						this.selected.size(), this.catalog.size()),
				this.width / 2, this.counterY, this.islandChallenge ? 0xFF606060 : 0xFFA0A0A0);
		if (this.islandChallenge) {
			context.centeredText(this.font,
					Component.translatable("preferred-biomes.screen.challenge_note"),
					this.width / 2, this.challengeNoteY, 0xFFA0A0A0);
		}
		super.extractRenderState(context, mouseX, mouseY, delta);
		BiomeCatalog.Entry hovered = this.list.hoveredEntry();
		if (hovered != null) {
			drawPlantTooltip(context, tooltipLines(hovered), mouseX, mouseY);
		}
	}
}
