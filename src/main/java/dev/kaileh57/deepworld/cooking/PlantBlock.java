package dev.kaileh57.deepworld.cooking;

import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.farming.Farming;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class PlantBlock extends VegetationBlock implements PolymerTexturedBlock {
	public static final IntegerProperty age = IntegerProperty.create("age", 0, 3);
	private static BlockState sprout_state;
	private final BlockState client_state;
	private final String id;

	public PlantBlock(Properties properties, Identifier model, String id) {
		super(properties);
		this.id = id;
		this.client_state = request(model);
		if (sprout_state == null) {
			sprout_state = request(DeepWorld.id("block/sprout"));
		}
		registerDefaultState(stateDefinition.any().setValue(age, 3));
	}

	private static BlockState request(Identifier model) {
		BlockState requested = PolymerBlockResourceUtils.requestBlock(BlockModelType.TRIPWIRE, PolymerBlockModel.of(model));
		if (requested == null) {
			requested = PolymerBlockResourceUtils.requestBlock(BlockModelType.PLANT, PolymerBlockModel.of(model));
		}
		return requested != null ? requested : Blocks.SHORT_GRASS.defaultBlockState();
	}

	public String id() {
		return id;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(age);
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isFaceSturdy(level, pos, Direction.UP);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return state.getValue(age) < 3;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(6) == 0 && Farming.can_grow(this, level, pos)) {
			level.setBlock(pos, state.setValue(age, state.getValue(age) + 1), 2);
		}
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return state.getValue(age) >= 3 ? client_state : sprout_state;
	}
}
