package dev.kaileh57.deepworld.transit;

import dev.kaileh57.deepworld.command.SelfTest;
import dev.kaileh57.deepworld.content.Content;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

public final class TrainTests {
	private static final BlockPos base = new BlockPos(0, 1520, 4);

	private TrainTests() {
	}

	private static List<BlockPos> track(ServerLevel level, BlockPos start, int length) {
		List<BlockPos> placed = new ArrayList<>();
		for (int x = 0; x < length; x++) {
			BlockPos pos = start.offset(x, 0, 0);
			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(pos, Blocks.RAIL.defaultBlockState());
			placed.add(pos);
			placed.add(pos.below());
		}
		return placed;
	}

	private static void clear(ServerLevel level, List<BlockPos> blocks, List<? extends AbstractMinecart> carts) {
		for (AbstractMinecart cart : carts) {
			cart.discard();
		}
		for (int i = blocks.size() - 1; i >= 0; i--) {
			level.setBlockAndUpdate(blocks.get(i), Blocks.AIR.defaultBlockState());
		}
	}

	private static <T extends AbstractMinecart> T cart(ServerLevel level, net.minecraft.world.entity.EntityType<T> type, BlockPos at) {
		T cart = type.create(level, EntitySpawnReason.COMMAND);
		cart.setPos(at.getX() + 0.5, at.getY() + 0.0625, at.getZ() + 0.5);
		level.addFreshEntity(cart);
		return cart;
	}

	private static RailDevice device(ServerLevel level, BlockPos pos, String id, List<BlockPos> blocks) {
		level.setBlockAndUpdate(pos, Content.blocks.get(id).defaultBlockState());
		blocks.add(pos);
		return (RailDevice) level.getBlockEntity(pos);
	}

	public static void register() {
		SelfTest.add("coupled carts follow the lead", server -> {
			ServerLevel level = server.overworld();
			List<BlockPos> blocks = track(level, base, 16);
			AbstractMinecart front = cart(level, EntityTypes.MINECART, base.offset(5, 0, 0));
			AbstractMinecart back = cart(level, EntityTypes.MINECART, base.offset(3, 0, 0));
			String result = Carts.couple(front, back);
			for (int step = 0; step < 6; step++) {
				front.setPos(front.getX() + 0.5, front.getY(), front.getZ());
				Carts.tick(server);
			}
			double gap = front.distanceTo(back);
			boolean behind = back.getX() < front.getX();
			clear(level, blocks, List.of(front, back));
			return result.equals("coupled") && Math.abs(gap - Carts.spacing) < 0.2 && behind ? null : result + " gap " + gap + " behind " + behind;
		});
		SelfTest.add("signals hold carts while the block is busy", server -> {
			ServerLevel level = server.overworld();
			List<BlockPos> blocks = track(level, base, 16);
			RailDevice signal = device(level, base.offset(2, 0, -1), "rail_signal", blocks);
			AbstractMinecart mover = cart(level, EntityTypes.MINECART, base.offset(2, 0, 0));
			AbstractMinecart blocker = cart(level, EntityTypes.MINECART, base.offset(9, 0, 0));
			mover.setDeltaMovement(0.4, 0, 0);
			signal.server_tick(level, signal.getBlockPos());
			boolean held = mover.getDeltaMovement().horizontalDistance() < 0.01;
			blocker.discard();
			signal.server_tick(level, signal.getBlockPos());
			boolean released = mover.getDeltaMovement().x > 0.1;
			clear(level, blocks, List.of(mover));
			return held && released ? null : "held " + held + " released " + released;
		});
		SelfTest.add("switch boxes route by ticket", server -> {
			ServerLevel level = server.overworld();
			List<BlockPos> blocks = track(level, base, 7);
			BlockPos junction = base.offset(3, 0, 0);
			for (int z = 1; z <= 3; z++) {
				level.setBlockAndUpdate(junction.offset(0, -1, z), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(junction.offset(0, 0, z), Blocks.RAIL.defaultBlockState());
				blocks.add(junction.offset(0, -1, z));
				blocks.add(junction.offset(0, 0, z));
			}
			level.setBlockAndUpdate(junction, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(junction, Blocks.RAIL.defaultBlockState());
			RailDevice box = device(level, junction.offset(0, 0, -1), "switch_box", blocks);
			box.routes.add("selftest_branch");
			var before = RailWalk.shape(level.getBlockState(junction));
			AbstractMinecart rider = cart(level, EntityTypes.MINECART, base.offset(1, 0, 0));
			rider.setAttached(Carts.destination, "selftest_branch");
			rider.setDeltaMovement(0.4, 0, 0);
			box.tick_switch(level);
			var after = RailWalk.shape(level.getBlockState(junction));
			boolean powered = box.powered;
			clear(level, blocks, List.of(rider));
			return powered && before != after ? null : "powered " + powered + " shape " + before + " -> " + after;
		});
		SelfTest.add("stations stop carts and unload cargo", server -> {
			ServerLevel level = server.overworld();
			List<BlockPos> blocks = track(level, base, 8);
			RailDevice station = device(level, base.offset(3, 0, -1), "rail_station", blocks);
			station.name = "selftest_stop";
			station.mode = "unload";
			BlockPos chest_pos = base.offset(3, 0, -2);
			level.setBlockAndUpdate(chest_pos, Blocks.CHEST.defaultBlockState());
			blocks.add(chest_pos);
			MinecartChest cargo = cart(level, EntityTypes.CHEST_MINECART, base.offset(3, 0, 0));
			cargo.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
			cargo.setAttached(Carts.destination, "selftest_stop");
			cargo.setDeltaMovement(0.4, 0, 0);
			for (int t = 0; t < 10; t++) {
				station.server_tick(level, station.getBlockPos());
			}
			int moved = -1;
			if (level.getBlockEntity(chest_pos) instanceof ChestBlockEntity chest) {
				if (chest.countItem(Items.COBBLESTONE) == 0) {
					station.move(cargo, chest);
				}
				moved = chest.countItem(Items.COBBLESTONE);
			}
			boolean stopped = cargo.getDeltaMovement().horizontalDistance() < 0.01 && cargo.getAttached(Carts.destination) == null;
			if (level.getBlockEntity(chest_pos) instanceof ChestBlockEntity chest) {
				chest.clearContent();
			}
			cargo.clearContent();
			clear(level, blocks, List.of(cargo));
			return stopped && moved == 32 ? null : "stopped " + stopped + " moved " + moved;
		});
		SelfTest.add("dispatch sends a waiting cart", server -> {
			ServerLevel level = server.overworld();
			List<BlockPos> blocks = track(level, base, 6);
			RailDevice station = device(level, base.offset(2, 0, -1), "rail_station", blocks);
			station.mode = "dispatch";
			station.dispatch_to = "selftest_far";
			station.dispatch_dir = net.minecraft.core.Direction.EAST;
			station.last_dispatch = -100000;
			AbstractMinecart waiting = cart(level, EntityTypes.MINECART, base.offset(2, 0, 0));
			waiting.setDeltaMovement(Vec3.ZERO);
			station.server_tick(level, station.getBlockPos());
			boolean sent = waiting.getDeltaMovement().x > 0.1 && "selftest_far".equals(waiting.getAttached(Carts.destination));
			clear(level, blocks, List.of(waiting));
			return sent ? null : "velocity " + waiting.getDeltaMovement() + " dest " + waiting.getAttached(Carts.destination);
		});
		SelfTest.add("lift cable limits the reach", server -> {
			LiftNetwork network = LiftNetwork.get(server);
			BlockPos top = new BlockPos(9999, 1200, 9999);
			BlockPos low = new BlockPos(9999, 1050, 9999);
			boolean bare = network.reaches(top, low);
			network.add_cable(top, 3);
			boolean cabled = network.reaches(top, low);
			network.cable.remove(LiftNetwork.column(top));
			network.setDirty();
			return !bare && cabled ? null : "bare " + bare + " cabled " + cabled;
		});
	}
}
