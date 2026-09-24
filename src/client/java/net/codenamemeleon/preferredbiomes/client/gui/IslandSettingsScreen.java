package net.codenamemeleon.preferredbiomes.client.gui;

import net.codenamemeleon.preferredbiomes.worldgen.PreferredBiomeSource;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

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
		super(Text.translatable("preferred-biomes.islands.title"));
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

		addDrawableChild(new SizeSlider(centreX - HALF_W, y, HALF_W * 2, WIDGET_H));
		y += WIDGET_H + SPACING;
		addDrawableChild(new FrequencySlider(centreX - HALF_W, y, HALF_W * 2, WIDGET_H));
		y += WIDGET_H + SPACING;
		addDrawableChild(new NoiseSlider(centreX - HALF_W, y, HALF_W * 2, WIDGET_H));

		int b = this.height - MARGIN - WIDGET_H;
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> {
			this.onDone.accept(this.islandSize, this.islandFrequency, this.islandNoise);
			this.close();
		}).dimensions(centreX - HALF_W, b, HALF_W - SPACING / 2, WIDGET_H).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close())
				.dimensions(centreX + SPACING / 2, b, HALF_W - SPACING / 2, WIDGET_H).build());
	}

	@Override
	public void close() {
		this.client.setScreen(this.parent);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, MARGIN, 0xFFFFFF);
		super.render(context, mouseX, mouseY, delta);
	}

	private class SizeSlider extends SliderWidget {
		SizeSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Text.empty(),
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
			setMessage(Text.translatable("preferred-biomes.islands.size", blocks()));
		}

		@Override
		protected void applyValue() {
			IslandSettingsScreen.this.islandSize = blocks();
		}
	}

	private class FrequencySlider extends SliderWidget {
		FrequencySlider(int x, int y, int width, int height) {
			super(x, y, width, height, Text.empty(), IslandSettingsScreen.this.islandFrequency);
			updateMessage();
		}

		private int percent() {
			int raw = (int) Math.round(this.value * 100.0);
			return MathHelper.clamp(Math.round((float) raw / FREQUENCY_STEP) * FREQUENCY_STEP, 0, 100);
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("preferred-biomes.islands.frequency", percent()));
		}

		@Override
		protected void applyValue() {
			IslandSettingsScreen.this.islandFrequency = percent() / 100.0F;
		}
	}

	private class NoiseSlider extends SliderWidget {
		NoiseSlider(int x, int y, int width, int height) {
			super(x, y, width, height, Text.empty(),
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
			setMessage(Text.translatable("preferred-biomes.islands.noise", level()));
		}

		@Override
		protected void applyValue() {
			IslandSettingsScreen.this.islandNoise = level();
		}
	}
}
