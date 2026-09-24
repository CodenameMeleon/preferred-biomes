package net.codenamemeleon.preferredbiomes.worldgen;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.biome.source.util.VanillaTerrainParametersCreator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseParametersKeys;
import net.minecraft.world.gen.noise.NoiseRouter;

public final class IslandTerrain {

	private IslandTerrain() {
	}

	private static final Identifier Y = new Identifier("y");
	private static final Identifier CONTINENTS = new Identifier("overworld/continents");
	private static final Identifier EROSION = new Identifier("overworld/erosion");
	private static final Identifier RIDGES_FOLDED = new Identifier("overworld/ridges_folded");
	private static final Identifier BASE_3D_NOISE = new Identifier("overworld/base_3d_noise");
	private static final Identifier CAVES_ENTRANCES = new Identifier("overworld/caves/entrances");
	private static final Identifier CAVES_NOODLE = new Identifier("overworld/caves/noodle");
	private static final Identifier CAVES_PILLARS = new Identifier("overworld/caves/pillars");
	private static final Identifier CAVES_SPAGHETTI_2D = new Identifier("overworld/caves/spaghetti_2d");
	private static final Identifier CAVES_SPAGHETTI_ROUGHNESS =
			new Identifier("overworld/caves/spaghetti_roughness_function");

	public static final Identifier ISLAND_JITTER = new Identifier("preferred-biomes", "island_jitter");
	public static final Identifier ISLAND_SHAPE = new Identifier("preferred-biomes", "island_shape");


	private static final double OCEAN_CONTINENTS = -0.2;

	private static final double OCEAN_CONTINENTS_MIN = -1.0;

	private static final int OCEAN_CEILING_BOTTOM = 58;

	private static final int OCEAN_CEILING_TOP = 62;

	private static final double OCEAN_FLOOR_DROP = -8.0;

	private static final double OCEAN_NOISE_SCALE = 0.85;

	private static final int IDW_CEILING_BOTTOM = 44;

	private static final int IDW_CEILING_TOP = 48;

	private static final double IDW_CEILING_VALUE = 0.3;

	private static final double C_SPAN = 28.0;

	private static final double MASK_BLOCKS = 2.0;

	private static final double CARVE_CEILING = 50.0;

	public static ChunkGeneratorSettings createSettings(DynamicRegistryManager registryManager,
			int size, float frequency, int noise) {
		RegistryEntryLookup<DensityFunction> functions =
				registryManager.getWrapperOrThrow(RegistryKeys.DENSITY_FUNCTION);
		RegistryEntryLookup<DoublePerlinNoiseSampler.NoiseParameters> noises =
				registryManager.getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS);

		ChunkGeneratorSettings vanilla = registryManager.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
				.entryOf(ChunkGeneratorSettings.OVERWORLD).value();

		return new ChunkGeneratorSettings(
				vanilla.generationShapeConfig(),
				vanilla.defaultBlock(),
				vanilla.defaultFluid(),
				createRouter(vanilla.noiseRouter(), functions, noises, size, frequency, noise),
				IslandSurface.rule(vanilla.surfaceRule(), size, frequency, noise),
				vanilla.spawnTarget(),
				vanilla.seaLevel(),
				vanilla.mobGenerationDisabled(),
				vanilla.hasAquifers(),
				vanilla.oreVeins(),
				vanilla.usesLegacyRandom());
	}

	private static NoiseRouter createRouter(NoiseRouter vanilla,
			RegistryEntryLookup<DensityFunction> functions,
			RegistryEntryLookup<DoublePerlinNoiseSampler.NoiseParameters> noises,
			int size, float frequency, int noise) {

		DensityFunction continents = DensityFunctionTypes.min(
				DensityFunctionTypes.max(vanilla.continents(),
						DensityFunctionTypes.constant(OCEAN_CONTINENTS_MIN)),
				DensityFunctionTypes.constant(OCEAN_CONTINENTS));

		DensityFunction erosion = vanilla.erosion();
		DensityFunction ridges = vanilla.ridges();
		DensityFunction ridgesFolded = holder(functions, RIDGES_FOLDED);

		DensityFunctionTypes.Spline.DensityFunctionWrapper wContinents = wrap(continents);
		DensityFunctionTypes.Spline.DensityFunctionWrapper wErosion = wrap(erosion);
		DensityFunctionTypes.Spline.DensityFunctionWrapper wRidges = wrap(ridges);
		DensityFunctionTypes.Spline.DensityFunctionWrapper wRidgesFolded = wrap(ridgesFolded);

		DensityFunction islandHeight = DensityFunctionTypes.flatCache(DensityFunctionTypes.cache2d(
				new IslandField(size, frequency, noise, IslandField.Channel.HEIGHT,
						new DensityFunction.Noise(noise(noises, ISLAND_JITTER)),
						new DensityFunction.Noise(noise(noises, ISLAND_SHAPE)),
						new DensityFunction.Noise(noise(noises, NoiseParametersKeys.OFFSET)),
						new DensityFunction.Noise(noise(noises, NoiseParametersKeys.TEMPERATURE)))));

		DensityFunction vanillaOffset = applyBlending(
				offsetExpression(wContinents, wErosion, wRidgesFolded),
				DensityFunctionTypes.blendOffset());

		DensityFunction oceanOffset = DensityFunctionTypes.add(vanillaOffset,
				DensityFunctionTypes.constant(OCEAN_FLOOR_DROP / 128.0));
		DensityFunction offset = DensityFunctionTypes.flatCache(DensityFunctionTypes.cache2d(
				DensityFunctionTypes.max(oceanOffset, islandHeight)));

		DensityFunction mask = DensityFunctionTypes.flatCache(DensityFunctionTypes.cache2d(
				maskExpression(islandHeight, vanillaOffset)));

		DensityFunction factor = applyBlending(
				DensityFunctionTypes.spline(VanillaTerrainParametersCreator.createFactorSpline(
						wContinents, wErosion, wRidges, wRidgesFolded, false)),
				DensityFunctionTypes.constant(10.0));
		DensityFunction jaggedness = applyBlending(
				DensityFunctionTypes.spline(VanillaTerrainParametersCreator.createJaggednessSpline(
						wContinents, wErosion, wRidges, wRidgesFolded, false)),
				DensityFunctionTypes.zero());
		DensityFunction depth = DensityFunctionTypes.add(
				DensityFunctionTypes.yClampedGradient(-64, 320, 1.5, -1.5), offset);

		DensityFunction jaggedNoise = DensityFunctionTypes.noise(
				noise(noises, NoiseParametersKeys.JAGGED), 1500.0, 0.0);
		DensityFunction depthPlusJag = DensityFunctionTypes.add(depth,
				DensityFunctionTypes.mul(jaggedness, jaggedNoise.halfNegative()));
		DensityFunction oneMinusMask = DensityFunctionTypes.add(DensityFunctionTypes.constant(1.0),
				DensityFunctionTypes.mul(mask, DensityFunctionTypes.constant(-1.0)));

		DensityFunction vanillaTerm = createInitialDensityFunction(factor, depthPlusJag);
		DensityFunction symmetric = DensityFunctionTypes.mul(DensityFunctionTypes.constant(4.0),
				DensityFunctionTypes.mul(depthPlusJag, factor));
		DensityFunction gradientTerm = DensityFunctionTypes.add(
				DensityFunctionTypes.mul(vanillaTerm, oneMinusMask),
				DensityFunctionTypes.mul(symmetric, mask));

		DensityFunction slopedCheese = DensityFunctionTypes.add(gradientTerm,
				DensityFunctionTypes.mul(holder(functions, BASE_3D_NOISE),
						DensityFunctionTypes.mul(oneMinusMask,
								DensityFunctionTypes.constant(OCEAN_NOISE_SCALE))));

		DensityFunction yFn = holder(functions, Y);
		DensityFunction entrancesFn = holder(functions, CAVES_ENTRANCES);
		DensityFunction scaledEntrances = DensityFunctionTypes.mul(
				DensityFunctionTypes.constant(5.0), entrancesFn);
		DensityFunction vanillaEntrances = DensityFunctionTypes.min(slopedCheese, scaledEntrances);
		DensityFunction carveDeep = DensityFunctionTypes.rangeChoice(yFn, -1000000.0, CARVE_CEILING,
				scaledEntrances, DensityFunctionTypes.constant(1000000.0));
		DensityFunction islandEntrances = DensityFunctionTypes.min(slopedCheese, carveDeep);
		DensityFunction entrances = DensityFunctionTypes.add(
				DensityFunctionTypes.mul(vanillaEntrances, oneMinusMask),
				DensityFunctionTypes.mul(islandEntrances, mask));

		DensityFunction noodleFn = holder(functions, CAVES_NOODLE);
		DensityFunction noodleDeep = DensityFunctionTypes.rangeChoice(yFn, -1000000.0, CARVE_CEILING,
				noodleFn, DensityFunctionTypes.constant(1000000.0));
		DensityFunction noodleTerm = DensityFunctionTypes.add(
				DensityFunctionTypes.mul(noodleFn, oneMinusMask),
				DensityFunctionTypes.mul(noodleDeep, mask));

		DensityFunction withCaves = DensityFunctionTypes.rangeChoice(slopedCheese, -1000000.0, 1.5625,
				entrances, createCavesFunction(functions, noises, slopedCheese));
		DensityFunction builtDensity = DensityFunctionTypes.min(
				applyBlendDensity(applySurfaceSlides(withCaves)), noodleTerm);

		DensityFunction ceiling = DensityFunctionTypes.yClampedGradient(
				OCEAN_CEILING_BOTTOM, OCEAN_CEILING_TOP, 1000.0, -1000.0);
		DensityFunction oceanCapped = DensityFunctionTypes.min(builtDensity, ceiling);
		DensityFunction finalDensity = DensityFunctionTypes.add(
				DensityFunctionTypes.mul(builtDensity, mask),
				DensityFunctionTypes.mul(oceanCapped, oneMinusMask));

		DensityFunction initialAsBuilt = applySurfaceSlides(
				DensityFunctionTypes.add(
						createInitialDensityFunction(DensityFunctionTypes.cache2d(factor), depth),
						DensityFunctionTypes.constant(-0.703125)).clamp(-64.0, 64.0));
		DensityFunction idwCeiling = DensityFunctionTypes.yClampedGradient(
				IDW_CEILING_BOTTOM, IDW_CEILING_TOP, 1000.0, IDW_CEILING_VALUE);
		DensityFunction idwCapped = DensityFunctionTypes.min(initialAsBuilt, idwCeiling);
		DensityFunction initialDensityWithoutJaggedness = DensityFunctionTypes.add(
				DensityFunctionTypes.mul(initialAsBuilt, mask),
				DensityFunctionTypes.mul(idwCapped, oneMinusMask));

		DensityFunction climateC = DensityFunctionTypes.add(
				DensityFunctionTypes.mul(offset, DensityFunctionTypes.constant(128.0 / C_SPAN)),
				DensityFunctionTypes.constant((128.0 - 63.0) / C_SPAN)).clamp(-1.0, 1.0);
		DensityFunction climateS = DensityFunctionTypes.flatCache(DensityFunctionTypes.cache2d(
				new IslandField(size, frequency, noise, IslandField.Channel.SIZE,
						new DensityFunction.Noise(noise(noises, ISLAND_JITTER)),
						new DensityFunction.Noise(noise(noises, ISLAND_SHAPE)),
						new DensityFunction.Noise(noise(noises, NoiseParametersKeys.OFFSET)),
						new DensityFunction.Noise(noise(noises, NoiseParametersKeys.TEMPERATURE)))));
		DensityFunction anchorTemperature = new IslandField(size, frequency, noise,
				IslandField.Channel.ANCHOR_TEMPERATURE,
				new DensityFunction.Noise(noise(noises, ISLAND_JITTER)),
				new DensityFunction.Noise(noise(noises, ISLAND_SHAPE)),
				new DensityFunction.Noise(noise(noises, NoiseParametersKeys.OFFSET)),
				new DensityFunction.Noise(noise(noises, NoiseParametersKeys.TEMPERATURE)));
		DensityFunction temperature = DensityFunctionTypes.flatCache(DensityFunctionTypes.cache2d(
				DensityFunctionTypes.rangeChoice(anchorTemperature, -2.0, 2.0,
						anchorTemperature, vanilla.temperature())));

		DensityFunction climateW = DensityFunctionTypes.flatCache(DensityFunctionTypes.cache2d(
				new IslandField(size, frequency, noise, IslandField.Channel.ROLL,
						new DensityFunction.Noise(noise(noises, ISLAND_JITTER)),
						new DensityFunction.Noise(noise(noises, ISLAND_SHAPE)),
						new DensityFunction.Noise(noise(noises, NoiseParametersKeys.OFFSET)),
						new DensityFunction.Noise(noise(noises, NoiseParametersKeys.TEMPERATURE)))));

		return new NoiseRouter(
				vanilla.barrierNoise(),
				vanilla.fluidLevelFloodednessNoise(),
				vanilla.fluidLevelSpreadNoise(),
				vanilla.lavaNoise(),
				temperature,
				vanilla.vegetation(),
				climateC,
				climateS,
				depth,
				climateW,
				initialDensityWithoutJaggedness,
				finalDensity,
				vanilla.veinToggle(),
				vanilla.veinRidged(),
				vanilla.veinGap());
	}


	private static DensityFunction offsetExpression(
			DensityFunctionTypes.Spline.DensityFunctionWrapper continents,
			DensityFunctionTypes.Spline.DensityFunctionWrapper erosion,
			DensityFunctionTypes.Spline.DensityFunctionWrapper ridgesFolded) {
		return DensityFunctionTypes.add(DensityFunctionTypes.constant(-0.50375F),
				DensityFunctionTypes.spline(VanillaTerrainParametersCreator.createOffsetSpline(
						continents, erosion, ridgesFolded, false)));
	}

	private static DensityFunction maskExpression(DensityFunction islandHeight,
			DensityFunction vanillaOffset) {
		return DensityFunctionTypes.mul(
				DensityFunctionTypes.add(islandHeight,
						DensityFunctionTypes.mul(vanillaOffset, DensityFunctionTypes.constant(-1.0))),
				DensityFunctionTypes.constant(128.0 / MASK_BLOCKS))
				.clamp(0.0, 1.0);
	}


	public static IslandField seededField(DynamicRegistryManager registryManager,
			NoiseConfig noiseConfig, int size, float frequency, int noise,
			IslandField.Channel channel) {
		return new IslandField(size, frequency, noise, channel,
				seededNoise(registryManager, noiseConfig, ISLAND_JITTER),
				seededNoise(registryManager, noiseConfig, ISLAND_SHAPE),
				seededNoise(registryManager, noiseConfig, NoiseParametersKeys.OFFSET.getValue()),
				seededNoise(registryManager, noiseConfig, NoiseParametersKeys.TEMPERATURE.getValue()));
	}

	private static DensityFunction.Noise seededNoise(DynamicRegistryManager registryManager,
			NoiseConfig noiseConfig, Identifier id) {
		RegistryKey<DoublePerlinNoiseSampler.NoiseParameters> key =
				RegistryKey.of(RegistryKeys.NOISE_PARAMETERS, id);
		return new DensityFunction.Noise(
				registryManager.get(RegistryKeys.NOISE_PARAMETERS).entryOf(key),
				noiseConfig.getOrCreateSampler(key));
	}

	public static DensityFunction seededVanillaOffset(DynamicRegistryManager registryManager,
			NoiseConfig noiseConfig) {
		RegistryEntryLookup<DensityFunction> functions =
				registryManager.getWrapperOrThrow(RegistryKeys.DENSITY_FUNCTION);
		DensityFunction continents = DensityFunctionTypes.min(
				DensityFunctionTypes.max(holder(functions, CONTINENTS),
						DensityFunctionTypes.constant(OCEAN_CONTINENTS_MIN)),
				DensityFunctionTypes.constant(OCEAN_CONTINENTS));
		DensityFunction expression = offsetExpression(
				wrap(continents), wrap(holder(functions, EROSION)), wrap(holder(functions, RIDGES_FOLDED)));
		return expression.apply(seedVisitor(noiseConfig));
	}

	public static DensityFunction seededMask(DynamicRegistryManager registryManager,
			NoiseConfig noiseConfig, int size, float frequency, int noise) {
		return maskExpression(
				seededField(registryManager, noiseConfig, size, frequency, noise,
						IslandField.Channel.HEIGHT),
				seededVanillaOffset(registryManager, noiseConfig));
	}

	private static DensityFunction.DensityFunctionVisitor seedVisitor(NoiseConfig noiseConfig) {
		return new DensityFunction.DensityFunctionVisitor() {
			@Override
			public DensityFunction.Noise apply(DensityFunction.Noise noise) {
				return new DensityFunction.Noise(noise.noiseData(),
						noiseConfig.getOrCreateSampler(noise.noiseData().getKey().orElseThrow()));
			}

			@Override
			public DensityFunction apply(DensityFunction function) {
				return function;
			}
		};
	}


	private static DensityFunction holder(RegistryEntryLookup<DensityFunction> functions, Identifier id) {
		return new DensityFunctionTypes.RegistryEntryHolder(
				functions.getOrThrow(RegistryKey.of(RegistryKeys.DENSITY_FUNCTION, id)));
	}

	private static DensityFunctionTypes.Spline.DensityFunctionWrapper wrap(DensityFunction function) {
		RegistryEntry<DensityFunction> entry =
				function instanceof DensityFunctionTypes.RegistryEntryHolder holder
						? holder.function()
						: new RegistryEntry.Direct<>(function);
		return new DensityFunctionTypes.Spline.DensityFunctionWrapper(entry);
	}

	private static RegistryEntry<DoublePerlinNoiseSampler.NoiseParameters> noise(
			RegistryEntryLookup<DoublePerlinNoiseSampler.NoiseParameters> noises,
			RegistryKey<DoublePerlinNoiseSampler.NoiseParameters> key) {
		return noises.getOrThrow(key);
	}

	private static RegistryEntry<DoublePerlinNoiseSampler.NoiseParameters> noise(
			RegistryEntryLookup<DoublePerlinNoiseSampler.NoiseParameters> noises, Identifier id) {
		return noises.getOrThrow(RegistryKey.of(RegistryKeys.NOISE_PARAMETERS, id));
	}


	private static DensityFunction applyBlending(DensityFunction function, DensityFunction blendOffset) {
		DensityFunction densityFunction = DensityFunctionTypes.lerp(
				DensityFunctionTypes.blendAlpha(), blendOffset, function);
		return DensityFunctionTypes.flatCache(DensityFunctionTypes.cache2d(densityFunction));
	}

	private static DensityFunction createInitialDensityFunction(DensityFunction factor, DensityFunction depth) {
		DensityFunction densityFunction = DensityFunctionTypes.mul(depth, factor);
		return DensityFunctionTypes.mul(DensityFunctionTypes.constant(4.0), densityFunction.quarterNegative());
	}

	private static DensityFunction applyBlendDensity(DensityFunction density) {
		DensityFunction densityFunction = DensityFunctionTypes.blendDensity(density);
		return DensityFunctionTypes.mul(DensityFunctionTypes.interpolated(densityFunction),
				DensityFunctionTypes.constant(0.64)).squeeze();
	}

	private static DensityFunction applySurfaceSlides(DensityFunction density) {
		return applySlides(density, -64, 384, 80, 64, -0.078125, 0, 24, 0.1171875);
	}

	private static DensityFunction applySlides(DensityFunction density, int minY, int maxY,
			int topRelativeMinY, int topRelativeMaxY, double topDensity,
			int bottomRelativeMinY, int bottomRelativeMaxY, double bottomDensity) {
		DensityFunction densityFunction = density;
		DensityFunction densityFunction2 = DensityFunctionTypes.yClampedGradient(
				minY + maxY - topRelativeMinY, minY + maxY - topRelativeMaxY, 1.0, 0.0);
		densityFunction = DensityFunctionTypes.lerp(densityFunction2, topDensity, densityFunction);
		DensityFunction densityFunction3 = DensityFunctionTypes.yClampedGradient(
				minY + bottomRelativeMinY, minY + bottomRelativeMaxY, 0.0, 1.0);
		return DensityFunctionTypes.lerp(densityFunction3, bottomDensity, densityFunction);
	}

	private static DensityFunction createCavesFunction(RegistryEntryLookup<DensityFunction> functions,
			RegistryEntryLookup<DoublePerlinNoiseSampler.NoiseParameters> noises,
			DensityFunction slopedCheese) {
		DensityFunction densityFunction = holder(functions, CAVES_SPAGHETTI_2D);
		DensityFunction densityFunction2 = holder(functions, CAVES_SPAGHETTI_ROUGHNESS);
		DensityFunction densityFunction3 = DensityFunctionTypes.noise(
				noise(noises, NoiseParametersKeys.CAVE_LAYER), 8.0);
		DensityFunction densityFunction4 = DensityFunctionTypes.mul(
				DensityFunctionTypes.constant(4.0), densityFunction3.square());
		DensityFunction densityFunction5 = DensityFunctionTypes.noise(
				noise(noises, NoiseParametersKeys.CAVE_CHEESE), 0.6666666666666666);
		DensityFunction densityFunction6 = DensityFunctionTypes.add(
				DensityFunctionTypes.add(DensityFunctionTypes.constant(0.27), densityFunction5).clamp(-1.0, 1.0),
				DensityFunctionTypes.add(DensityFunctionTypes.constant(1.5),
						DensityFunctionTypes.mul(DensityFunctionTypes.constant(-0.64), slopedCheese)).clamp(0.0, 0.5));
		DensityFunction densityFunction7 = DensityFunctionTypes.add(densityFunction4, densityFunction6);
		DensityFunction densityFunction8 = DensityFunctionTypes.min(
				DensityFunctionTypes.min(densityFunction7, holder(functions, CAVES_ENTRANCES)),
				DensityFunctionTypes.add(densityFunction, densityFunction2));
		DensityFunction densityFunction9 = holder(functions, CAVES_PILLARS);
		DensityFunction densityFunction10 = DensityFunctionTypes.rangeChoice(
				densityFunction9, -1000000.0, 0.03, DensityFunctionTypes.constant(-1000000.0), densityFunction9);
		return DensityFunctionTypes.max(densityFunction8, densityFunction10);
	}
}
