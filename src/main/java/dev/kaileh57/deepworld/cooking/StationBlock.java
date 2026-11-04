package dev.kaileh57.deepworld.cooking;

import com.google.gson.JsonObject;
import dev.kaileh57.deepworld.DeepWorld;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class StationBlock extends Block implements PolymerBlock, BlockWithElementHolder, EntityBlock {
	private final CookData.Station station;

	public StationBlock(Properties properties, CookData.Station station) {
		super(properties);
		this.station = station;
	}

	public CookData.Station station() {
		return station;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return Blocks.BARRIER.defaultBlockState();
	}

	@Override
	public ElementHolder createElementHolder(ServerLevel level, BlockPos pos, BlockState state) {
		ElementHolder holder = new ElementHolder();
		for (JsonObject part : station.visual()) {
			float scale = part.get("scale").getAsFloat();
			float x = part.get("x").getAsFloat();
			float y = part.get("y").getAsFloat();
			float z = part.get("z").getAsFloat();
			Quaternionf rotation = new Quaternionf().rotationXYZ((float) Math.toRadians(part.get("rx").getAsFloat()), (float) Math.toRadians(part.get("ry").getAsFloat()), (float) Math.toRadians(part.get("rz").getAsFloat()));
			if (part.has("block")) {
				BlockState shown;
				try {
					shown = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, part.get("block").getAsString(), false).blockState();
				} catch (Exception error) {
					DeepWorld.logger.error("bad station visual {}", part, error);
					continue;
				}
				BlockDisplayElement element = new BlockDisplayElement(shown);
				element.setScale(new Vector3f(scale));
				element.setTranslation(new Vector3f(-scale / 2 + x, -0.5F + y, -scale / 2 + z));
				holder.addElement(element);
			} else {
				ItemDisplayElement element = new ItemDisplayElement(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(part.get("item").getAsString()))));
				element.setItemDisplayContext(ItemDisplayContext.FIXED);
				element.setScale(new Vector3f(scale));
				element.setTranslation(new Vector3f(x, -0.5F + y, z));
				element.setLeftRotation(rotation);
				holder.addElement(element);
			}
		}
		return holder;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return station.kind().equals("cook") ? new CookBlockEntity(pos, state) : null;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != Cooking.cook_entity) {
			return null;
		}
		return (tick_level, pos, tick_state, entity) -> ((CookBlockEntity) entity).server_tick((ServerLevel) tick_level, pos);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer server_player) {
			if (level.getBlockEntity(pos) instanceof CookBlockEntity cook) {
				new CookGui(server_player, cook).open();
			} else {
				new ProcessGui(server_player, station, pos).open();
			}
		}
		return InteractionResult.SUCCESS_SERVER;
	}
}
