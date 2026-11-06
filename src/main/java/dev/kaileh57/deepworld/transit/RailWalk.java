package dev.kaileh57.deepworld.transit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public final class RailWalk {
	private RailWalk() {
	}

	public static boolean is_rail(BlockState state) {
		return state.getBlock() instanceof BaseRailBlock;
	}

	public static BlockPos rail_at(Level level, BlockPos pos) {
		if (is_rail(level.getBlockState(pos))) {
			return pos;
		}
		if (is_rail(level.getBlockState(pos.below()))) {
			return pos.below();
		}
		return null;
	}

	public static RailShape shape(BlockState state) {
		return state.getValue(((BaseRailBlock) state.getBlock()).getShapeProperty());
	}

	public static Direction[] exits(RailShape shape) {
		return switch (shape) {
			case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new Direction[] {Direction.NORTH, Direction.SOUTH};
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new Direction[] {Direction.EAST, Direction.WEST};
			case SOUTH_EAST -> new Direction[] {Direction.SOUTH, Direction.EAST};
			case SOUTH_WEST -> new Direction[] {Direction.SOUTH, Direction.WEST};
			case NORTH_WEST -> new Direction[] {Direction.NORTH, Direction.WEST};
			case NORTH_EAST -> new Direction[] {Direction.NORTH, Direction.EAST};
		};
	}

	public static Direction rises_toward(RailShape shape) {
		return switch (shape) {
			case ASCENDING_EAST -> Direction.EAST;
			case ASCENDING_WEST -> Direction.WEST;
			case ASCENDING_NORTH -> Direction.NORTH;
			case ASCENDING_SOUTH -> Direction.SOUTH;
			default -> null;
		};
	}

	public static Direction heading(Vec3 motion) {
		if (motion.horizontalDistanceSqr() < 1.0E-4) {
			return null;
		}
		if (Math.abs(motion.x) > Math.abs(motion.z)) {
			return motion.x > 0 ? Direction.EAST : Direction.WEST;
		}
		return motion.z > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	public static Direction heading(AbstractMinecart cart) {
		return heading(cart.getDeltaMovement());
	}

	public static List<BlockPos> ahead(Level level, BlockPos start, Direction heading, int max, Predicate<BlockPos> stop) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos pos = start;
		Direction going = heading;
		for (int step = 0; step < max; step++) {
			BlockState state = level.getBlockState(pos);
			if (!is_rail(state)) {
				break;
			}
			RailShape shape = shape(state);
			Direction[] options = exits(shape);
			Direction exit = options[0] == going.getOpposite() ? options[1] : options[1] == going.getOpposite() ? options[0] : options[0] == going ? options[0] : options[1] == going ? options[1] : null;
			if (exit == null) {
				break;
			}
			BlockPos next = pos.relative(exit);
			if (exit == rises_toward(shape)) {
				next = next.above();
			}
			BlockPos rail = rail_at(level, next);
			if (rail == null) {
				break;
			}
			out.add(rail);
			if (stop.test(rail)) {
				break;
			}
			pos = rail;
			going = exit;
		}
		return out;
	}
}
