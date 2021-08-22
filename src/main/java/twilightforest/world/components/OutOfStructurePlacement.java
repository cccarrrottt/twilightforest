package twilightforest.world.components;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.configurations.NoneDecoratorConfiguration;
import net.minecraft.world.level.levelgen.placement.DecorationContext;
import net.minecraft.world.level.levelgen.placement.FeatureDecorator;
import twilightforest.world.components.chunkgenerators.ChunkGeneratorTwilightBase;
import twilightforest.world.registration.TFFeature;

import java.util.Random;
import java.util.stream.Stream;

public class OutOfStructurePlacement extends FeatureDecorator<NoneDecoratorConfiguration> {
	public OutOfStructurePlacement(Codec<NoneDecoratorConfiguration> codec) {
		super(codec);
	}

	@Override
	public Stream<BlockPos> getPositions(DecorationContext worldDecoratingHelper, Random random, NoneDecoratorConfiguration noPlacementConfig, BlockPos blockPos) {
		if (!(worldDecoratingHelper.getLevel().getLevel().getChunkSource().generator instanceof ChunkGeneratorTwilightBase tfChunkGen))
			return Stream.of(blockPos);

		// Feature Center
		BlockPos.MutableBlockPos featurePos = TFFeature.getNearestCenterXYZ(blockPos.getX() >> 4, blockPos.getZ() >> 4).mutable();

		final TFFeature feature = tfChunkGen.getFeatureCached(new ChunkPos(featurePos), worldDecoratingHelper.getLevel());

		if (feature.areChunkDecorationsEnabled)
			return Stream.of(blockPos);

		// Turn Feature Center into Feature Offset
		featurePos.set(Math.abs(featurePos.getX() - blockPos.getX()), 0, Math.abs(featurePos.getZ() - blockPos.getZ()));
		int size = feature.size * 16;

		return featurePos.getX() < size && featurePos.getZ() < size ? Stream.empty() : Stream.of(blockPos);
	}
}