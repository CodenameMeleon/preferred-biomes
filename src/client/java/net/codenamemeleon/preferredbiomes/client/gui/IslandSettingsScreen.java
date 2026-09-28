package net.codenamemeleon.preferredbiomes.client.gui;

import net.codenamemeleon.preferredbiomes.worldgen.PreferredBiomeSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class IslandSettingsScreen extends Screen {

	private static final int MARGIN = 8;
	private static final int SPACING = 6;
	private static final int WIDGET_H = 20;
	private static final int LINE_H = 9;
	private static final int HALF_W = 150;

	private static final int FREQUENCY_STEP = 5;

	@FunctionalInterface
	public interface Settings {
		void accept(int size, float frequency, int noise);
	}

	private final Screen parent;
	private final Settings onDone;

	private int islandSize;
	private float islandFrequency;
	private int islandNoise;

	public IslandSettingsScreen(Screen parent, int islandSize, float islandFrequency,
			int islandNoise, Settings onDone) {
		super(Component.translatable("preferred-biomes.islands.title"));
		this.parent = parent;
		this.islandSize = islandSize;
		this.islandFrequency = islandFrequency;
		this.islandNoise = islandNoise;
		this.onDone = onDone;
	}

	@Override
	protected void init() {
		int centreX = this.width / 2;
		int y = MARGIN + LINE_H + SPACING * 3;

		addRenderableWidget(new SizeSlider(centreX - HALF_W, y, HALF_W * 2, WIDGET_H));
		y += WIDGET_H + SPACING;
		addRenderableWidget(new FrequencySlider(centreX - HALF_W, y, HALF_W * 2, WIDGET_H));
		y += WIDGET_H + SPACING;
		addRenderableWidget(new NoiseSlider(centreX - HALF_W, y, HALF_W * 2, WIDGET_H));

		int b = this.height - MARGIN - WIDGET_H;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
			this.onDone.accept(this.islandSize, this.islandFrequency, this.islandNoise);
			this.onClose();
		}).bounds(centreX - HALF_W, b, HALF_W - SPACING / 2, WIDGET_H).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
				.bounds(centreX + SPACING / 2, b, HALF_W - SPACING / 2, WIDGET_H).build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.centeredText(this.font, this.title, this.width / 2, MARGIN, 0xFFFFFFFF);
		super.extractRenderState(context, mouseX, mouseY, delta);
	}

	private class SizeSlider extends AbstractSliderButton {
		SizeSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(),
					toFraction(IslandSettingsScreen.this.islandSize));
			updateMessage();
		}

		private static double toFraction(int blocks) {
			return (double) (blocks - PreferredBiomeSource.MIN_ISLAND_SIZE)
					/ (PreferredBiomeSource.MAX_ISLAND_SIZE - PreferredBiomeSource.MIN_ISLAND_SIZE);
		}

		private int blocks() {
			return PreferredBiomeSource.MIN_ISLAND_SIZE + (int) Math.round(this.value
					* (PreferredBiomeSource.MAX_ISLAND_SIZE - PreferredBiomeSource.MIN_ISLAND_SIZE));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("preferred-biomes.islands.size", blocks()));
		}

		@Override
		protected void applyValue() {
			IslandSettingsScreen.this.islandSize = blocks();
		}
	}

	private class FrequencySlider extends AbstractSliderButton {
		FrequencySlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(), IslandSettingsScreen.this.islandFrequency);
			updateMessage();
		}

		private int percent() {
			int raw = (int) Math.round(this.value * 100.0);
			return Mth.clamp(Math.round((float) raw / FREQUENCY_STEP) * FREQUENCY_STEP, 0, 100);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("preferred-biomes.islands.frequency", percent()));
		}

		@Override
		protected void applyValue() {
			IslandSettingsScreen.this.islandFrequency = percent() / 100.0F;
		}
	}

	private class NoiseSlider extends AbstractSliderButton {
		NoiseSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Component.empty(),
					toFraction(IslandSettingsScreen.this.islandNoise));
			updateMessage();
		}

		private static double toFraction(int level) {
			return (double) (level - PreferredBiomeSource.MIN_ISLAND_NOISE)
					/ (PreferredBiomeSource.MAX_ISLAND_NOISE - PreferredBiomeSource.MIN_ISLAND_NOISE);
		}

		private int level() {
			return PreferredBiomeSource.MIN_ISLAND_NOISE + (int) Math.round(this.value
					* (PreferredBiomeSource.MAX_ISLAND_NOISE - PreferredBiomeSource.MIN_ISLAND_NOISE));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("preferred-biomes.islands.noise", level()));
		}

		@Override
		protected void applyValue() {
			IslandSettingsScreen.this.islandNoise = level();
		}
	}
}
