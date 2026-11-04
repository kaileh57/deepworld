package dev.kaileh57.deepworld.transit;

import dev.kaileh57.deepworld.command.SelfTest;
import dev.kaileh57.deepworld.content.Content;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public final class TransitTests {
	private TransitTests() {
	}

	private static double run_cart(ServerLevel level, int z, net.minecraft.world.level.block.Block bed) {
		int y = 1480;
		for (int x = 0; x < 120; x++) {
			level.setBlockAndUpdate(new BlockPos(x, y - 1, z), bed.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
		}
		Minecart cart = net.minecraft.world.entity.EntityTypes.MINECART.create(level, EntitySpawnReason.COMMAND);
		cart.setPos(2.5, y + 0.0625, z + 0.5);
		level.addFreshEntity(cart);
		cart.setDeltaMovement(new Vec3(0.4, 0, 0));
		for (int tick = 0; tick < 60; tick++) {
			cart.tick();
		}
		double travelled = cart.getX() - 2.5;
		cart.discard();
		for (int x = 0; x < 120; x++) {
			level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(x, y - 1, z), Blocks.AIR.defaultBlockState());
		}
		return travelled;
	}

	public static void register() {
		SelfTest.add("gold rail bed is faster than stone", server -> {
			ServerLevel level = server.overworld();
			double stone = run_cart(level, 40, Blocks.STONE);
			double gold = run_cart(level, 44, Blocks.GOLD_BLOCK);
			return gold > stone * 1.8 ? null : "stone " + Math.round(stone) + " gold " + Math.round(gold);
		});
		SelfTest.add("lift finds stations and checks the shaft", server -> {
			ServerLevel level = server.overworld();
			BlockPos low = new BlockPos(30, 1440, 30);
			BlockPos high = new BlockPos(30, 1490, 30);
			level.setBlockAndUpdate(low, Content.blocks.get("lift_station").defaultBlockState());
			level.setBlockAndUpdate(high, Content.blocks.get("lift_station").defaultBlockState());
			int count = Lifts.stations(level, low).size();
			boolean open = Lifts.clear(level, low, high);
			level.setBlockAndUpdate(new BlockPos(30, 1460, 30), Blocks.STONE.defaultBlockState());
			boolean blocked = !Lifts.clear(level, low, high);
			level.setBlockAndUpdate(new BlockPos(30, 1460, 30), Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(low, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(high, Blocks.AIR.defaultBlockState());
			if (count != 2) {
				return count + " stations found";
			}
			return open && blocked ? null : "open " + open + " blocked " + blocked;
		});
		SelfTest.add("deepline terminal and east line generate", server -> {
			ServerLevel level = server.overworld();
			int y = DeeplineFeature.line_y - 1;
			var floor = level.getBlockState(new BlockPos(3, y, 3));
			var bed_one = level.getBlockState(new BlockPos(120, y, 2));
			var bed_two = level.getBlockState(new BlockPos(121, y, -2));
			if (!floor.is(Blocks.POLISHED_ANDESITE)) {
				return "terminal floor is " + floor;
			}
			return bed_one.is(Blocks.STONE_BRICKS) || bed_two.is(Blocks.STONE_BRICKS) ? null : "east line bed is " + bed_one + " " + bed_two;
		});
	}
}
