package net.codenamemeleon.preferredbiomes.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class IslandTerrain {

	private IslandTerrain() {
	}

	private static final Identifier Y = Identifier.parse("y");
	private static final Identifier CONTINENTS = Identifier.parse("overworld/continents");
	private static final Identifier EROSION = Identifier.parse("overworld/erosion");
	private static final Identifier RIDGES_FOLDED = Identifier.parse("overworld/ridges_folded");
	private static final Identifier BASE_3D_NOISE = Identifier.parse("overworld/base_3d_noise");
	private static final Identifier CAVES_ENTRANCES = Identifier.parse("overworld/caves/entrances");
	private static final Identifier CAVES_NOODLE = Identifier.parse("overworld/caves/noodle");
	private static final Identifier CAVES_PILLARS = Identifier.parse("overworld/caves/pillars");
	private static final Identifier CAVES_SPAGHETTI_2D = Identifier.parse("overworld/caves/spaghetti_2d");
	private static final Identifier CAVES_SPAGHETTI_ROUGHNESS =
			Identifier.parse("overworld/caves/spaghetti_roughness_function");

	public static final Identifier ISLAND_JITTER = Identifier.fromNamespaceAndPath("preferred-biomes", "island_jitter");
	public static final Identifier ISLAND_SHAPE = Identifier.fromNamespaceAndPath("preferred-biomes", "island_shape");


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

	public static NoiseGeneratorSettings createSettings(HolderLookup.Provider registryManager,
			int size, float frequency, int noise) {
		HolderGetter<DensityFunction> functions =
				registryManager.lookupOrThrow(Registries.DENSITY_FUNCTION);
		HolderGetter<NormalNoise.NoiseParameters> noises =
				registryManager.lookupOrThrow(Registries.NOISE);

		NoiseGeneratorSettings vanilla = registryManager.lookupOrThrow(Registries.NOISE_SETTINGS)
				.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();

		return new NoiseGeneratorSettings(
				vanilla.noiseSettings(),
				vanilla.defaultBlock(),
				vanilla.defaultFluid(),
				createRouter(vanilla.noiseRouter(), vanilla.noiseSettings(), functions, noises, size,
						frequency, noise),
				IslandSurface.rule(vanilla.surfaceRule(), size, frequency, noise),
				vanilla.spawnTarget(),
				vanilla.seaLevel(),
				vanilla.disableMobGeneration(),
				vanilla.isAquifersEnabled(),
				vanilla.oreVeinsEnabled(),
				vanilla.useLegacyRandomSource());
	}

	public static DensityFunction buildSlot(IslandRouterSlot.Slot slot, NoiseRouter vanilla,
			NoiseSettings noiseSettings, HolderGetter<DensityFunction> functions,
			HolderGetter<NormalNoise.NoiseParameters> noises, int size, float frequency, int noise) {
		NoiseRouter router = buildRouter(vanilla, noiseSettings, functions, noises, size, frequency, noise);
		return slot == IslandRouterSlot.Slot.FINAL_DENSITY
				? router.finalDensity()
				: router.preliminarySurfaceLevel();
	}

	private static NoiseRouter createRouter(NoiseRouter vanilla, NoiseSettings noiseSettings,
			HolderGetter<DensityFunction> functions,
			HolderGetter<NormalNoise.NoiseParameters> noises,
			int size, float frequency, int noise) {
		NoiseRouter router = buildRouter(vanilla, noiseSettings, functions, noises, size, frequency, noise);
		return new NoiseRouter(
				router.barrierNoise(),
				router.fluidLevelFloodednessNoise(),
				router.fluidLevelSpreadNoise(),
				router.lavaNoise(),
				router.temperature(),
				router.vegetation(),
				router.continents(),
				router.erosion(),
				router.depth(),
				router.ridges(),
				new IslandRouterSlot(size, frequency, noise, IslandRouterSlot.Slot.PRELIMINARY_SURFACE_LEVEL,
						router.preliminarySurfaceLevel()),
				new IslandRouterSlot(size, frequency, noise, IslandRouterSlot.Slot.FINAL_DENSITY,
						router.finalDensity()),
				router.veinToggle(),
				router.veinRidged(),
				router.veinGap());
	}

	private static NoiseRouter buildRouter(NoiseRouter vanilla, NoiseSettings noiseSettings,
			HolderGetter<DensityFunction> functions,
			HolderGetter<NormalNoise.NoiseParameters> noises,
			int size, float frequency, int noise) {

		DensityFunction continents = DensityFunctions.min(
				DensityFunctions.max(vanilla.continents(),
						DensityFunctions.constant(OCEAN_CONTINENTS_MIN)),
				DensityFunctions.constant(OCEAN_CONTINENTS));

		DensityFunction erosion = vanilla.erosion();
		DensityFunction ridges = vanilla.ridges();
		DensityFunction ridgesFolded = holder(functions, RIDGES_FOLDED);

		DensityFunctions.Spline.Coordinate wContinents = wrap(continents);
		DensityFunctions.Spline.Coordinate wErosion = wrap(erosion);
		DensityFunctions.Spline.Coordinate wRidges = wrap(ridges);
		DensityFunctions.Spline.Coordinate wRidgesFolded = wrap(ridgesFolded);

		DensityFunction islandHeight = DensityFunctions.flatCache(DensityFunctions.cache2d(
				new IslandField(size, frequency, noise, IslandField.Channel.HEIGHT,
						new DensityFunction.NoiseHolder(noise(noises, ISLAND_JITTER)),
						new DensityFunction.NoiseHolder(noise(noises, ISLAND_SHAPE)),
						new DensityFunction.NoiseHolder(noise(noises, Noises.SHIFT)),
						new DensityFunction.NoiseHolder(noise(noises, Noises.TEMPERATURE)))));

		DensityFunction vanillaOffset = applyBlending(
				offsetExpression(wContinents, wErosion, wRidgesFolded),
				DensityFunctions.blendOffset());

		DensityFunction oceanOffset = DensityFunctions.add(vanillaOffset,
				DensityFunctions.constant(OCEAN_FLOOR_DROP / 128.0));
		DensityFunction offset = DensityFunctions.flatCache(DensityFunctions.cache2d(
				DensityFunctions.max(oceanOffset, islandHeight)));

		DensityFunction mask = DensityFunctions.flatCache(DensityFunctions.cache2d(
				maskExpression(islandHeight, vanillaOffset)));

		DensityFunction factor = applyBlending(
				DensityFunctions.spline(TerrainProvider.overworldFactor(
						wContinents, wErosion, wRidges, wRidgesFolded, false)),
				DensityFunctions.constant(10.0));
		DensityFunction jaggedness = applyBlending(
				DensityFunctions.spline(TerrainProvider.overworldJaggedness(
						wContinents, wErosion, wRidges, wRidgesFolded, false)),
				DensityFunctions.zero());
		DensityFunction depth = DensityFunctions.add(
				DensityFunctions.yClampedGradient(-64, 320, 1.5, -1.5), offset);

		DensityFunction jaggedNoise = DensityFunctions.noise(
				noise(noises, Noises.JAGGED), 1500.0, 0.0);
		DensityFunction depthPlusJag = DensityFunctions.add(depth,
				DensityFunctions.mul(jaggedness, jaggedNoise.halfNegative()));
		DensityFunction oneMinusMask = DensityFunctions.add(DensityFunctions.constant(1.0),
				DensityFunctions.mul(mask, DensityFunctions.constant(-1.0)));

		DensityFunction vanillaTerm = createInitialDensityFunction(factor, depthPlusJag);
		DensityFunction symmetric = DensityFunctions.mul(DensityFunctions.constant(4.0),
				DensityFunctions.mul(depthPlusJag, factor));
		DensityFunction gradientTerm = DensityFunctions.add(
				DensityFunctions.mul(vanillaTerm, oneMinusMask),
				DensityFunctions.mul(symmetric, mask));

		DensityFunction slopedCheese = DensityFunctions.add(gradientTerm,
				DensityFunctions.mul(holder(functions, BASE_3D_NOISE),
						DensityFunctions.mul(oneMinusMask,
								DensityFunctions.constant(OCEAN_NOISE_SCALE))));

		DensityFunction yFn = holder(functions, Y);
		DensityFunction entrancesFn = holder(functions, CAVES_ENTRANCES);
		DensityFunction scaledEntrances = DensityFunctions.mul(
				DensityFunctions.constant(5.0), entrancesFn);
		DensityFunction vanillaEntrances = DensityFunctions.min(slopedCheese, scaledEntrances);
		DensityFunction carveDeep = DensityFunctions.rangeChoice(yFn, -1000000.0, CARVE_CEILING,
				scaledEntrances, DensityFunctions.constant(1000000.0));
		DensityFunction islandEntrances = DensityFunctions.min(slopedCheese, carveDeep);
		DensityFunction entrances = DensityFunctions.add(
				DensityFunctions.mul(vanillaEntrances, oneMinusMask),
				DensityFunctions.mul(islandEntrances, mask));

		DensityFunction noodleFn = holder(functions, CAVES_NOODLE);
		DensityFunction noodleDeep = DensityFunctions.rangeChoice(yFn, -1000000.0, CARVE_CEILING,
				noodleFn, DensityFunctions.constant(1000000.0));
		DensityFunction noodleTerm = DensityFunctions.add(
				DensityFunctions.mul(noodleFn, oneMinusMask),
				DensityFunctions.mul(noodleDeep, mask));

		DensityFunction withCaves = DensityFunctions.rangeChoice(slopedCheese, -1000000.0, 1.5625,
				entrances, createCavesFunction(functions, noises, slopedCheese));
		DensityFunction builtDensity = DensityFunctions.min(
				applyBlendDensity(applySurfaceSlides(withCaves)), noodleTerm);

		DensityFunction ceiling = DensityFunctions.yClampedGradient(
				OCEAN_CEILING_BOTTOM, OCEAN_CEILING_TOP, 1000.0, -1000.0);
		DensityFunction oceanCapped = DensityFunctions.min(builtDensity, ceiling);
		DensityFunction finalDensity = DensityFunctions.add(
				DensityFunctions.mul(builtDensity, mask),
				DensityFunctions.mul(oceanCapped, oneMinusMask));

		DensityFunction initialAsBuilt = applySurfaceSlides(
				DensityFunctions.add(
						createInitialDensityFunction(DensityFunctions.cache2d(factor), depth),
						DensityFunctions.constant(-0.703125)).clamp(-64.0, 64.0));
		DensityFunction idwCeiling = DensityFunctions.yClampedGradient(
				IDW_CEILING_BOTTOM, IDW_CEILING_TOP, 1000.0, IDW_CEILING_VALUE);
		DensityFunction idwCapped = DensityFunctions.min(initialAsBuilt, idwCeiling);
		DensityFunction initialDensityWithoutJaggedness = DensityFunctions.add(
				DensityFunctions.mul(initialAsBuilt, mask),
				DensityFunctions.mul(idwCapped, oneMinusMask));
		DensityFunction preliminarySurfaceLevel = DensityFunctions.findTopSurface(
				DensityFunctions.add(initialDensityWithoutJaggedness, DensityFunctions.constant(-0.390625)),
				DensityFunctions.constant(noiseSettings.minY() + noiseSettings.height()),
				noiseSettings.minY(), noiseSettings.getCellHeight());

		DensityFunction climateC = DensityFunctions.add(
				DensityFunctions.mul(offset, DensityFunctions.constant(128.0 / C_SPAN)),
				DensityFunctions.constant((128.0 - 63.0) / C_SPAN)).clamp(-1.0, 1.0);
		DensityFunction climateS = DensityFunctions.flatCache(DensityFunctions.cache2d(
				new IslandField(size, frequency, noise, IslandField.Channel.SIZE,
						new DensityFunction.NoiseHolder(noise(noises, ISLAND_JITTER)),
						new DensityFunction.NoiseHolder(noise(noises, ISLAND_SHAPE)),
						new DensityFunction.NoiseHolder(noise(noises, Noises.SHIFT)),
						new DensityFunction.NoiseHolder(noise(noises, Noises.TEMPERATURE)))));
		DensityFunction anchorTemperature = new IslandField(size, frequency, noise,
				IslandField.Channel.ANCHOR_TEMPERATURE,
				new DensityFunction.NoiseHolder(noise(noises, ISLAND_JITTER)),
				new DensityFunction.NoiseHolder(noise(noises, ISLAND_SHAPE)),
				new DensityFunction.NoiseHolder(noise(noises, Noises.SHIFT)),
				new DensityFunction.NoiseHolder(noise(noises, Noises.TEMPERATURE)));
		DensityFunction temperature = DensityFunctions.flatCache(DensityFunctions.cache2d(
				DensityFunctions.rangeChoice(anchorTemperature, -2.0, 2.0,
						anchorTemperature, vanilla.temperature())));

		DensityFunction climateW = DensityFunctions.flatCache(DensityFunctions.cache2d(
				new IslandField(size, frequency, noise, IslandField.Channel.ROLL,
						new DensityFunction.NoiseHolder(noise(noises, ISLAND_JITTER)),
						new DensityFunction.NoiseHolder(noise(noises, ISLAND_SHAPE)),
						new DensityFunction.NoiseHolder(noise(noises, Noises.SHIFT)),
						new DensityFunction.NoiseHolder(noise(noises, Noises.TEMPERATURE)))));

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
				preliminarySurfaceLevel,
				finalDensity,
				vanilla.veinToggle(),
				vanilla.veinRidged(),
				vanilla.veinGap());
	}


	private static DensityFunction offsetExpression(
			DensityFunctions.Spline.Coordinate continents,
			DensityFunctions.Spline.Coordinate erosion,
			DensityFunctions.Spline.Coordinate ridgesFolded) {
		return DensityFunctions.add(DensityFunctions.constant(-0.50375F),
				DensityFunctions.spline(TerrainProvider.overworldOffset(
						continents, erosion, ridgesFolded, false)));
	}

	private static DensityFunction maskExpression(DensityFunction islandHeight,
			DensityFunction vanillaOffset) {
		return DensityFunctions.mul(
				DensityFunctions.add(islandHeight,
						DensityFunctions.mul(vanillaOffset, DensityFunctions.constant(-1.0))),
				DensityFunctions.constant(128.0 / MASK_BLOCKS))
				.clamp(0.0, 1.0);
	}


	public static IslandField seededField(RegistryAccess registryManager,
			RandomState noiseConfig, int size, float frequency, int noise,
			IslandField.Channel channel) {
		return new IslandField(size, frequency, noise, channel,
				seededNoise(registryManager, noiseConfig, ISLAND_JITTER),
				seededNoise(registryManager, noiseConfig, ISLAND_SHAPE),
				seededNoise(registryManager, noiseConfig, Noises.SHIFT.identifier()),
				seededNoise(registryManager, noiseConfig, Noises.TEMPERATURE.identifier()));
	}

	private static DensityFunction.NoiseHolder seededNoise(RegistryAccess registryManager,
			RandomState noiseConfig, Identifier id) {
		ResourceKey<NormalNoise.NoiseParameters> key =
				ResourceKey.create(Registries.NOISE, id);
		return new DensityFunction.NoiseHolder(
				registryManager.lookupOrThrow(Registries.NOISE).getOrThrow(key),
				noiseConfig.getOrCreateNoise(key));
	}

	public static DensityFunction seededVanillaOffset(RegistryAccess registryManager,
			RandomState noiseConfig) {
		HolderGetter<DensityFunction> functions =
				registryManager.lookupOrThrow(Registries.DENSITY_FUNCTION);
		DensityFunction continents = DensityFunctions.min(
				DensityFunctions.max(holder(functions, CONTINENTS),
						DensityFunctions.constant(OCEAN_CONTINENTS_MIN)),
				DensityFunctions.constant(OCEAN_CONTINENTS));
		DensityFunction expression = offsetExpression(
				wrap(continents), wrap(holder(functions, EROSION)), wrap(holder(functions, RIDGES_FOLDED)));
		return expression.mapAll(seedVisitor(noiseConfig));
	}

	public static DensityFunction seededMask(RegistryAccess registryManager,
			RandomState noiseConfig, int size, float frequency, int noise) {
		return maskExpression(
				seededField(registryManager, noiseConfig, size, frequency, noise,
						IslandField.Channel.HEIGHT),
				seededVanillaOffset(registryManager, noiseConfig));
	}

	private static DensityFunction.Visitor seedVisitor(RandomState noiseConfig) {
		return new DensityFunction.Visitor() {
			@Override
			public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
				return new DensityFunction.NoiseHolder(noise.noiseData(),
						noiseConfig.getOrCreateNoise(noise.noiseData().unwrapKey().orElseThrow()));
			}

			@Override
			public DensityFunction apply(DensityFunction function) {
				return function;
			}
		};
	}


	private static DensityFunction holder(HolderGetter<DensityFunction> functions, Identifier id) {
		return new DensityFunctions.HolderHolder(
				functions.getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, id)));
	}

	private static DensityFunctions.Spline.Coordinate wrap(DensityFunction function) {
		Holder<DensityFunction> entry =
				function instanceof DensityFunctions.HolderHolder holder
						? holder.function()
						: Holder.direct(function);
		return new DensityFunctions.Spline.Coordinate(entry);
	}

	private static Holder<NormalNoise.NoiseParameters> noise(
			HolderGetter<NormalNoise.NoiseParameters> noises,
			ResourceKey<NormalNoise.NoiseParameters> key) {
		return noises.getOrThrow(key);
	}

	private static Holder<NormalNoise.NoiseParameters> noise(
			HolderGetter<NormalNoise.NoiseParameters> noises, Identifier id) {
		return noises.getOrThrow(ResourceKey.create(Registries.NOISE, id));
	}


	private static DensityFunction applyBlending(DensityFunction function, DensityFunction blendOffset) {
		DensityFunction densityFunction = DensityFunctions.lerp(
				DensityFunctions.blendAlpha(), blendOffset, function);
		return DensityFunctions.flatCache(DensityFunctions.cache2d(densityFunction));
	}

	private static DensityFunction createInitialDensityFunction(DensityFunction factor, DensityFunction depth) {
		DensityFunction densityFunction = DensityFunctions.mul(depth, factor);
		return DensityFunctions.mul(DensityFunctions.constant(4.0), densityFunction.quarterNegative());
	}

	private static DensityFunction applyBlendDensity(DensityFunction density) {
		DensityFunction densityFunction = DensityFunctions.blendDensity(density);
		return DensityFunctions.mul(DensityFunctions.interpolated(densityFunction),
				DensityFunctions.constant(0.64)).squeeze();
	}

	private static DensityFunction applySurfaceSlides(DensityFunction density) {
		return applySlides(density, -64, 384, 80, 64, -0.078125, 0, 24, 0.1171875);
	}

	private static DensityFunction applySlides(DensityFunction density, int minY, int maxY,
			int topRelativeMinY, int topRelativeMaxY, double topDensity,
			int bottomRelativeMinY, int bottomRelativeMaxY, double bottomDensity) {
		DensityFunction densityFunction = density;
		DensityFunction densityFunction2 = DensityFunctions.yClampedGradient(
				minY + maxY - topRelativeMinY, minY + maxY - topRelativeMaxY, 1.0, 0.0);
		densityFunction = DensityFunctions.lerp(densityFunction2, topDensity, densityFunction);
		DensityFunction densityFunction3 = DensityFunctions.yClampedGradient(
				minY + bottomRelativeMinY, minY + bottomRelativeMaxY, 0.0, 1.0);
		return DensityFunctions.lerp(densityFunction3, bottomDensity, densityFunction);
	}

	private static DensityFunction createCavesFunction(HolderGetter<DensityFunction> functions,
			HolderGetter<NormalNoise.NoiseParameters> noises,
			DensityFunction slopedCheese) {
		DensityFunction densityFunction = holder(functions, CAVES_SPAGHETTI_2D);
		DensityFunction densityFunction2 = holder(functions, CAVES_SPAGHETTI_ROUGHNESS);
		DensityFunction densityFunction3 = DensityFunctions.noise(
				noise(noises, Noises.CAVE_LAYER), 8.0);
		DensityFunction densityFunction4 = DensityFunctions.mul(
				DensityFunctions.constant(4.0), densityFunction3.square());
		DensityFunction densityFunction5 = DensityFunctions.noise(
				noise(noises, Noises.CAVE_CHEESE), 0.6666666666666666);
		DensityFunction densityFunction6 = DensityFunctions.add(
				DensityFunctions.add(DensityFunctions.constant(0.27), densityFunction5).clamp(-1.0, 1.0),
				DensityFunctions.add(DensityFunctions.constant(1.5),
						DensityFunctions.mul(DensityFunctions.constant(-0.64), slopedCheese)).clamp(0.0, 0.5));
		DensityFunction densityFunction7 = DensityFunctions.add(densityFunction4, densityFunction6);
		DensityFunction densityFunction8 = DensityFunctions.min(
				DensityFunctions.min(densityFunction7, holder(functions, CAVES_ENTRANCES)),
				DensityFunctions.add(densityFunction, densityFunction2));
		DensityFunction densityFunction9 = holder(functions, CAVES_PILLARS);
		DensityFunction densityFunction10 = DensityFunctions.rangeChoice(
				densityFunction9, -1000000.0, 0.03, DensityFunctions.constant(-1000000.0), densityFunction9);
		return DensityFunctions.max(densityFunction8, densityFunction10);
	}
}
