package twilightforest.init;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;
import twilightforest.TFRegistries;
import twilightforest.TwilightForestMod;
import twilightforest.init.custom.BiomeLayerStack;
import twilightforest.world.components.chunkgenerators.*;
import twilightforest.world.components.layer.BiomeDensitySource;

@SuppressWarnings("unused")
public class TFDensityFunctions {
    public static final DeferredRegister<Codec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES = DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, TwilightForestMod.ID);

    public static final DeferredHolder<Codec<? extends DensityFunction>, Codec<TerrainDensityRouter>> BIOME_DRIVEN = register("biome_driven", TerrainDensityRouter.CODEC);
    public static final DeferredHolder<Codec<? extends DensityFunction>, Codec<FocusedDensityFunction>> FOCUSED = register("focused", FocusedDensityFunction.CODEC);
    public static final DeferredHolder<Codec<? extends DensityFunction>, Codec<HollowHillFunction>> HOLLOW_HILL = register("hollow_hill", HollowHillFunction.CODEC);
    public static final DeferredHolder<Codec<? extends DensityFunction>, Codec<AbsoluteDifferenceFunction.Min>> COORD_MIN = register("coord_min", AbsoluteDifferenceFunction.Min.CODEC);
    public static final DeferredHolder<Codec<? extends DensityFunction>, Codec<AbsoluteDifferenceFunction.Max>> COORD_MAX = register("coord_max", AbsoluteDifferenceFunction.Max.CODEC);
    public static final DeferredHolder<Codec<? extends DensityFunction>, Codec<SqrtDensityFunction>> SQRT = register("sqrt", SqrtDensityFunction.CODEC);

    public static final ResourceKey<DensityFunction> BIOMES_RAW = ResourceKey.create(Registries.DENSITY_FUNCTION, TwilightForestMod.prefix("raw_biome_terrain"));
    public static final ResourceKey<DensityFunction> FORESTED_TERRAIN = ResourceKey.create(Registries.DENSITY_FUNCTION, TwilightForestMod.prefix("forested_terrain"));
    public static final ResourceKey<DensityFunction> SKYLIGHT_TERRAIN = ResourceKey.create(Registries.DENSITY_FUNCTION, TwilightForestMod.prefix("skylight_terrain"));

    private static <T extends DensityFunction> DeferredHolder<Codec<? extends DensityFunction>, Codec<T>> register(String name, KeyDispatchDataCodec<T> keyCodec) {
        return DENSITY_FUNCTION_TYPES.register(name, keyCodec::codec);
    }

    public static void bootstrap(BootstapContext<DensityFunction> context) {
        DensityFunction referencedBiomeDensity = makeBiomeDensityRaw(context);
        DensityFunction ambientTerrainNoise = makeAmbientNoise2D(context);

        makeForestedTerrain(context, referencedBiomeDensity, ambientTerrainNoise);
        makeSkylightTerrain(context, referencedBiomeDensity, ambientTerrainNoise);
    }

    @NotNull
    private static DensityFunction makeBiomeDensityRaw(BootstapContext<DensityFunction> context) {
        Holder.Reference<BiomeDensitySource> biomeGrid = context.lookup(TFRegistries.Keys.BIOME_TERRAIN_DATA).getOrThrow(BiomeLayerStack.BIOME_GRID);
        Holder.Reference<NormalNoise.NoiseParameters> surfaceParams = context.lookup(Registries.NOISE).getOrThrow(Noises.SURFACE);

        DensityFunction rawBiomeDensityReferenced = new TerrainDensityRouter(
                biomeGrid,
                new DensityFunction.NoiseHolder(surfaceParams),
                -31,
                64,
                1,
                DensityFunctions.constant(8),
                DensityFunctions.constant(-1.25)
        );

        // Debug: For a flat substitute of TerrainDensityRouter
        //if (false) rawBiomeDensityReferenced = DensityFunctions.yClampedGradient(-31, 32, 2, -2);

        return new DensityFunctions.HolderHolder(context.register(BIOMES_RAW, rawBiomeDensityReferenced));
    }

    @NotNull
    private static DensityFunction makeAmbientNoise2D(BootstapContext<DensityFunction> context) {
        HolderGetter<NormalNoise.NoiseParameters> noiseLookup = context.lookup(Registries.NOISE);
        Holder.Reference<NormalNoise.NoiseParameters> surfaceParams = noiseLookup.getOrThrow(Noises.SURFACE);
        Holder.Reference<NormalNoise.NoiseParameters> ridgeParams = noiseLookup.getOrThrow(Noises.RIDGE);

        DensityFunction noiseInterpolator = mulAddHalf(DensityFunctions.noise(surfaceParams, 1, 0));
        DensityFunction wideNoise = mulAddHalf(DensityFunctions.noise(ridgeParams, 1, 0));
        DensityFunction thinNoise = mulAddHalf(DensityFunctions.noise(ridgeParams, 4, 0));

        DensityFunction jitteredNoise = DensityFunctions.lerp(
                noiseInterpolator.clamp(0, 1),
                wideNoise,
                thinNoise
        );

        return DensityFunctions.flatCache(jitteredNoise);
    }

    @NotNull
    private static DensityFunction mulAddHalf(DensityFunction input) {
        // mulAddHalf(x) = x * 0.5 + 0.5
        // Useful for squeezing function range [-1,1] into [0,1]
        return DensityFunctions.add(
                DensityFunctions.constant(0.5),
                DensityFunctions.mul(
                        DensityFunctions.constant(0.5),
                        input
                )
        );
    }

    private static void makeForestedTerrain(BootstapContext<DensityFunction> context, DensityFunction rawBiomeDensity, DensityFunction ambientTerrainNoise) {
        DensityFunction biomedLandscape = DensityFunctions.mul(
                DensityFunctions.constant(1 / 6f),
                DensityFunctions.add(
                        rawBiomeDensity,
                        DensityFunctions.yClampedGradient(-31, 256, 31, -256)
                )
        );

        DensityFunction finalDensity = DensityFunctions.add(
                biomedLandscape,
                DensityFunctions.interpolated(DensityFunctions.max(
                        DensityFunctions.zero(),
                        ambientTerrainNoise
                ))
        );

        context.register(FORESTED_TERRAIN, finalDensity.clamp(-0.1, 1));
    }

    // Heavy WIP
    private static void makeSkylightTerrain(BootstapContext<DensityFunction> context, DensityFunction rawBiomeDensity, DensityFunction ambientTerrainNoise) {
        // FIXME Rapid terrain changes around Highlands are causing islands to stretch into walls when transitioning from the Stream biome

        DensityFunction skyIslandNoise = DensityFunctions.add(
                DensityFunctions.constant(-0.5),
                DensityFunctions.mul(
                        DensityFunctions.add(
                                DensityFunctions.constant(-0.5),
                                ambientTerrainNoise
                        ),
                        DensityFunctions.constant(5)
                )
        );

        DensityFunction biomeDensity = DensityFunctions.mul(
                DensityFunctions.constant(-0.25),
                DensityFunctions.mul(DensityFunctions.add(
                        rawBiomeDensity,
                        DensityFunctions.yClampedGradient(-31, 256, 31, -256)
                ), DensityFunctions.constant(-1)).halfNegative().abs()
        );

        DensityFunction finalDensity = DensityFunctions.add(
                new SqrtDensityFunction(
                        DensityFunctions.interpolated(skyIslandNoise).clamp(0, 2)
                ),
                biomeDensity
		);

        context.register(SKYLIGHT_TERRAIN, finalDensity.clamp(-0.1, 1));
    }
}
