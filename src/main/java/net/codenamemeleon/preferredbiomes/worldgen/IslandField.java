package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public record IslandField(int size, float frequency, int noise, IslandField.Channel channel,
		DensityFunction.NoiseHolder jitter, DensityFunction.NoiseHolder shape,
		DensityFunction.NoiseHolder offset, DensityFunction.NoiseHolder temperature)
		implements DensityFunction {

	public enum Channel {
		HEIGHT("height"),
		SIZE("size"),
		ROLL("roll"),
		SHORE("shore"),
		RADIUS("radius"),
		ANCHOR_TEMPERATURE("anchor_temperature"),
		SHORE_RING("shore_ring");

		private final String name;

		Channel(String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}

		public static Channel byName(String name) {
			for (Channel channel : values()) {
				if (channel.name.equals(name)) {
					return channel;
				}
			}
			return HEIGHT;
		}
	}

	private static final Codec<Channel> CHANNEL_CODEC =
			Codec.STRING.xmap(Channel::byName, Channel::getName);

	public static final MapCodec<IslandField> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(PreferredBiomeSource.MIN_ISLAND_SIZE, PreferredBiomeSource.MAX_ISLAND_SIZE)
					.fieldOf("size").forGetter(IslandField::size),
			Codec.floatRange(0.0F, 1.0F).fieldOf("frequency").forGetter(IslandField::frequency),
			Codec.INT.optionalFieldOf("noise", PreferredBiomeSource.DEFAULT_ISLAND_NOISE)
					.forGetter(IslandField::noise),
			CHANNEL_CODEC.fieldOf("channel").forGetter(IslandField::channel),
			DensityFunction.NoiseHolder.CODEC.fieldOf("jitter").forGetter(IslandField::jitter),
			DensityFunction.NoiseHolder.CODEC.fieldOf("shape").forGetter(IslandField::shape),
			DensityFunction.NoiseHolder.CODEC.fieldOf("offset").forGetter(IslandField::offset),
			DensityFunction.NoiseHolder.CODEC.fieldOf("temperature").forGetter(IslandField::temperature)
	).apply(instance, IslandField::new));

	public static final KeyDispatchDataCodec<IslandField> CODEC_HOLDER = KeyDispatchDataCodec.of(MAP_CODEC);

	public static final double SHELF_MIN = 150.0;

	public static final double SHELF_MAX = 200.0;

	private static final double WATERLINE = 63.5;

	private static final double FLOOR = 35.5;

	private static final double MIN_RADIUS = 5.0;

	public static final double RAMP_MIN = 0.15;

	public static final double RAMP_MAX = 0.25;

	private static final double PLATEAU_DIVISOR = 1.25;

	private static final double SHAPE_NOISE_WAVELENGTH = 128.0;

	private static final double RING_TOP_OFFSET = 1.5;

	private static final double MARSH_LEVEL = 62.9;

	private static final double MARSH_RELIEF = 1.2;

	private static final double MARSH_WAVELENGTH = 24.0;

	private static final double MARSH_FADE = 4.0;

	private static final double MARSH_OFFSET = 40009.0;

	public static final double FACE_WIDTH = 4.0;

	private static final double FACE_DROP = 2.0;

	private static final double FACE_SLOPE = FACE_DROP / FACE_WIDTH;

	private static final double SHORE_NOISE_WAVELENGTH = 24.0;
	private static final double SHORE_NOISE_AMPLITUDE = 2.0;
	private static final double SHORE_NOISE_FADE = 8.0;
	private static final double SHORE_NOISE_OFFSET = 30011.0;

	private static final double SHELF_NOISE_WAVELENGTH_1 = 48.0;
	private static final double SHELF_NOISE_HEIGHT_1 = 3.0;
	private static final double SHELF_NOISE_IN = 8.0;
	private static final double SHELF_NOISE_OUT = 32.0;
	private static final double SHELF_NOISE_OFFSET = 20011.0;


	private static final double MASK_RADIUS = 0.8;
	private static final double MASK_BIAS = 0.8;
	private static final double MASK_WAVELENGTH_1 = 1.3;
	private static final double MASK_WAVELENGTH_2 = 0.5;
	private static final double MASK_OCTAVE_2 = 0.5;
	private static final double MASK_OCTAVE_OFFSET = 10007.0;
	private static final double MASK_CLAMP = 1.0;
	private static final double MASK_CALIBRATION = 2.5;

	private static final double ANCHOR_HOLD = FACE_WIDTH + 24.0;

	private static final double ANCHOR_FADE = 96.0;

	private static final double MASK_GAIN_MIN = 0.5;
	private static final double MASK_GAIN_DEFAULT = 3.75;
	private static final double MASK_GAIN_MAX = 8.0;

	public double maskGain() {
		int lo = PreferredBiomeSource.MIN_ISLAND_NOISE;
		int mid = PreferredBiomeSource.DEFAULT_ISLAND_NOISE;
		int hi = PreferredBiomeSource.MAX_ISLAND_NOISE;
		if (this.noise <= mid) {
			return MASK_GAIN_MIN + (MASK_GAIN_DEFAULT - MASK_GAIN_MIN) * (this.noise - lo) / (double) (mid - lo);
		}
		return MASK_GAIN_DEFAULT + (MASK_GAIN_MAX - MASK_GAIN_DEFAULT) * (this.noise - mid) / (double) (hi - mid);
	}

	public double maskReach() {
		return MASK_RADIUS * Math.sqrt((maskGain() + MASK_BIAS) / (1.0 + MASK_BIAS));
	}

	private static final double NO_ISLAND = -1.0;

	public static final double NO_SHORE = -1000.0;

	public double maxRadius() {
		return this.size / 2.0;
	}

	public double cellSize() {
		return 2.0 * (maxRadius() * maskReach() + FACE_WIDTH + SHELF_MAX) + 4.0;
	}

	private double roll(double mx, double mz, int k) {
		double v = this.jitter.getValue(mx + 7.0 * k, 0.0, mz + 13.0 * k) * 4096.0 + 0.5;
		return v - Math.floor(v);
	}

	public double rollAt(int blockX, int blockZ, int k) {
		double g = cellSize();
		int cx = (int) Math.floor(blockX / g);
		int cz = (int) Math.floor(blockZ / g);
		return roll(cx * g + g / 2.0, cz * g + g / 2.0, k);
	}

	public record Shape(double centreX, double centreZ, double radius, double rampSlope,
			int hTop, double shelfWidth, boolean marsh, double anchorTemperature) {
	}

	public Shape shapeAt(int blockX, int blockZ) {
		double g = cellSize();
		return shapeOf((int) Math.floor(blockX / g), (int) Math.floor(blockZ / g), g);
	}

	private record ShapeKey(NormalNoise sampler, int size, float frequency, int noise,
			int cellX, int cellZ) {
	}

	private static final Shape NO_SHAPE = new Shape(0, 0, 0, 0, 0, 0, false, 0);

	private static final int SHAPE_CACHE_ENTRIES = 4096;

	private static final Map<ShapeKey, Shape> SHAPES = Collections.synchronizedMap(
			new LinkedHashMap<>(256, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<ShapeKey, Shape> eldest) {
					return size() > SHAPE_CACHE_ENTRIES;
				}
			});

	private Shape shapeOf(int cx, int cz, double g) {
		ShapeKey key = new ShapeKey(this.jitter.noise(), this.size, this.frequency, this.noise, cx, cz);
		Shape cached = SHAPES.get(key);
		if (cached == null) {
			Shape built = computeShape(cx, cz, g);
			cached = built == null ? NO_SHAPE : built;
			SHAPES.put(key, cached);
		}
		return cached == NO_SHAPE ? null : cached;
	}

	private Shape computeShape(int cx, int cz, double g) {
		double mx = cx * g + g / 2.0;
		double mz = cz * g + g / 2.0;
		if (roll(mx, mz, 0) >= this.frequency) {
			return null;
		}

		double u1 = roll(mx, mz, 1);
		double radius = MIN_RADIUS + (maxRadius() - MIN_RADIUS) * u1 * u1;

		double inset = radius * maskReach() + FACE_WIDTH + SHELF_MAX;
		double ox = cx * g + inset + roll(mx, mz, 3) * (g - 2.0 * inset);
		double oz = cz * g + inset + roll(mx, mz, 4) * (g - 2.0 * inset);

		double rampSlope = RAMP_MIN + roll(mx, mz, 7) * (RAMP_MAX - RAMP_MIN);

		double u2 = roll(mx, mz, 2);
		int hTop = Mth.clamp(1 + (int) Math.floor(u2 * u2 * 5.0), 1,
				Math.max(1, (int) Math.floor(radius * rampSlope / PLATEAU_DIVISOR)));

		double shelfWidth = SHELF_MIN + roll(mx, mz, 6) * (SHELF_MAX - SHELF_MIN);

		double anchor = vanillaTemperatureAt(ox, oz);
		boolean marsh = IslandBiomes.rollsMangrove(
				IslandBiomes.bandOf((float) anchor),
				(float) (radius / maxRadius()), (float) roll(mx, mz, 5));

		return new Shape(ox, oz, radius, rampSlope, hTop, shelfWidth, marsh, anchor);
	}

	private static final class Outline {
		final int originX;
		final int originZ;
		final int side;
		final float[] distance;
		final int coarseOriginX;
		final int coarseOriginZ;
		final int coarseSide;
		final float[] coarse;
		final BlockPos anchor;

		Outline(int originX, int originZ, int side, float[] distance,
				int coarseOriginX, int coarseOriginZ, int coarseSide, float[] coarse,
				BlockPos anchor) {
			this.originX = originX;
			this.originZ = originZ;
			this.side = side;
			this.distance = distance;
			this.coarseOriginX = coarseOriginX;
			this.coarseOriginZ = coarseOriginZ;
			this.coarseSide = coarseSide;
			this.coarse = coarse;
			this.anchor = anchor;
		}

		double at(int blockX, int blockZ) {
			int i = blockX - this.originX;
			int j = blockZ - this.originZ;
			if (i < 0 || j < 0 || i >= this.side || j >= this.side) {
				return Double.POSITIVE_INFINITY;
			}
			return this.distance[j * this.side + i];
		}

		double shelfAt(int blockX, int blockZ) {
			double u = (blockX - this.coarseOriginX) / (double) COARSE;
			double v = (blockZ - this.coarseOriginZ) / (double) COARSE;
			int i = (int) Math.floor(u);
			int j = (int) Math.floor(v);
			if (i < 0 || j < 0 || i + 1 >= this.coarseSide || j + 1 >= this.coarseSide) {
				return Double.POSITIVE_INFINITY;
			}
			double fu = u - i;
			double fv = v - j;
			double a = this.coarse[j * this.coarseSide + i];
			double b = this.coarse[j * this.coarseSide + i + 1];
			double c = this.coarse[(j + 1) * this.coarseSide + i];
			double d = this.coarse[(j + 1) * this.coarseSide + i + 1];
			return (a * (1 - fu) + b * fu) * (1 - fv) + (c * (1 - fu) + d * fu) * fv;
		}
	}

	private static final int COARSE = 4;

	private record OutlineKey(NormalNoise sampler, int size, int noise,
			int cellX, int cellZ) {
	}

	private static final int OUTLINE_CACHE_ENTRIES = 128;

	private static final Map<OutlineKey, Outline> OUTLINES = Collections.synchronizedMap(
			new LinkedHashMap<>(64, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<OutlineKey, Outline> eldest) {
					return size() > OUTLINE_CACHE_ENTRIES;
				}
			});

	public BlockPos anchorLand(int cx, int cz) {
		Shape shape = shapeOf(cx, cz, cellSize());
		return shape == null ? null : outlineOf(cx, cz, shape).anchor;
	}

	private Outline outlineOf(int cx, int cz, Shape shape) {
		OutlineKey key = new OutlineKey(this.shape.noise(), this.size, this.noise, cx, cz);
		Outline cached = OUTLINES.get(key);
		if (cached != null) {
			return cached;
		}
		Outline built = buildOutline(shape);
		OUTLINES.put(key, built);
		return built;
	}

	private boolean landAt(int x, int z, Shape shape, double maskRadius, double k1, double k2) {
		double n1 = Mth.clamp(MASK_CALIBRATION * this.shape.getValue(x * k1, 0.0, z * k1),
				-MASK_CLAMP, MASK_CLAMP);
		double n2 = Mth.clamp(MASK_CALIBRATION * this.shape.getValue(
				x * k2 + MASK_OCTAVE_OFFSET, 0.0, z * k2 + MASK_OCTAVE_OFFSET),
				-MASK_CLAMP, MASK_CLAMP);
		double n = (n1 + MASK_OCTAVE_2 * n2) / ((1.0 + MASK_OCTAVE_2) * MASK_CLAMP) * maskGain();
		double dx = x - shape.centreX();
		double dz = z - shape.centreZ();
		double r2 = (dx * dx + dz * dz) / (maskRadius * maskRadius);
		return n + MASK_BIAS > (1.0 + MASK_BIAS) * r2;
	}

	private double clampedNoise(int x, int z, double wavelength, double offset) {
		double k = SHAPE_NOISE_WAVELENGTH / wavelength;
		return Mth.clamp(this.shape.getValue(x * k + offset, 0.0, z * k + offset), -1.0, 1.0);
	}

	private double looseShoreDistance(double s, int x, int z) {
		if (s <= 0.0 || s == Double.POSITIVE_INFINITY) {
			return s;
		}
		double w = Math.min(s / SHORE_NOISE_FADE, 1.0);
		return s + w * SHORE_NOISE_AMPLITUDE * clampedNoise(x, z, SHORE_NOISE_WAVELENGTH, SHORE_NOISE_OFFSET);
	}

	public double shoreNoiseAt(int x, int z) {
		return SHORE_NOISE_AMPLITUDE
				* clampedNoise(x, z, SHORE_NOISE_WAVELENGTH, SHORE_NOISE_OFFSET);
	}

	public double shelfNoiseAt(int x, int z) {
		return SHELF_NOISE_HEIGHT_1 * clampedNoise(x, z, SHELF_NOISE_WAVELENGTH_1, SHELF_NOISE_OFFSET);
	}

	private static final double DT_INF = 1e20;

	private static double toDistance(double squared) {
		return squared >= DT_INF ? Double.POSITIVE_INFINITY : Math.sqrt(squared) - 0.5;
	}

	private static void edt1d(double[] f, double[] d, int n, int[] v, double[] z) {
		int k = 0;
		v[0] = 0;
		z[0] = Double.NEGATIVE_INFINITY;
		z[1] = Double.POSITIVE_INFINITY;
		for (int q = 1; q < n; q++) {
			double s;
			while (true) {
				int p = v[k];
				s = ((f[q] + (double) q * q) - (f[p] + (double) p * p)) / (2.0 * q - 2.0 * p);
				if (s > z[k]) {
					break;
				}
				k--;
			}
			k++;
			v[k] = q;
			z[k] = s;
			z[k + 1] = Double.POSITIVE_INFINITY;
		}
		k = 0;
		for (int q = 0; q < n; q++) {
			while (z[k + 1] < q) {
				k++;
			}
			int p = v[k];
			d[q] = (double) (q - p) * (q - p) + f[p];
		}
	}

	private static float[] distanceTransform(boolean[] seed, int side) {
		double[] g = new double[side * side];
		for (int i = 0; i < g.length; i++) {
			g[i] = seed[i] ? 0.0 : DT_INF;
		}
		double[] f = new double[side];
		double[] d = new double[side];
		int[] v = new int[side];
		double[] z = new double[side + 1];
		for (int x = 0; x < side; x++) {
			for (int y = 0; y < side; y++) {
				f[y] = g[y * side + x];
			}
			edt1d(f, d, side, v, z);
			for (int y = 0; y < side; y++) {
				g[y * side + x] = d[y];
			}
		}
		float[] out = new float[side * side];
		for (int y = 0; y < side; y++) {
			System.arraycopy(g, y * side, f, 0, side);
			edt1d(f, d, side, v, z);
			for (int x = 0; x < side; x++) {
				out[y * side + x] = (float) d[x];
			}
		}
		return out;
	}

	private Outline buildOutline(Shape shape) {
		double maskRadius = MASK_RADIUS * shape.radius();
		int half = (int) Math.ceil(shape.radius() * maskReach() + FACE_WIDTH) + 1;
		int side = 2 * half + 1;
		int originX = (int) Math.round(shape.centreX()) - half;
		int originZ = (int) Math.round(shape.centreZ()) - half;

		double k1 = SHAPE_NOISE_WAVELENGTH / (MASK_WAVELENGTH_1 * maskRadius);
		double k2 = SHAPE_NOISE_WAVELENGTH / (MASK_WAVELENGTH_2 * maskRadius);
		boolean[] land = new boolean[side * side];
		for (int j = 0; j < side; j++) {
			for (int i = 0; i < side; i++) {
				land[j * side + i] = landAt(originX + i, originZ + j, shape, maskRadius, k1, k2);
			}
		}

		int coarseHalf = (int) Math.ceil((shape.radius() * maskReach() + FACE_WIDTH + SHELF_MAX) / COARSE) + 2;
		int coarseSide = 2 * coarseHalf + 1;
		int coarseOriginX = (int) Math.round(shape.centreX()) - coarseHalf * COARSE;
		int coarseOriginZ = (int) Math.round(shape.centreZ()) - coarseHalf * COARSE;

		boolean[] landCoast = new boolean[side * side];
		boolean[] seaCoast = new boolean[side * side];
		boolean[] coarseSeed = new boolean[coarseSide * coarseSide];
		for (int j = 0; j < side; j++) {
			for (int i = 0; i < side; i++) {
				boolean here = land[j * side + i];
				boolean edge = i == 0 || j == 0 || i == side - 1 || j == side - 1;
				boolean touchesOther = edge && here;
				if (!touchesOther) {
					touchesOther = (i > 0 && land[j * side + i - 1] != here)
							|| (i < side - 1 && land[j * side + i + 1] != here)
							|| (j > 0 && land[(j - 1) * side + i] != here)
							|| (j < side - 1 && land[(j + 1) * side + i] != here);
				}
				if (!touchesOther) {
					continue;
				}
				if (here) {
					landCoast[j * side + i] = true;
					int ci = Math.floorDiv(originX + i - coarseOriginX, COARSE);
					int cj = Math.floorDiv(originZ + j - coarseOriginZ, COARSE);
					if (ci >= 0 && cj >= 0 && ci < coarseSide && cj < coarseSide) {
						coarseSeed[cj * coarseSide + ci] = true;
					}
				} else {
					seaCoast[j * side + i] = true;
				}
			}
		}

		float[] dLand = distanceTransform(landCoast, side);
		float[] dSea = distanceTransform(seaCoast, side);
		float[] distance = new float[side * side];
		for (int i = 0; i < side * side; i++) {
			boolean here = land[i];
			double d = toDistance(here ? dSea[i] : dLand[i]);
			distance[i] = (float) (here ? -d : d);
		}

		float[] coarseD2 = distanceTransform(coarseSeed, coarseSide);
		float[] coarse = new float[coarseSide * coarseSide];
		for (int j = 0; j < coarseSide; j++) {
			for (int i = 0; i < coarseSide; i++) {
				int fx = (coarseOriginX + i * COARSE) - originX;
				int fz = (coarseOriginZ + j * COARSE) - originZ;
				if (fx >= 0 && fz >= 0 && fx < side && fz < side) {
					coarse[j * coarseSide + i] = (float) toDistance(dLand[fz * side + fx]);
				} else {
					double d2 = coarseD2[j * coarseSide + i];
					coarse[j * coarseSide + i] = d2 >= DT_INF
							? Float.POSITIVE_INFINITY
							: (float) (Math.sqrt(d2) * COARSE - 0.5);
				}
			}
		}

		int centreX = (int) Math.round(shape.centreX());
		int centreZ = (int) Math.round(shape.centreZ());
		BlockPos anchor = null;
		double bestD2 = Double.POSITIVE_INFINITY;
		for (int j = 0; j < side; j++) {
			for (int i = 0; i < side; i++) {
				if (distance[j * side + i] > -2.0f) {
					continue;
				}
				int x = originX + i;
				int z = originZ + j;
				double d2 = (x - centreX) * (double) (x - centreX) + (z - centreZ) * (double) (z - centreZ);
				if (d2 < bestD2) {
					bestD2 = d2;
					anchor = new BlockPos(x, 0, z);
				}
			}
		}
		return new Outline(originX, originZ, side, distance,
				coarseOriginX, coarseOriginZ, coarseSide, coarse, anchor);
	}

	private double vanillaTemperatureAt(double x, double z) {
		double sx = this.offset.getValue(x * 0.25, 0.0, z * 0.25) * 4.0;
		double sz = this.offset.getValue(z * 0.25, x * 0.25, 0.0) * 4.0;
		return this.temperature.getValue(x * 0.25 + sx, 0.0, z * 0.25 + sz);
	}

	@Override
	public double compute(DensityFunction.FunctionContext pos) {
		double g = cellSize();
		int cx = (int) Math.floor(pos.blockX() / g);
		int cz = (int) Math.floor(pos.blockZ() / g);
		double mx = cx * g + g / 2.0;
		double mz = cz * g + g / 2.0;

		Shape shape = shapeOf(cx, cz, g);
		if (shape == null) {
			return switch (this.channel) {
				case HEIGHT -> NO_ISLAND;
				case SHORE, SHORE_RING, ANCHOR_TEMPERATURE -> NO_SHORE;
				case SIZE, ROLL, RADIUS -> 0.0;
			};
		}

		if (this.channel == Channel.SIZE) {
			return shape.radius() / maxRadius();
		}
		if (this.channel == Channel.RADIUS) {
			return shape.radius();
		}
		if (this.channel == Channel.ANCHOR_TEMPERATURE) {
			double anchor = shape.anchorTemperature();
			double dx = pos.blockX() - shape.centreX();
			double dz = pos.blockZ() - shape.centreZ();
			double f = Math.hypot(dx, dz) - shape.radius() * maskReach();
			double w = Mth.clamp((f - ANCHOR_HOLD) / ANCHOR_FADE, 0.0, 1.0);
			if (w <= 0.0) {
				return anchor;
			}
			return anchor + (vanillaTemperatureAt(pos.blockX(), pos.blockZ()) - anchor) * w;
		}
		if (this.channel == Channel.ROLL) {
			return roll(mx, mz, 5);
		}

		Outline outline = outlineOf(cx, cz, shape);
		double raw = outline.at(pos.blockX(), pos.blockZ());
		double s = looseShoreDistance(raw, pos.blockX(), pos.blockZ());

		if (this.channel == Channel.SHORE) {
			return s == Double.POSITIVE_INFINITY ? NO_SHORE : -s;
		}
		if (this.channel == Channel.SHORE_RING) {
			return s == Double.POSITIVE_INFINITY ? NO_SHORE : -s + RING_TOP_OFFSET / FACE_SLOPE;
		}

		double d = outline.shelfAt(pos.blockX(), pos.blockZ());
		if (d > FACE_WIDTH + shape.shelfWidth()) {
			return NO_ISLAND;
		}

		double y;
		if (s <= 0.0) {
			if (shape.marsh()) {
				double relief = MARSH_RELIEF * clampedNoise(pos.blockX(), pos.blockZ(), MARSH_WAVELENGTH, MARSH_OFFSET);
				double inland = Math.min(-s / MARSH_FADE, 1.0);
				y = MARSH_LEVEL + inland * relief;
			} else {
				y = WATERLINE + Math.min(shape.hTop(), -s * shape.rampSlope());
			}
		} else {
			y = WATERLINE - FACE_SLOPE * Math.min(s, FACE_WIDTH);
		}
		if (raw >= FACE_WIDTH && d > FACE_WIDTH) {
			double in = Mth.clamp((d - FACE_WIDTH) / SHELF_NOISE_IN, 0.0, 1.0);
			double out = Mth.clamp(
					(FACE_WIDTH + shape.shelfWidth() - d) / SHELF_NOISE_OUT, 0.0, 1.0);
			double shelf = (WATERLINE - FACE_DROP)
					- (WATERLINE - FACE_DROP - FLOOR) * ((d - FACE_WIDTH) / shape.shelfWidth())
					+ in * out * shelfNoiseAt(pos.blockX(), pos.blockZ());
			y = Math.min(y, shelf);
		}
		return (y - 128.0) / 128.0;
	}

	@Override
	public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
		applier.fillAllDirectly(densities, this);
	}

	@Override
	public DensityFunction mapAll(DensityFunction.Visitor visitor) {
		return visitor.apply(new IslandField(this.size, this.frequency, this.noise, this.channel,
				visitor.visitNoise(this.jitter), visitor.visitNoise(this.shape),
				visitor.visitNoise(this.offset), visitor.visitNoise(this.temperature)));
	}

	@Override
	public double minValue() {
		return switch (this.channel) {
			case HEIGHT -> NO_ISLAND;
			case SIZE -> 0.0;
			case ROLL -> -1.0;
			case SHORE, SHORE_RING, ANCHOR_TEMPERATURE -> NO_SHORE;
			case RADIUS -> 0.0;
		};
	}

	@Override
	public double maxValue() {
		return switch (this.channel) {
			case HEIGHT -> -0.45;
			case SIZE -> 1.0;
			case ROLL -> 1.0;
			case SHORE -> maxRadius() * maskReach() + FACE_WIDTH + 1.0;
			case RADIUS -> maxRadius();
			case SHORE_RING -> maxRadius() * maskReach() + FACE_WIDTH + 1.0
					+ RING_TOP_OFFSET / FACE_SLOPE;
			case ANCHOR_TEMPERATURE -> 2.0;
		};
	}

	@Override
	public KeyDispatchDataCodec<? extends DensityFunction> codec() {
		return CODEC_HOLDER;
	}
}
