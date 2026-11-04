package dev.kaileh57.deepworld.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

public final class ColumnFluids {
	public static final int deep_line = 1100;
	public static final int nether_lava_level = -224;
	public static final int lava_sea_floor = 450;
	public static final int lava_sea_level = 490;
	public static final int crust_river_level = 186;
	public static final int hellmouth_river_level = 41;
	public static final int flood_floor = 900;
	public static final int flood_level = 936;

	private static final BlockState air = Blocks.AIR.defaultBlockState();
	private static final BlockState lava = Blocks.LAVA.defaultBlockState();
	private static final BlockState water = Blocks.WATER.defaultBlockState();
	private static final SimplexNoise flood_noise = new SimplexNoise(new LegacyRandomSource(7741));

	private ColumnFluids() {
	}

	public static BlockState fluid_at(int x, int y, int z) {
		if (y < nether_lava_level) {
			return lava;
		}
		if (y >= lava_sea_floor && y < lava_sea_level) {
			return lava;
		}
		if (y >= 150 && y < crust_river_level) {
			return lava;
		}
		if (y >= 0 && y < hellmouth_river_level) {
			return lava;
		}
		if (y >= flood_floor && y < flood_level && flood_noise.get(x / 160.0, z / 160.0) > 0.35) {
			return water;
		}
		return air;
	}
}
