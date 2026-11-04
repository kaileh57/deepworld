package dev.kaileh57.deepworld.exploration;

import dev.kaileh57.deepworld.command.SelfTest;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;

public final class ExplorationTests {
	private static final String[] kinds = {"miners_camp", "drowned_pump_station", "fossil_dig", "crystal_cathedral", "sulfur_works", "bridge_pylons", "sculk_shrine", "mantle_forge", "the_gate"};

	private ExplorationTests() {
	}

	private static void wipe(ServerLevel level, BlockPos center, int radius, int down, int up) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -down, -radius), center.offset(radius, up, radius))) {
			if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
				container.clearContent();
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
	}

	public static void register() {
		SelfTest.add("every landmark builder places a chest", server -> {
			ServerLevel level = server.overworld();
			StringBuilder missing = new StringBuilder();
			for (int i = 0; i < kinds.length; i++) {
				BlockPos origin = new BlockPos(-40 + (i % 3) * 20, 1480, -40 + (i / 3) * 20);
				wipe(level, origin, 9, 6, 10);
				level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 2);
				new LandmarkFeature(kinds[i]).build(level, level.getRandom(), origin);
				boolean chest = false;
				for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -5, -8), origin.offset(8, 9, 8))) {
					if (level.getBlockState(pos).is(Blocks.CHEST)) {
						chest = true;
						break;
					}
				}
				wipe(level, origin, 9, 6, 10);
				if (!chest) {
					missing.append(kinds[i]).append(' ');
				}
			}
			return missing.isEmpty() ? null : "no chest: " + missing;
		});
		SelfTest.add("cathedral notes open the vault", server -> {
			ServerLevel level = server.overworld();
			BlockPos origin = new BlockPos(40, 1480, -40);
			wipe(level, origin, 9, 2, 9);
			level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 2);
			new LandmarkFeature("crystal_cathedral").build(level, level.getRandom(), origin);
			int[] targets = Exploration.cathedral_notes(origin);
			int[][] spots = {{-2, -3}, {2, -3}, {-2, 3}, {2, 3}};
			for (int i = 0; i < 4; i++) {
				BlockPos pos = origin.offset(spots[i][0], 0, spots[i][1]);
				level.setBlock(pos, level.getBlockState(pos).setValue(NoteBlock.NOTE, targets[i]), 2);
			}
			boolean closed = !level.getBlockState(origin.offset(0, 1, 5)).isAir();
			Exploration.check_cathedral(level, FakePlayer.get(level), origin);
			boolean open = level.getBlockState(origin.offset(0, 1, 5)).isAir();
			wipe(level, origin, 9, 2, 9);
			return closed && open ? null : "closed before " + closed + ", open after " + open;
		});
		SelfTest.add("repaired pump drains water", server -> {
			ServerLevel level = server.overworld();
			BlockPos center = new BlockPos(-40, 1460, 40);
			for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 2, 3))) {
				level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
			}
			int drained = Exploration.drain(level, center, 6);
			boolean dry = level.getBlockState(center).isAir();
			return drained >= 100 && dry ? null : "drained " + drained;
		});
	}
}
