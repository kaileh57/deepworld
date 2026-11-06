package dev.kaileh57.deepworld.transit;

import dev.kaileh57.deepworld.content.Content;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
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
	private record Ride(ArmorStand car, ServerPlayer rider, double target_y, int direction, double speed) {
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

	private static final Map<String, Long> calls = new HashMap<>();
	private static final Map<UUID, Boolean> freight_mode = new HashMap<>();

	public static String call_state(ServerLevel level, BlockPos here) {
		LiftNetwork network = LiftNetwork.get(level.getServer());
		Long arrives = calls.get(LiftNetwork.column(here) + " " + here.getY());
		if (arrives != null) {
			if (level.getGameTime() < arrives) {
				return "coming";
			}
			calls.remove(LiftNetwork.column(here) + " " + here.getY());
			network.move_car(here);
		}
		return network.car_y(here) == here.getY() ? "here" : "away";
	}

	public static void call(ServerLevel level, BlockPos here) {
		LiftNetwork network = LiftNetwork.get(level.getServer());
		int distance = Math.abs(network.car_y(here) - here.getY());
		calls.put(LiftNetwork.column(here) + " " + here.getY(), level.getGameTime() + Math.max(20, (long) (distance / speed)));
	}

	public static net.minecraft.world.Container freight_box(ServerLevel level, BlockPos station) {
		for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
			if (direction != net.minecraft.core.Direction.UP && level.getBlockEntity(station.relative(direction)) instanceof net.minecraft.world.Container container) {
				return container;
			}
		}
		return null;
	}

	public static int send_freight(ServerLevel level, BlockPos from, BlockPos to) {
		net.minecraft.world.Container source = freight_box(level, from);
		net.minecraft.world.Container target = freight_box(level, to);
		if (source == null || target == null) {
			return -1;
		}
		int moved = 0;
		for (int slot = 0; slot < source.getContainerSize(); slot++) {
			net.minecraft.world.item.ItemStack stack = source.getItem(slot);
			for (int t = 0; t < target.getContainerSize() && !stack.isEmpty(); t++) {
				net.minecraft.world.item.ItemStack there = target.getItem(t);
				if (there.isEmpty()) {
					target.setItem(t, stack.copy());
					moved += stack.getCount();
					stack.setCount(0);
				} else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
					int amount = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
					there.grow(amount);
					stack.shrink(amount);
					moved += amount;
				}
			}
			source.setItem(slot, stack.isEmpty() ? net.minecraft.world.item.ItemStack.EMPTY : stack);
		}
		source.setChanged();
		target.setChanged();
		LiftNetwork.get(level.getServer()).move_car(to);
		return moved;
	}

	private static void open(ServerPlayer player, ServerLevel level, BlockPos here) {
		List<BlockPos> list = stations(level, here);
		LiftNetwork network = LiftNetwork.get(level.getServer());
		String car = call_state(level, here);
		boolean freight = freight_mode.getOrDefault(player.getUUID(), false);
		dev.kaileh57.deepworld.hud.DwGui gui = new dev.kaileh57.deepworld.hud.DwGui(MenuType.GENERIC_9x3, player, false);
		gui.setTitle(Component.literal(freight ? "lift · freight" : "lift"));
		int index = 0;
		for (BlockPos stop : list) {
			if (index >= 24) {
				break;
			}
			boolean current = stop.equals(here);
			Layers.Layer layer = Layers.at(stop.getY());
			String where = layer == null ? "surface" : layer.name();
			boolean open_path = current || clear(level, here, stop);
			boolean cabled = current || network.reaches(here, stop);
			boolean ready = car.equals("here");
			String note = current ? "you are here" : !open_path ? "shaft blocked" : !cabled ? "needs more cable" : !ready ? "call the car first" : freight ? "click to send the box next to this station" : "click to ride";
			GuiElementBuilder button = new GuiElementBuilder(current ? Items.CONCRETE_POWDER.lime() : open_path && cabled && ready ? Items.GOLD_INGOT : Items.BARRIER)
				.setName(Component.literal("y " + stop.getY() + " · " + where))
				.addLoreLine(Component.literal(note));
			if (!current && open_path && cabled && ready) {
				button.setCallback((slot, type, action, g) -> {
					g.close();
					if (freight) {
						int moved = send_freight(level, here, stop);
						dev.kaileh57.deepworld.hud.Hud.notice(player, Component.literal(moved < 0 ? "both stations need a chest or barrel beside them" : "sent " + moved + " items"));
					} else {
						network.move_car(stop);
						start(player, level, here, stop);
					}
				});
			}
			gui.setSlot(index++, button.build());
		}
		int cable = network.cable_at(here);
		gui.setSlot(24, new GuiElementBuilder(Items.GOLD_BLOCK).setName(Component.literal("cable: " + cable + " (reaches " + cable * LiftNetwork.blocks_per_cable + " blocks)")).addLoreLine(Component.literal("click to install a gold block of cable")).setCallback((slot, type, action, g) -> {
			if (player.getInventory().countItem(Items.GOLD_BLOCK) > 0 || player.isCreative()) {
				if (!player.isCreative()) {
					for (int slot_index = 0; slot_index < player.getInventory().getContainerSize(); slot_index++) {
						if (player.getInventory().getItem(slot_index).is(Items.GOLD_BLOCK)) {
							player.getInventory().getItem(slot_index).shrink(1);
							break;
						}
					}
				}
				network.add_cable(here, 1);
				level.playSound(null, here, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			g.close();
			open(player, level, here);
		}).build());
		gui.setSlot(25, new GuiElementBuilder(car.equals("here") ? Items.MINECART : Items.BELL).setName(Component.literal(car.equals("here") ? "the car is here" : car.equals("coming") ? "the car is on its way" : "call the car")).setCallback((slot, type, action, g) -> {
			if (car.equals("away")) {
				call(level, here);
				level.playSound(null, here, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			g.close();
		}).build());
		gui.setSlot(26, new GuiElementBuilder(freight ? Items.CHEST : Items.SADDLE).setName(Component.literal(freight ? "freight mode" : "passenger mode")).addLoreLine(Component.literal("click to switch")).setCallback((slot, type, action, g) -> {
			freight_mode.put(player.getUUID(), !freight);
			g.close();
			open(player, level, here);
		}).build());
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
		double ride_speed = dev.kaileh57.deepworld.guild.Guild.focused(player, "engineer") ? speed * 1.5 : speed;
		rides.put(player.getUUID(), new Ride(car, player, to.getY() + 1.0, direction, ride_speed));
		dev.kaileh57.deepworld.guild.Guild.add(player, "lift_rides", 1);
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
			double y = car.getY() + ride.speed() * ride.direction();
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
