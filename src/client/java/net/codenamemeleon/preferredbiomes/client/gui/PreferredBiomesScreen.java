package net.codenamemeleon.preferredbiomes.client.gui;

import com.mojang.datafixers.util.Either;
import net.codenamemeleon.preferredbiomes.PreferredBiomes;
import net.codenamemeleon.preferredbiomes.worldgen.IslandBiomes;
import net.codenamemeleon.preferredbiomes.worldgen.IslandTerrain;
import net.codenamemeleon.preferredbiomes.worldgen.PreferredBiomeSource;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

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

	private static final Text FILTER_ALL = Text.translatable("preferred-biomes.screen.filter.all");

	private static final int LABEL_PADDING = 8;

	private static final String ELLIPSIS = "\u2026";

	private static final int TOOLTIP_PAD = 4;

	private static final int COLLAPSED = 12;

	private static final int ROWS_PER_COLUMN = 16;

	private static final int MAX_COLUMNS = 3;

	private static final int GUTTER = 12;

	private static final RegistryKey<MultiNoiseBiomeSourceParameterList> OVERWORLD_PARAMETERS =
			RegistryKey.of(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
					new Identifier("overworld"));

	private final CreateWorldScreen parent;
	private final GeneratorOptionsHolder generatorOptionsHolder;
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

	private boolean tooltipWidthWarned;

	private BiomeListWidget list;
	private TextFieldWidget search;
	private CyclingButtonWidget<BiomeListWidget.Sort> sortButton;
	private CyclingButtonWidget<Optional<BiomeCatalog.Temperature>> temperatureButton;
	private CyclingButtonWidget<Optional<BiomeCatalog.Humidity>> humidityButton;
	private CyclingButtonWidget<Optional<String>> modButton;
	private ButtonWidget selectAll;
	private ButtonWidget selectNone;
	private ButtonWidget islandSettings;
	private ButtonWidget done;
	private int counterY;
	private int challengeNoteY;

	public PreferredBiomesScreen(CreateWorldScreen parent, GeneratorOptionsHolder generatorOptionsHolder) {
		super(Text.translatable("preferred-biomes.screen.title"));
		this.parent = parent;
		this.generatorOptionsHolder = generatorOptionsHolder;
		this.catalog = BiomeCatalog.build(generatorOptionsHolder.getCombinedRegistryManager());
		readCurrentSettings();
	}

	private void readCurrentSettings() {
		ChunkGenerator generator = this.generatorOptionsHolder.selectedDimensions().getChunkGenerator();
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
		String previousFilter = this.search == null ? "" : this.search.getText();
		int searchW = HALF_W * 2 - SORT_W - SPACING;
		this.search = new TextFieldWidget(this.textRenderer, centreX - HALF_W, y, searchW, WIDGET_H,
				Text.translatable("preferred-biomes.screen.search"));
		this.search.setPlaceholder(Text.translatable("preferred-biomes.screen.search")
				.copy().formatted(Formatting.DARK_GRAY));
		this.search.setChangedListener(text -> {
			if (this.list != null) {
				this.list.setFilter(text);
			}
		});
		this.search.setText(previousFilter);
		addDrawableChild(this.search);

		this.sortButton = addDrawableChild(CyclingButtonWidget
				.<BiomeListWidget.Sort>builder(value ->
						Text.translatable("preferred-biomes.screen.sort." + key(value.name())))
				.values(BiomeListWidget.Sort.values())
				.initially(this.sort)
				.omitKeyText()
				.build(centreX - HALF_W + searchW + SPACING, y, SORT_W, WIDGET_H,
						Text.translatable("preferred-biomes.screen.sort"),
						(button, value) -> {
							this.sort = value;
							this.list.setSort(value);
						}));
		y += WIDGET_H + SPACING;

		this.temperatureButton = addDrawableChild(CyclingButtonWidget
				.<Optional<BiomeCatalog.Temperature>>builder(value -> value
						.<Text>map(band -> Text.translatable("preferred-biomes.screen.filter." + key(band.name())))
						.orElse(FILTER_ALL))
				.values(withAll(List.of(BiomeCatalog.Temperature.values())))
				.initially(this.temperatureFilter)
				.build(centreX - HALF_W, y, FILTER_W, WIDGET_H,
						Text.translatable("preferred-biomes.screen.filter.temperature"),
						(button, value) -> {
							this.temperatureFilter = value;
							this.list.setTemperature(value.orElse(null));
						}));
		this.humidityButton = addDrawableChild(CyclingButtonWidget
				.<Optional<BiomeCatalog.Humidity>>builder(value -> value
						.<Text>map(band -> Text.translatable("preferred-biomes.screen.filter." + key(band.name())))
						.orElse(FILTER_ALL))
				.values(withAll(List.of(BiomeCatalog.Humidity.values())))
				.initially(this.humidityFilter)
				.build(centreX - HALF_W + FILTER_W + SPACING, y, FILTER_W, WIDGET_H,
						Text.translatable("preferred-biomes.screen.filter.humidity"),
						(button, value) -> {
							this.humidityFilter = value;
							this.list.setHumidity(value.orElse(null));
						}));
		this.modButton = addDrawableChild(CyclingButtonWidget
				.<Optional<String>>builder(this::modLabel)
				.values(withAll(BiomeListWidget.modNames(this.catalog)))
				.initially(this.modFilter)
				.omitKeyText()
				.build(centreX - HALF_W + (FILTER_W + SPACING) * 2, y, FILTER_W, WIDGET_H,
						Text.translatable("preferred-biomes.screen.filter.mod"),
						(button, value) -> {
							this.modFilter = value;
							this.list.setMod(value.orElse(null));
							updateModTooltip();
						}));
		updateModTooltip();
		y += WIDGET_H + SPACING;

		this.selectAll = addDrawableChild(ButtonWidget.builder(
						Text.translatable("preferred-biomes.screen.select_all"),
						button -> this.list.setVisibleSelected(true))
				.dimensions(centreX - HALF_W, y, HALF_W - SPACING / 2, WIDGET_H).build());
		this.selectNone = addDrawableChild(ButtonWidget.builder(
						Text.translatable("preferred-biomes.screen.select_none"),
						button -> this.list.setVisibleSelected(false))
				.dimensions(centreX + SPACING / 2, y, HALF_W - SPACING / 2, WIDGET_H).build());
		y += WIDGET_H + SPACING;

		int b = this.height - MARGIN - WIDGET_H;
		this.done = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> apply())
				.dimensions(centreX - HALF_W, b, HALF_W - SPACING / 2, WIDGET_H).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close())
				.dimensions(centreX + SPACING / 2, b, HALF_W - SPACING / 2, WIDGET_H).build());

		b -= SPACING + LINE_H;
		this.challengeNoteY = b;

		b -= SPACING + WIDGET_H;
		addDrawableChild(CyclingButtonWidget.onOffBuilder(this.islandChallenge)
				.build(centreX - HALF_W, b, HALF_W - SPACING / 2, WIDGET_H,
						Text.translatable("preferred-biomes.screen.challenge"),
						(button, value) -> {
							this.islandChallenge = value;
							refresh();
						}));
		this.islandSettings = addDrawableChild(ButtonWidget.builder(
						Text.translatable("preferred-biomes.screen.island_settings"),
						button -> this.client.setScreen(new IslandSettingsScreen(this,
								this.islandSize, this.islandFrequency, this.islandNoise,
								(size, frequency, noise) -> {
									this.islandSize = size;
									this.islandFrequency = frequency;
									this.islandNoise = noise;
								})))
				.dimensions(centreX + SPACING / 2, b, HALF_W - SPACING / 2, WIDGET_H).build());

		b -= SPACING + LINE_H;
		this.counterY = b;
		b -= SPACING;

		this.list = new BiomeListWidget(this.client, this.width, y, b,
				this.catalog, this.selected, this::refresh);
		this.list.setSort(this.sort);
		this.list.setTemperature(this.temperatureFilter.orElse(null));
		this.list.setHumidity(this.humidityFilter.orElse(null));
		this.list.setMod(this.modFilter.orElse(null));
		this.list.setFilter(previousFilter);
		addSelectableChild(this.list);

		refresh();
	}

	private Text modLabel(Optional<String> value) {
		MutableText label = Text.translatable("preferred-biomes.screen.filter.mod").append(": ");
		if (value.isEmpty()) {
			return label.append(FILTER_ALL);
		}
		String name = value.get();
		int room = FILTER_W - LABEL_PADDING - this.textRenderer.getWidth(label);
		String trimmed = this.textRenderer.trimToWidth(name, room);
		if (trimmed.length() < name.length()) {
			trimmed = this.textRenderer.trimToWidth(name, room - this.textRenderer.getWidth(ELLIPSIS))
					+ ELLIPSIS;
		}
		return label.append(trimmed);
	}

	private void updateModTooltip() {
		if (this.modButton != null) {
			this.modButton.setTooltip(this.modFilter
					.map(name -> Tooltip.of(Text.literal(name)))
					.orElse(null));
		}
	}

	private List<Text> tooltipLines(BiomeCatalog.Entry entry) {
		List<Text> lines = new ArrayList<>();
		lines.add(Text.literal(entry.modName()).formatted(Formatting.BOLD, Formatting.UNDERLINE));
		lines.addAll(plantLines(entry.plants(), hasShiftDown()));
		return lines;
	}

	private List<Text> plantLines(List<String> plants, boolean expanded) {
		List<Text> lines = new ArrayList<>();
		if (plants.isEmpty()) {
			return lines;
		}
		if (!expanded && plants.size() > COLLAPSED) {
			for (String plant : plants.subList(0, COLLAPSED)) {
				lines.add(Text.literal(cell(plant)));
			}
			lines.add(Text.literal(ELLIPSIS + " and " + (plants.size() - COLLAPSED)
					+ " more (hold Shift)").formatted(Formatting.GRAY));
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
					widest = Math.max(widest, this.textRenderer.getWidth(cell(plants.get(i))));
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
					while (this.textRenderer.getWidth(line.toString()) < start[c + 1]) {
						line.append(' ');
					}
				}
			}
			lines.add(Text.literal(line.toString()));
		}
		if (shown < plants.size()) {
			lines.add(Text.literal(ELLIPSIS + " and " + (plants.size() - shown) + " more")
					.formatted(Formatting.GRAY));
		}
		return lines;
	}

	private static String cell(String plant) {
		return "- " + plant;
	}

	private void drawPlantTooltip(DrawContext context, List<Text> lines, int mouseX, int mouseY) {
		TextRenderer font = this.textRenderer;
		int linePitch = font.fontHeight + 1;
		int textW = 0;
		for (Text line : lines) {
			int w = font.getWidth(line);
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
		y = MathHelper.clamp(y, 4, Math.max(4, this.height - boxH - 4));

		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 400.0F);

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
		for (Text line : lines) {
			int end = context.drawTextWithShadow(font, line, textX, textY, 0xFFFFFF);
			if (end > textX + textW && !this.tooltipWidthWarned) {
				this.tooltipWidthWarned = true;
				PreferredBiomes.LOGGER.warn("tooltip line drew {} px past its measured width: {}",
						end - textX - textW, line.getString());
			}
			textY += linePitch;
		}
		context.getMatrices().pop();
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
		for (CyclingButtonWidget<?> button : new CyclingButtonWidget<?>[] {
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
		this.parent.getWorldCreator().applyModifier((registryManager, dimensions) -> {
			var settings = challenge
					? RegistryEntry.of(
							IslandTerrain.createSettings(registryManager, size, frequency, noise))
					: registryManager.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
							.entryOf(ChunkGeneratorSettings.OVERWORLD);
			PreferredBiomeSource source;
			if (challenge) {
				var biomeLookup = registryManager.getWrapperOrThrow(RegistryKeys.BIOME);
				List<RegistryEntry<Biome>> declared = List.of(
						biomeLookup.getOrThrow(BiomeKeys.SNOWY_PLAINS),
						biomeLookup.getOrThrow(BiomeKeys.SWAMP),
						biomeLookup.getOrThrow(BiomeKeys.BEACH));
				source = new PreferredBiomeSource(
						Either.left(IslandBiomes.entries(biomeLookup)),
						List.of(), true, size, frequency, noise, declared);
			} else {
				source = new PreferredBiomeSource(
						Either.right(registryManager
								.get(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
								.entryOf(OVERWORLD_PARAMETERS)),
						excluded, false, size, frequency);
			}
			return dimensions.with(registryManager, new NoiseChunkGenerator(source, settings));
		});
		this.close();
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		this.list.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, MARGIN, 0xFFFFFF);
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("preferred-biomes.screen.counter",
						this.selected.size(), this.catalog.size()),
				this.width / 2, this.counterY, this.islandChallenge ? 0x606060 : 0xA0A0A0);
		if (this.islandChallenge) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("preferred-biomes.screen.challenge_note"),
					this.width / 2, this.challengeNoteY, 0xA0A0A0);
		}
		super.render(context, mouseX, mouseY, delta);
		BiomeCatalog.Entry hovered = this.list.hoveredEntry();
		if (hovered != null) {
			drawPlantTooltip(context, tooltipLines(hovered), mouseX, mouseY);
		}
	}
}
