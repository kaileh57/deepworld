package dev.kaileh57.deepworld.forge;

import dev.kaileh57.deepworld.content.Content;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ForgeBlock extends Block implements EntityBlock, PolymerTexturedBlock {
	public static final EnumProperty<Direction> facing = HorizontalDirectionalBlock.FACING;
	public static final BooleanProperty lit = BooleanProperty.create("lit");
	private final String station;
	private final Map<Direction, BlockState> client_states = new EnumMap<>(Direction.class);

	public ForgeBlock(Properties properties, String station, Identifier model) {
		super(properties);
		this.station = station;
		this.registerDefaultState(this.stateDefinition.any().setValue(facing, Direction.NORTH).setValue(lit, false));
		client_states.put(Direction.NORTH, PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, PolymerBlockModel.of(model, 0, 0)));
		client_states.put(Direction.EAST, PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, PolymerBlockModel.of(model, 0, 90)));
		client_states.put(Direction.SOUTH, PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, PolymerBlockModel.of(model, 0, 180)));
		client_states.put(Direction.WEST, PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, PolymerBlockModel.of(model, 0, 270)));
	}

	public String station() {
		return station;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(facing, lit);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(facing, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return client_states.get(state.getValue(facing));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ForgeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != Content.forge_entity) {
			return null;
		}
		return (tick_level, pos, tick_state, entity) -> ((ForgeBlockEntity) entity).server_tick((ServerLevel) tick_level, pos, tick_state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer server_player && level.getBlockEntity(pos) instanceof ForgeBlockEntity forge) {
			new ForgeGui(server_player, forge).open();
		}
		return InteractionResult.SUCCESS_SERVER;
	}
}
