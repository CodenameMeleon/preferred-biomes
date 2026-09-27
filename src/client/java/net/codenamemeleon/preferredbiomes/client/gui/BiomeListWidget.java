package net.codenamemeleon.preferredbiomes.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class BiomeListWidget extends ContainerObjectSelectionList<BiomeListWidget.AbstractEntry> {

	public enum Sort {
		BY_MOD, A_TO_Z, Z_TO_A
	}

	private static final int BOX = 9;
	private static final int GAP = 5;
	private static final int OUTLINE = 0xFFA0A0A0;
	private static final int BOX_BACKGROUND = 0xFF101010;
	private static final int TICK = 0xFFFFFFFF;
	private static final int NAME = 0xFFFFFFFF;
	private static final int MOD_GREY = 0xFF707070;
	private static final int HEADER = 0xFFFFD700;
	private static final int DISABLED = 0xFF606060;
	private static final String VANILLA = "minecraft";

	private final BiomeCatalog catalog;
	private final Set<Identifier> selected;
	private final Runnable onChanged;
	private String filter = "";
	private Sort sort = Sort.BY_MOD;
	private BiomeCatalog.Temperature temperature;
	private BiomeCatalog.Humidity humidity;
	private String mod;
	private boolean enabled = true;
	private BiomeCatalog.Entry hovered;

	public BiomeListWidget(Minecraft client, int width, int top, int bottom,
			BiomeCatalog catalog, Set<Identifier> selected, Runnable onChanged) {
		super(client, width, bottom - top, top, 14);
		this.catalog = catalog;
		this.selected = selected;
		this.onChanged = onChanged;
		rebuild();
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		this.hovered = null;
		super.extractWidgetRenderState(context, mouseX, mouseY, delta);
	}

	public BiomeCatalog.Entry hoveredEntry() {
		return this.hovered;
	}

	@Override
	public int getRowWidth() {
		return Math.min(this.width - 20, 320);
	}

	@Override
	protected int scrollBarX() {
		return this.width / 2 + getRowWidth() / 2 + 4;
	}

	private static List<String> namespaces(BiomeCatalog catalog) {
		return catalog.entries().stream()
				.map(BiomeCatalog.Entry::namespace)
				.distinct()
				.sorted(Comparator.comparingInt((String ns) -> ns.equals(VANILLA) ? 0 : 1)
						.thenComparing(Comparator.naturalOrder()))
				.toList();
	}

	private Map<String, List<BiomeCatalog.Entry>> groups() {
		Map<String, List<BiomeCatalog.Entry>> byNamespace = new LinkedHashMap<>();
		for (String namespace : namespaces(this.catalog)) {
			byNamespace.put(namespace, this.catalog.entries().stream()
					.filter(entry -> entry.namespace().equals(namespace))
					.sorted(Comparator.comparing(BiomeCatalog.Entry::displayName,
							String.CASE_INSENSITIVE_ORDER))
					.toList());
		}
		return byNamespace;
	}

	public static List<String> modNames(BiomeCatalog catalog) {
		List<String> names = new ArrayList<>();
		for (String namespace : namespaces(catalog)) {
			catalog.entries().stream()
					.filter(entry -> entry.namespace().equals(namespace))
					.map(BiomeCatalog.Entry::modName)
					.findFirst()
					.filter(name -> !names.contains(name))
					.ifPresent(names::add);
		}
		return List.copyOf(names);
	}

	private boolean matches(BiomeCatalog.Entry entry) {
		if (this.temperature != null
				&& BiomeCatalog.temperatureBand(entry.temperature()) != this.temperature) {
			return false;
		}
		if (this.humidity != null
				&& BiomeCatalog.humidityBand(entry.downfall()) != this.humidity) {
			return false;
		}
		if (this.mod != null && !this.mod.equals(entry.modName())) {
			return false;
		}
		if (this.filter.isEmpty()) {
			return true;
		}
		String needle = this.filter.toLowerCase(Locale.ROOT);
		return entry.displayName().toLowerCase(Locale.ROOT).contains(needle)
				|| entry.id().toString().toLowerCase(Locale.ROOT).contains(needle);
	}

	public void setFilter(String filter) {
		this.filter = filter == null ? "" : filter.trim();
		rebuild();
		setScrollAmount(0.0);
	}

	public void setSort(Sort sort) {
		this.sort = sort == null ? Sort.BY_MOD : sort;
		rebuild();
		setScrollAmount(0.0);
	}

	public void setTemperature(BiomeCatalog.Temperature temperature) {
		this.temperature = temperature;
		rebuild();
		setScrollAmount(0.0);
	}

	public void setHumidity(BiomeCatalog.Humidity humidity) {
		this.humidity = humidity;
		rebuild();
		setScrollAmount(0.0);
	}

	public void setMod(String mod) {
		this.mod = mod;
		rebuild();
		setScrollAmount(0.0);
	}

	private void rebuild() {
		clearEntries();
		if (this.sort == Sort.BY_MOD) {
			rebuildGrouped();
			return;
		}
		Comparator<BiomeCatalog.Entry> order = Comparator.comparing(
				BiomeCatalog.Entry::displayName, String.CASE_INSENSITIVE_ORDER);
		if (this.sort == Sort.Z_TO_A) {
			order = order.reversed();
		}
		this.catalog.entries().stream()
				.filter(this::matches)
				.sorted(order)
				.forEach(entry -> addEntry(new BiomeEntry(entry, true)));
	}

	private void rebuildGrouped() {
		for (Map.Entry<String, List<BiomeCatalog.Entry>> group : groups().entrySet()) {
			List<BiomeCatalog.Entry> visible = group.getValue().stream().filter(this::matches).toList();
			if (visible.isEmpty()) {
				continue;
			}
			addEntry(new HeaderEntry(group.getValue().get(0).modName(), group.getValue()));
			for (BiomeCatalog.Entry entry : visible) {
				addEntry(new BiomeEntry(entry, false));
			}
		}
	}

	public List<BiomeCatalog.Entry> visibleEntries() {
		List<BiomeCatalog.Entry> out = new ArrayList<>();
		for (AbstractEntry entry : children()) {
			if (entry instanceof BiomeEntry row) {
				out.add(row.entry);
			}
		}
		return out;
	}

	public void setVisibleSelected(boolean ticked) {
		for (BiomeCatalog.Entry entry : visibleEntries()) {
			if (ticked) {
				this.selected.add(entry.id());
			} else {
				this.selected.remove(entry.id());
			}
		}
		this.onChanged.run();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!this.enabled) {
			return false;
		}
		int keyCode = event.key();
		if (keyCode == 32 || keyCode == 257 || keyCode == 335) {
			AbstractEntry focused = getSelected();
			if (focused != null) {
				focused.toggle();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	public abstract static class AbstractEntry extends ContainerObjectSelectionList.Entry<AbstractEntry> {
		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}

		abstract void toggle();
	}

	public class HeaderEntry extends AbstractEntry {
		private final String modName;
		private final List<BiomeCatalog.Entry> group;

		HeaderEntry(String modName, List<BiomeCatalog.Entry> group) {
			this.modName = modName;
			this.group = group;
		}

		private int selectedCount() {
			int n = 0;
			for (BiomeCatalog.Entry entry : this.group) {
				if (BiomeListWidget.this.selected.contains(entry.id())) {
					n++;
				}
			}
			return n;
		}

		@Override
		void toggle() {
			boolean all = selectedCount() == this.group.size();
			for (BiomeCatalog.Entry entry : this.group) {
				if (all) {
					BiomeListWidget.this.selected.remove(entry.id());
				} else {
					BiomeListWidget.this.selected.add(entry.id());
				}
			}
			BiomeListWidget.this.onChanged.run();
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() == 0 && BiomeListWidget.this.enabled) {
				BiomeListWidget.this.setSelected(this);
				toggle();
				return true;
			}
			return false;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			int x = getContentX();
			int y = getContentY();
			Component text = Component.literal(this.modName + " (" + selectedCount() + "/" + this.group.size() + ")");
			int colour = BiomeListWidget.this.enabled ? HEADER : DISABLED;
			context.text(BiomeListWidget.this.minecraft.font, text, x, y + 2, colour);
		}
	}

	public class BiomeEntry extends AbstractEntry {
		private final BiomeCatalog.Entry entry;
		private final boolean showMod;

		BiomeEntry(BiomeCatalog.Entry entry, boolean showMod) {
			this.entry = entry;
			this.showMod = showMod;
		}

		@Override
		void toggle() {
			if (!BiomeListWidget.this.selected.remove(this.entry.id())) {
				BiomeListWidget.this.selected.add(this.entry.id());
			}
			BiomeListWidget.this.onChanged.run();
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() == 0 && BiomeListWidget.this.enabled) {
				BiomeListWidget.this.setSelected(this);
				toggle();
				return true;
			}
			return false;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			int x = getContentX();
			int y = getContentY();
			int entryWidth = getWidth();
			boolean on = BiomeListWidget.this.enabled;
			int boxY = y + 1;
			context.fill(x, boxY, x + BOX, boxY + BOX, on ? OUTLINE : DISABLED);
			context.fill(x + 1, boxY + 1, x + BOX - 1, boxY + BOX - 1, BOX_BACKGROUND);
			if (BiomeListWidget.this.selected.contains(this.entry.id())) {
				context.fill(x + 2, boxY + 2, x + BOX - 2, boxY + BOX - 2, on ? TICK : DISABLED);
			}
			if (hovered) {
				BiomeListWidget.this.hovered = this.entry;
			}
			Font font = BiomeListWidget.this.minecraft.font;
			int textX = x + BOX + GAP;
			context.text(font, Component.literal(this.entry.displayName()), textX, y + 2,
					on ? NAME : DISABLED);
			if (!this.showMod) {
				return;
			}
			int modX = x + entryWidth - font.width(this.entry.modName());
			if (modX > textX + font.width(this.entry.displayName()) + GAP) {
				context.text(font, Component.literal(this.entry.modName()), modX, y + 2,
						on ? MOD_GREY : DISABLED);
			}
		}
	}
}
