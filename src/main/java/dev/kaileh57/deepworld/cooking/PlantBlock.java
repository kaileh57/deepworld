package dev.kaileh57.deepworld.cooking;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

public class PlantBlock extends VegetationBlock implements PolymerTexturedBlock {
	private final BlockState client_state;

	public PlantBlock(Properties properties, Identifier model) {
		super(properties);
		BlockState requested = PolymerBlockResourceUtils.requestBlock(BlockModelType.TRIPWIRE, PolymerBlockModel.of(model));
		if (requested == null) {
			requested = PolymerBlockResourceUtils.requestBlock(BlockModelType.PLANT, PolymerBlockModel.of(model));
		}
		this.client_state = requested != null ? requested : Blocks.SHORT_GRASS.defaultBlockState();
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isFaceSturdy(level, pos, Direction.UP);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return client_state;
	}
}
