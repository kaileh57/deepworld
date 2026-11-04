package dev.kaileh57.deepworld.transit;

import com.mojang.serialization.MapCodec;
import dev.kaileh57.deepworld.DeepWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.loot.LootTable;

public class DeeplineFeature implements Feature {
	public static final MapCodec<DeeplineFeature> codec = MapCodec.unit(DeeplineFeature::new);
	public static final int line_y = 1212;
	public static final int terminal_half = 15;
	public static final int station_half = 9;
	public static final ResourceKey<LootTable> depot_loot = ResourceKey.create(Registries.LOOT_TABLE, DeepWorld.id("chests/depot"));
	public static final ResourceKey<LootTable> camp_loot = ResourceKey.create(Registries.LOOT_TABLE, DeepWorld.id("chests/camp"));
	public static final ResourceKey<LootTable> train_loot = ResourceKey.create(Registries.LOOT_TABLE, DeepWorld.id("chests/derelict_train"));

	private record Line(int dx, int dz, int length, String end) {
	}

	private static final Line[] lines = {
		new Line(1, 0, 250, "depot"),
		new Line(-1, 0, 450, "camp"),
		new Line(0, 1, 1000, "station"),
		new Line(0, -1, 2000, "station")
	};

	@Override
	public MapCodec<DeeplineFeature> codec() {
		return codec;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		if (!near_any(x0, z0)) {
			return false;
		}
		for (int x = x0; x < x0 + 16; x++) {
			for (int z = z0; z < z0 + 16; z++) {
				column(level, random, x, z);
			}
		}
		return true;
	}

	private static boolean near_any(int x0, int z0) {
		if (Math.abs(x0) <= 48 && Math.abs(z0) <= 48) {
			return true;
		}
		for (Line line : lines) {
			int lateral_min = line.dx() != 0 ? z0 : x0;
			int along_min = line.dx() != 0 ? x0 * line.dx() : z0 * line.dz();
			int along_max = line.dx() != 0 ? (x0 + 15) * line.dx() : (z0 + 15) * line.dz();
			int low = Math.min(along_min, along_max);
			int high = Math.max(along_min, along_max);
			if (lateral_min <= 12 && lateral_min + 15 >= -12 && high >= terminal_half && low <= line.length() + 12) {
				return true;
			}
		}
		return false;
	}

	private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 2);
	}

	private static long hash(long a, long b, long c) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0x94D049BB133111EBL;
		return h ^ (h >>> 29);
	}

	private static double chance(long a, long b, long c) {
		return (hash(a, b, c) >>> 11) / (double) (1L << 53);
	}

	private void column(WorldGenLevel level, RandomSource random, int x, int z) {
		if (Math.abs(x) <= terminal_half && Math.abs(z) <= terminal_half) {
			station(level, x, z, 0, 0, terminal_half, true);
			return;
		}
		for (int index = 0; index < lines.length; index++) {
			Line line = lines[index];
			int along = line.dx() != 0 ? x * line.dx() : z * line.dz();
			int lateral = line.dx() != 0 ? z : x;
			if (along <= terminal_half || along > line.length() + 12) {
				continue;
			}
			int end_x = line.dx() * (line.length() + 3);
			int end_z = line.dz() * (line.length() + 3);
			if (line.end().equals("station") && Math.abs(x - end_x) <= station_half && Math.abs(z - end_z) <= station_half) {
				station(level, x, z, end_x, end_z, station_half, line.length() >= 2000);
				return;
			}
			if (line.end().equals("depot") && Math.abs(x - end_x) <= 6 && Math.abs(z - end_z) <= 6) {
				depot(level, x, z, end_x, end_z);
				return;
			}
			if (line.end().equals("camp") && Math.abs(x - end_x) <= 7 && Math.abs(z - end_z) <= 7) {
				camp(level, x, z, end_x, end_z);
				return;
			}
			if (along > line.length()) {
				continue;
			}
			int cabin_offset = along % 500;
			if (along > 100 && cabin_offset >= 247 && cabin_offset <= 253 && lateral >= 4 && lateral <= 9) {
				cabin(level, x, z, cabin_offset - 247, lateral - 4, line);
				continue;
			}
			if (Math.abs(lateral) <= 3) {
				track(level, x, z, along, lateral, index, line);
				return;
			}
		}
	}

	private void track(WorldGenLevel level, int x, int z, int along, int lateral, int index, Line line) {
		int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
		boolean tunnel = surface > line_y + 2;
		int edge = Math.abs(lateral);
		boolean gap = along % 500 < 5 && along > 20;
		int segment = along / 150;
		boolean collapse_segment = chance(index, segment, 11) < 0.3;
		int collapse_start = segment * 150 + 20;
		int collapse_length = 40 + (int) (chance(index, segment, 13) * 80);
		boolean collapsed = collapse_segment && along >= collapse_start && along < collapse_start + collapse_length;
		for (int y = line_y; y <= line_y + 4; y++) {
			if (edge <= 2 && y <= line_y + 3) {
				set(level, x, y, z, Blocks.AIR.defaultBlockState());
			}
		}
		if (tunnel) {
			if (edge == 3) {
				for (int y = line_y; y <= line_y + 3; y++) {
					set(level, x, y, z, chance(x, y, z) < 0.15 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
			set(level, x, line_y + 4, z, chance(x, z, 3) < 0.2 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
			if (edge == 0 && along % 24 == 0) {
				set(level, x, line_y + 3, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			}
		} else if (edge == 3) {
			return;
		}
		if (edge <= 2 && !(gap && ground < line_y - 2)) {
			BlockState bed = edge == 2 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
			if (edge <= 1 && ground < line_y - 1) {
				bed = Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
			}
			set(level, x, line_y - 1, z, bed);
			if (edge == 2 && ground < line_y - 1 && along % 12 == 0) {
				for (int y = line_y - 2; y >= Math.max(ground, line_y - 90); y--) {
					set(level, x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
			if (edge == 2 && !tunnel && ground < line_y - 3) {
				set(level, x, line_y, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
			}
		}
		if (collapsed && edge <= 2) {
			int height = 1 + (int) (chance(x, z, 17) * (3 - edge * 0.6));
			for (int y = line_y; y < line_y + height; y++) {
				set(level, x, y, z, chance(x, y, z) < 0.5 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
			}
			return;
		}
		if (edge == 0 && !gap && chance(x, z, 7) < 0.62) {
			RailShape shape = line.dx() != 0 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
			set(level, x, line_y, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape));
		}
	}

	private void station(WorldGenLevel level, int x, int z, int center_x, int center_z, int half, boolean with_train) {
		int dx = x - center_x;
		int dz = z - center_z;
		int edge = Math.max(Math.abs(dx), Math.abs(dz));
		int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		for (int y = line_y - 2; y >= Math.max(ground, line_y - 40); y--) {
			set(level, x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
		}
		boolean axis = Math.abs(dx) <= 0 || Math.abs(dz) <= 0;
		set(level, x, line_y - 1, z, axis && edge > 1 ? Blocks.GRAVEL.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
		for (int y = line_y; y <= line_y + 6; y++) {
			set(level, x, y, z, Blocks.AIR.defaultBlockState());
		}
		boolean exit = Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
		if (edge == half) {
			if (!exit) {
				for (int y = line_y; y <= line_y + 5; y++) {
					set(level, x, y, z, chance(x, y, z) < 0.12 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
				}
			} else {
				set(level, x, line_y + 4, z, Blocks.STONE_BRICKS.defaultBlockState());
				set(level, x, line_y + 5, z, Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4) {
			set(level, x, line_y + 6, z, chance(x, z, 5) < 0.2 ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState());
		} else {
			set(level, x, line_y + 6, z, chance(x, z, 9) < 0.06 ? Blocks.AIR.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
		}
		int pillar = half / 2 + 1;
		if (Math.abs(dx) == pillar && Math.abs(dz) == pillar) {
			for (int y = line_y; y <= line_y + 5; y++) {
				set(level, x, y, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
			}
		}
		if ((Math.abs(dx) == pillar && dz == 0) || (Math.abs(dz) == pillar && dx == 0)) {
			set(level, x, line_y + 5, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		if (axis && edge > 1 && edge < half) {
			RailShape shape = dz == 0 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
			if (chance(x, z, 21) < 0.8) {
				set(level, x, line_y, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape));
			}
		}
		if (Math.abs(dx) == half - 2 && Math.abs(dz) >= 5 && Math.abs(dz) <= 8) {
			Direction facing = dx > 0 ? Direction.WEST : Direction.EAST;
			set(level, x, line_y, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, facing));
		}
		if (dx == 5 && dz == 5) {
			set(level, x, line_y, z, Blocks.LECTERN.defaultBlockState());
		}
		if (dx == -5 && dz == 5) {
			BlockPos chest = new BlockPos(x, line_y, z);
			level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 2);
			RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest, depot_loot);
		}
		if (with_train && dx == 0 && (dz == -6 || dz == -8 || dz == -10)) {
			MinecartChest cart = net.minecraft.world.entity.EntityTypes.CHEST_MINECART.create(level.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
			if (cart != null) {
				cart.setPos(x + 0.5, line_y + 0.1, z + 0.5);
				cart.setLootTable(train_loot, hash(x, z, 23));
				level.addFreshEntity(cart);
			}
		}
	}

	private void depot(WorldGenLevel level, int x, int z, int cx, int cz) {
		int dx = x - cx;
		int dz = z - cz;
		int edge = Math.max(Math.abs(dx), Math.abs(dz));
		int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		for (int y = line_y - 2; y >= Math.max(ground, line_y - 30); y--) {
			set(level, x, y, z, Blocks.COBBLESTONE.defaultBlockState());
		}
		set(level, x, line_y - 1, z, Blocks.STONE_BRICKS.defaultBlockState());
		for (int y = line_y; y <= line_y + 4; y++) {
			set(level, x, y, z, Blocks.AIR.defaultBlockState());
		}
		if (edge == 5) {
			int height = (int) (chance(x, z, 31) * 4);
			for (int y = line_y; y < line_y + height; y++) {
				set(level, x, y, z, chance(x, y, z) < 0.3 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		if (dx == 0 && dz == 0) {
			BlockPos chest = new BlockPos(x, line_y, z);
			level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
			RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest, depot_loot);
		}
		if (dx == 2 && dz == -2) {
			set(level, x, line_y, z, Blocks.BARREL.defaultBlockState());
		}
	}

	private void camp(WorldGenLevel level, int x, int z, int cx, int cz) {
		int dx = x - cx;
		int dz = z - cz;
		int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
		int floor = Math.min(surface, line_y);
		for (int y = floor; y <= line_y + 4; y++) {
			set(level, x, y, z, Blocks.AIR.defaultBlockState());
		}
		for (int y = line_y - 1; y >= floor - 1 && y > line_y - 30; y--) {
			set(level, x, y, z, Blocks.DIRT.defaultBlockState());
		}
		set(level, x, line_y - 1, z, Blocks.COARSE_DIRT.defaultBlockState());
		if (dx == 0 && dz == 0) {
			set(level, x, line_y, z, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
		}
		int[][] tents = {{-4, -4}, {4, -4}, {0, 5}};
		for (int[] tent : tents) {
			int tx = dx - tent[0];
			int tz = dz - tent[1];
			if (Math.abs(tx) <= 1 && Math.abs(tz) <= 2) {
				int height = 2 - Math.abs(tx);
				set(level, x, line_y + height, z, Blocks.WOOL.white().defaultBlockState());
				if (tx == 0 && tz == 2) {
					set(level, x, line_y, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
		if (dx == 2 && dz == 2) {
			BlockPos chest = new BlockPos(x, line_y, z);
			level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
			RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest, camp_loot);
		}
		if (dx == -2 && dz == 2) {
			set(level, x, line_y, z, Blocks.BARREL.defaultBlockState());
		}
	}

	private void cabin(WorldGenLevel level, int x, int z, int along, int across, Line line) {
		boolean wall = along == 0 || along == 6 || across == 0 || across == 5;
		set(level, x, line_y - 1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
		for (int y = line_y; y <= line_y + 2; y++) {
			set(level, x, y, z, wall ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		if (wall && across == 0 && along == 3) {
			set(level, x, line_y, z, Blocks.AIR.defaultBlockState());
			set(level, x, line_y + 1, z, Blocks.AIR.defaultBlockState());
		}
		if (wall && across == 5 && along == 3) {
			set(level, x, line_y + 1, z, Blocks.GLASS_PANE.defaultBlockState());
		}
		set(level, x, line_y + 3, z, Blocks.SPRUCE_SLAB.defaultBlockState());
		if (along == 3 && across == 3) {
			set(level, x, line_y, z, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
			set(level, x, line_y + 2, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		for (int y = line_y - 2; y >= Math.max(ground, line_y - 20); y--) {
			set(level, x, y, z, Blocks.COBBLESTONE.defaultBlockState());
		}
	}
}
