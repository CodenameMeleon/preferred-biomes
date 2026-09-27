package net.codenamemeleon.preferredbiomes.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public record IslandRouterSlot(int size, float frequency, int noise, Slot slot,
		DensityFunction built) implements DensityFunction {

	public enum Slot implements StringRepresentable {
		FINAL_DENSITY("final_density"),
		PRELIMINARY_SURFACE_LEVEL("preliminary_surface_level");

		public static final Codec<Slot> CODEC = StringRepresentable.fromEnum(Slot::values);

		private final String name;

		Slot(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public static final MapCodec<IslandRouterSlot> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.fieldOf("size").forGetter(IslandRouterSlot::size),
			Codec.FLOAT.fieldOf("frequency").forGetter(IslandRouterSlot::frequency),
			Codec.INT.fieldOf("noise").forGetter(IslandRouterSlot::noise),
			Slot.CODEC.fieldOf("slot").forGetter(IslandRouterSlot::slot),
			RegistryOps.retrieveGetter(Registries.DENSITY_FUNCTION),
			RegistryOps.retrieveGetter(Registries.NOISE),
			RegistryOps.retrieveElement(NoiseGeneratorSettings.OVERWORLD)
	).apply(instance, (size, frequency, noise, slot, functions, noises, overworld) ->
			new IslandRouterSlot(size, frequency, noise, slot,
					IslandTerrain.buildSlot(slot, overworld.value().noiseRouter(),
							overworld.value().noiseSettings(), functions, noises, size, frequency, noise))));

	public static final KeyDispatchDataCodec<IslandRouterSlot> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

	@Override
	public double compute(DensityFunction.FunctionContext context) {
		return this.built.compute(context);
	}

	@Override
	public void fillArray(double[] densities, DensityFunction.ContextProvider applier) {
		this.built.fillArray(densities, applier);
	}

	@Override
	public double minValue() {
		return this.built.minValue();
	}

	@Override
	public double maxValue() {
		return this.built.maxValue();
	}

	@Override
	public DensityFunction mapAll(DensityFunction.Visitor visitor) {
		return visitor.apply(new IslandRouterSlot(this.size, this.frequency, this.noise, this.slot,
				this.built.mapAll(visitor)));
	}

	@Override
	public KeyDispatchDataCodec<? extends DensityFunction> codec() {
		return CODEC;
	}
}
