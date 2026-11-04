package dev.kaileh57.deepworld.transit;

import dev.kaileh57.deepworld.content.Content;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import dev.kaileh57.deepworld.world.Layers;

public final class Lifts {
	public static double speed = 0.4;
	private record Ride(ArmorStand car, ServerPlayer rider, double target_y, int direction) {
	}

	private static final Map<UUID, Ride> rides = new HashMap<>();

	private Lifts() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer server_player) || !(level instanceof ServerLevel server_level)) {
				return InteractionResult.PASS;
			}
			Block lift = Content.blocks.get("lift_station");
			BlockPos pos = hit.getBlockPos();
			if (!level.getBlockState(pos).is(lift) || player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			open(server_player, server_level, pos);
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	public static List<BlockPos> stations(ServerLevel level, BlockPos from) {
		List<BlockPos> found = new ArrayList<>();
		Block lift = Content.blocks.get("lift_station");
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
			cursor.set(from.getX(), y, from.getZ());
			if (level.getBlockState(cursor).is(lift)) {
				found.add(cursor.immutable());
			}
		}
		return found;
	}

	public static boolean clear(ServerLevel level, BlockPos from, BlockPos to) {
		int low = Math.min(from.getY(), to.getY()) + 1;
		int high = Math.max(from.getY(), to.getY());
		for (int y = low; y < high; y++) {
			BlockPos pos = new BlockPos(from.getX(), y, from.getZ());
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static void open(ServerPlayer player, ServerLevel level, BlockPos here) {
		List<BlockPos> list = stations(level, here);
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
		gui.setTitle(Component.literal("lift"));
		int index = 0;
		for (BlockPos stop : list) {
			if (index >= 27) {
				break;
			}
			boolean current = stop.equals(here);
			Layers.Layer layer = Layers.at(stop.getY());
			String where = layer == null ? "surface" : layer.name();
			boolean open_path = current || clear(level, here, stop);
			GuiElementBuilder button = new GuiElementBuilder(current ? Items.CONCRETE_POWDER.lime() : open_path ? Items.GOLD_INGOT : Items.BARRIER)
				.setName(Component.literal("y " + stop.getY() + " · " + where))
				.addLoreLine(Component.literal(current ? "you are here" : open_path ? "click to ride" : "shaft blocked"));
			if (!current && open_path) {
				button.setCallback((slot, type, action, g) -> {
					g.close();
					start(player, level, here, stop);
				});
			}
			gui.setSlot(index++, button.build());
		}
		gui.open();
	}

	public static void start(ServerPlayer player, ServerLevel level, BlockPos from, BlockPos to) {
		ArmorStand car = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
		if (car == null) {
			return;
		}
		car.setInvisible(true);
		car.setNoGravity(true);
		car.setSilent(true);
		car.setPos(from.getX() + 0.5, from.getY() + 1.0, from.getZ() + 0.5);
		level.addFreshEntity(car);
		player.startRiding(car, true, true);
		int direction = to.getY() > from.getY() ? 1 : -1;
		rides.put(player.getUUID(), new Ride(car, player, to.getY() + 1.0, direction));
		level.playSound(null, from, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.6F);
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Ride>> iterator = rides.entrySet().iterator();
		while (iterator.hasNext()) {
			Ride ride = iterator.next().getValue();
			ArmorStand car = ride.car();
			if (car.isRemoved() || !ride.rider().isAlive() || ride.rider().getVehicle() != car) {
				car.discard();
				iterator.remove();
				continue;
			}
			double y = car.getY() + speed * ride.direction();
			boolean arrived = ride.direction() > 0 ? y >= ride.target_y() : y <= ride.target_y();
			if (arrived) {
				car.setPos(car.getX(), ride.target_y(), car.getZ());
				ride.rider().stopRiding();
				ride.rider().teleportTo(car.getX(), ride.target_y(), car.getZ());
				car.discard();
				iterator.remove();
				continue;
			}
			car.setPos(car.getX(), y, car.getZ());
		}
	}
}
