package dev.kaileh57.deepworld.transit;

import com.mojang.serialization.Codec;
import dev.kaileh57.deepworld.cooking.StationBlock;
import eu.pb4.polymer.virtualentity.api.attachment.BlockAwareAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RailDevice extends BlockEntity {
	public static final List<String> modes = List.of("stop", "unload", "load", "dispatch");
	public static final int[] intervals = {1, 2, 5, 10, 20};
	public String name = "";
	public Set<String> routes = new HashSet<>();
	public final NonNullList<ItemStack> filter = NonNullList.withSize(9, ItemStack.EMPTY);
	public String mode = "stop";
	public String dispatch_to = "";
	public int interval = 5;
	public Direction dispatch_dir = Direction.NORTH;
	public long last_dispatch;
	public boolean powered;
	public int cable;
	private final Map<UUID, Direction> held = new HashMap<>();
	private boolean shown_red;

	public RailDevice(BlockPos pos, BlockState state) {
		super(Transit.rail_entity, pos, state);
	}

	public String kind() {
		return getBlockState().getBlock() instanceof StationBlock station ? station.station().kind() : "";
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		name = input.getStringOr("name", "");
		routes = new HashSet<>(input.read("routes", Codec.STRING.listOf()).orElse(List.of()));
		ContainerHelper.loadAllItems(input, filter);
		mode = input.getStringOr("mode", "stop");
		dispatch_to = input.getStringOr("dispatch_to", "");
		interval = input.getIntOr("interval", 5);
		dispatch_dir = Direction.from2DDataValue(input.getIntOr("dispatch_dir", 2));
		last_dispatch = input.getLongOr("last_dispatch", 0L);
		powered = input.getBooleanOr("powered", false);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("name", name);
		output.store("routes", Codec.STRING.listOf(), new ArrayList<>(routes));
		ContainerHelper.saveAllItems(output, filter);
		output.putString("mode", mode);
		output.putString("dispatch_to", dispatch_to);
		output.putInt("interval", interval);
		output.putInt("dispatch_dir", dispatch_dir.get2DDataValue());
		output.putLong("last_dispatch", last_dispatch);
		output.putBoolean("powered", powered);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server_level) {
			RailNetwork.remove(server_level.getServer(), pos);
			for (ItemStack stack : filter) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
			}
		}
		super.preRemoveSideEffects(pos, state);
	}

	public List<BlockPos> rails() {
		List<BlockPos> out = new ArrayList<>();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos rail = RailWalk.rail_at(level, worldPosition.relative(direction));
			if (rail != null) {
				out.add(rail);
			}
		}
		return out;
	}

	public List<AbstractMinecart> carts_at(List<BlockPos> rails, double reach) {
		List<AbstractMinecart> out = new ArrayList<>();
		for (BlockPos rail : rails) {
			for (AbstractMinecart cart : level.getEntitiesOfClass(AbstractMinecart.class, new AABB(rail).inflate(reach))) {
				if (!out.contains(cart)) {
					out.add(cart);
				}
			}
		}
		return out;
	}

	public void server_tick(ServerLevel level, BlockPos pos) {
		switch (kind()) {
			case "signal" -> tick_signal(level);
			case "switch" -> {
				if (level.getGameTime() % 2 == 0) {
					tick_switch(level);
				}
			}
			case "rail_station" -> tick_station(level);
			default -> {
			}
		}
	}

	private void tick_signal(ServerLevel level) {
		List<BlockPos> rails = rails();
		boolean any_held = false;
		for (AbstractMinecart cart : carts_at(rails, 0.3)) {
			if (Carts.follower(cart)) {
				continue;
			}
			Direction heading = held.containsKey(cart.getUUID()) ? held.get(cart.getUUID()) : RailWalk.heading(cart);
			if (heading == null) {
				continue;
			}
			BlockPos rail = RailWalk.rail_at(level, cart.blockPosition());
			if (rail == null) {
				continue;
			}
			boolean occupied = occupied(level, cart, RailWalk.ahead(level, rail, heading, 200, this::next_signal_at));
			if (occupied) {
				held.put(cart.getUUID(), heading);
				cart.setDeltaMovement(Vec3.ZERO);
				cart.setPos(rail.getX() + 0.5, cart.getY(), rail.getZ() + 0.5);
				any_held = true;
			} else if (held.remove(cart.getUUID()) != null) {
				cart.setDeltaMovement(heading.getStepX() * 0.6, 0, heading.getStepZ() * 0.6);
				level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6F, 1.4F);
			}
		}
		held.keySet().removeIf(id -> !(level.getEntity(id) instanceof AbstractMinecart cart) || cart.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 9);
		if (any_held != shown_red) {
			shown_red = any_held;
			light(level, any_held ? Blocks.CONCRETE.red().defaultBlockState() : Blocks.CONCRETE.lime().defaultBlockState());
		}
	}

	private boolean next_signal_at(BlockPos rail) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = rail.relative(direction);
			if (!side.equals(worldPosition) && level.getBlockEntity(side) instanceof RailDevice device && device.kind().equals("signal")) {
				return true;
			}
			if (!side.above().equals(worldPosition) && level.getBlockEntity(side.above()) instanceof RailDevice device && device.kind().equals("signal")) {
				return true;
			}
		}
		return false;
	}

	public static boolean occupied(ServerLevel level, AbstractMinecart self, List<BlockPos> section) {
		if (section.isEmpty()) {
			return false;
		}
		int min_x = Integer.MAX_VALUE, min_y = Integer.MAX_VALUE, min_z = Integer.MAX_VALUE, max_x = Integer.MIN_VALUE, max_y = Integer.MIN_VALUE, max_z = Integer.MIN_VALUE;
		Set<BlockPos> set = new HashSet<>(section);
		for (BlockPos pos : section) {
			min_x = Math.min(min_x, pos.getX());
			min_y = Math.min(min_y, pos.getY());
			min_z = Math.min(min_z, pos.getZ());
			max_x = Math.max(max_x, pos.getX());
			max_y = Math.max(max_y, pos.getY());
			max_z = Math.max(max_z, pos.getZ());
		}
		AABB box = new AABB(min_x, min_y - 1, min_z, max_x + 1, max_y + 2, max_z + 1);
		for (AbstractMinecart other : level.getEntitiesOfClass(AbstractMinecart.class, box)) {
			if (other == self || Carts.same_train(other, self)) {
				continue;
			}
			BlockPos at = RailWalk.rail_at(level, other.blockPosition());
			if (at != null && set.contains(at)) {
				return true;
			}
		}
		return false;
	}

	public void tick_switch(ServerLevel level) {
		AbstractMinecart nearest = null;
		double best = Double.MAX_VALUE;
		for (AbstractMinecart cart : level.getEntitiesOfClass(AbstractMinecart.class, new AABB(worldPosition).inflate(6))) {
			if (Carts.follower(cart) || cart.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4) {
				continue;
			}
			double distance = cart.distanceToSqr(Vec3.atCenterOf(worldPosition));
			if (distance < best) {
				best = distance;
				nearest = cart;
			}
		}
		if (nearest == null) {
			return;
		}
		set_powered(level, routes.contains(Carts.destination_of(nearest)));
	}

	public void set_powered(ServerLevel level, boolean value) {
		if (value == powered) {
			return;
		}
		powered = value;
		setChanged();
		level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		light(level, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, value));
		level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, value ? 0.6F : 0.5F);
	}

	private void light(ServerLevel level, BlockState shown) {
		BlockAwareAttachment attachment = BlockAwareAttachment.get(level, worldPosition);
		if (attachment == null) {
			return;
		}
		List<VirtualElement> elements = attachment.holder().getElements();
		if (!elements.isEmpty() && elements.get(elements.size() - 1) instanceof BlockDisplayElement display) {
			display.setBlockState(shown);
		}
	}

	private void tick_station(ServerLevel level) {
		List<BlockPos> rails = rails();
		if (rails.isEmpty()) {
			return;
		}
		for (AbstractMinecart cart : carts_at(rails, 1.5)) {
			if (Carts.follower(cart)) {
				continue;
			}
			if (!name.isEmpty() && name.equals(Carts.destination_of(cart))) {
				cart.setDeltaMovement(Vec3.ZERO);
				Carts.arrived(cart, name);
				level.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8F, 1.2F);
			}
		}
		if (level.getGameTime() % 10 == 0 && (mode.equals("unload") || mode.equals("load"))) {
			Container store = store(level);
			if (store != null) {
				List<AbstractMinecart> carts = new ArrayList<>();
				for (AbstractMinecart cart : carts_at(rails, 1.5)) {
					carts.add(cart);
					carts.addAll(Carts.train_behind(cart));
				}
				for (AbstractMinecart cart : carts) {
					if (cart instanceof Container cargo) {
						if (mode.equals("unload")) {
							move(cargo, store);
						} else {
							move(store, cargo);
						}
					}
				}
			}
		}
		if (mode.equals("dispatch") && level.getGameTime() - last_dispatch >= interval * 1200L) {
			for (AbstractMinecart cart : carts_at(rails, 0.6)) {
				if (Carts.follower(cart) || cart.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3) {
					continue;
				}
				last_dispatch = level.getGameTime();
				setChanged();
				if (!dispatch_to.isEmpty()) {
					cart.setAttached(Carts.destination, dispatch_to);
				}
				cart.setDeltaMovement(dispatch_dir.getStepX() * 0.6, 0, dispatch_dir.getStepZ() * 0.6);
				level.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 0.8F);
				break;
			}
		}
	}

	public Container store(ServerLevel level) {
		for (Direction direction : Direction.values()) {
			if (level.getBlockEntity(worldPosition.relative(direction)) instanceof Container container && !(level.getBlockEntity(worldPosition.relative(direction)) instanceof RailDevice)) {
				return container;
			}
		}
		return null;
	}

	public boolean matches(ItemStack stack) {
		boolean any = false;
		for (ItemStack sample : filter) {
			if (!sample.isEmpty()) {
				any = true;
				if (ItemStack.isSameItem(sample, stack)) {
					return true;
				}
			}
		}
		return !any;
	}

	public int move(Container from, Container to) {
		int moved = 0;
		for (int slot = 0; slot < from.getContainerSize(); slot++) {
			ItemStack stack = from.getItem(slot);
			if (stack.isEmpty() || !matches(stack)) {
				continue;
			}
			for (int target = 0; target < to.getContainerSize() && !stack.isEmpty(); target++) {
				ItemStack there = to.getItem(target);
				if (there.isEmpty()) {
					to.setItem(target, stack.copy());
					moved += stack.getCount();
					stack.setCount(0);
				} else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
					int amount = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
					there.grow(amount);
					stack.shrink(amount);
					moved += amount;
				}
			}
			from.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
		}
		if (moved > 0) {
			from.setChanged();
			to.setChanged();
		}
		return moved;
	}
}
