package dev.kaileh57.deepworld.exploration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.forge.ForgeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;

public record LandmarkFeature(String kind) implements Feature {
	public static final MapCodec<LandmarkFeature> codec = RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.fieldOf("kind").forGetter(LandmarkFeature::kind)).apply(i, LandmarkFeature::new));

	@Override
	public MapCodec<LandmarkFeature> codec() {
		return codec;
	}

	private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 2);
	}

	private static void fill(WorldGenLevel level, BlockPos from, BlockPos to, BlockState state) {
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			set(level, pos, state);
		}
	}

	private static void hollow(WorldGenLevel level, BlockPos origin, int radius, int height, BlockState wall, BlockState floor) {
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -1, -radius), origin.offset(radius, height, radius))) {
			int dx = Math.abs(pos.getX() - origin.getX());
			int dz = Math.abs(pos.getZ() - origin.getZ());
			int dy = pos.getY() - origin.getY();
			if (dy == -1) {
				set(level, pos, floor);
			} else if (dy == height || dx == radius || dz == radius) {
				set(level, pos, wall);
			} else {
				set(level, pos, Blocks.AIR.defaultBlockState());
			}
		}
	}

	private static void chest(WorldGenLevel level, BlockPos pos, String table) {
		set(level, pos, Blocks.CHEST.defaultBlockState());
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, ResourceKey.create(Registries.LOOT_TABLE, DeepWorld.id(table)));
	}

	private static void clear(WorldGenLevel level, BlockPos origin, int radius, int height) {
		fill(level, origin.offset(-radius, 0, -radius), origin.offset(radius, height, radius), Blocks.AIR.defaultBlockState());
	}

	private static final java.util.Set<Long> used_chunks = java.util.concurrent.ConcurrentHashMap.newKeySet();

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		long chunk = net.minecraft.world.level.ChunkPos.pack(origin.getX() >> 4, origin.getZ() >> 4) ^ ((long) kind.hashCode() << 40);
		if (!used_chunks.add(chunk)) {
			return false;
		}
		return build(level, random, origin);
	}

	public boolean build(WorldGenLevel level, RandomSource random, BlockPos origin) {
		BlockPos o = origin;
		switch (kind) {
			case "miners_camp" -> {
				clear(level, o, 5, 4);
				fill(level, o.offset(-5, -1, -5), o.offset(5, -1, 5), Blocks.COARSE_DIRT.defaultBlockState());
				for (int z = -2; z <= 2; z++) {
					set(level, o.offset(-3, 0, z), Blocks.WOOL.white().defaultBlockState());
					set(level, o.offset(-2, 1, z), Blocks.WOOL.white().defaultBlockState());
					set(level, o.offset(-1, 0, z), Blocks.WOOL.white().defaultBlockState());
				}
				set(level, o, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
				set(level, o.offset(3, 0, 2), Blocks.OAK_FENCE.defaultBlockState());
				set(level, o.offset(3, 1, 2), Blocks.LANTERN.defaultBlockState());
				set(level, o.offset(3, 0, -2), Blocks.BARREL.defaultBlockState());
				set(level, o.offset(2, 0, -3), Blocks.CRAFTING_TABLE.defaultBlockState());
				chest(level, o.offset(3, 0, 0), "landmarks/miners_camp");
			}
			case "drowned_pump_station" -> {
				hollow(level, o, 4, 5, Blocks.BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
				set(level, o.offset(0, 0, -4), Blocks.AIR.defaultBlockState());
				set(level, o.offset(0, 1, -4), Blocks.AIR.defaultBlockState());
				set(level, o.offset(4, 2, 0), Blocks.IRON_BARS.defaultBlockState());
				set(level, o.offset(-4, 2, 0), Blocks.IRON_BARS.defaultBlockState());
				set(level, o, Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING, Direction.DOWN));
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					set(level, o.relative(direction), Blocks.COPPER_BLOCK.weathering().exposed().defaultBlockState());
				}
				set(level, o.offset(0, 4, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
				chest(level, o.offset(3, 0, 3), "landmarks/drowned_pump_station");
				Landmarks.record("pump", o);
			}
			case "fossil_dig" -> {
				clear(level, o, 6, 4);
				fill(level, o.offset(-4, -3, -4), o.offset(4, -1, 4), Blocks.AIR.defaultBlockState());
				fill(level, o.offset(-4, -4, -4), o.offset(4, -4, 4), Blocks.GRAVEL.defaultBlockState());
				for (int i = 0; i < 7; i++) {
					BlockPos spot = o.offset(random.nextInt(9) - 4, -4, random.nextInt(9) - 4);
					set(level, spot, Blocks.SUSPICIOUS_GRAVEL.defaultBlockState());
					if (level.getBlockEntity(spot) instanceof BrushableBlockEntity brush) {
						brush.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, DeepWorld.id("archaeology/fossil_dig")), random.nextLong());
					}
				}
				for (int x = -3; x <= 3; x++) {
					set(level, o.offset(x, -3, 0), Blocks.BONE_BLOCK.defaultBlockState());
				}
				for (int y = -3; y <= 0; y++) {
					set(level, o.offset(-5, y, 0), Blocks.SCAFFOLDING.defaultBlockState());
				}
				chest(level, o.offset(5, 0, 3), "landmarks/fossil_dig");
			}
			case "crystal_cathedral" -> {
				hollow(level, o, 6, 7, Blocks.CALCITE.defaultBlockState(), Blocks.SMOOTH_BASALT.defaultBlockState());
				for (int[] c : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
					fill(level, o.offset(c[0], 0, c[1]), o.offset(c[0], 6, c[1]), Blocks.AMETHYST_BLOCK.defaultBlockState());
				}
				int[][] notes = {{-2, -3}, {2, -3}, {-2, 3}, {2, 3}};
				for (int[] n : notes) {
					set(level, o.offset(n[0], 0, n[1]), Blocks.NOTE_BLOCK.defaultBlockState());
					set(level, o.offset(n[0], -1, n[1]), Blocks.AMETHYST_BLOCK.defaultBlockState());
				}
				fill(level, o.offset(-1, 0, 5), o.offset(1, 2, 5), Blocks.BUDDING_AMETHYST.defaultBlockState());
				fill(level, o.offset(-1, 0, 6), o.offset(1, 2, 8), Blocks.AIR.defaultBlockState());
				fill(level, o.offset(-2, -1, 6), o.offset(2, 3, 9), Blocks.CALCITE.defaultBlockState());
				fill(level, o.offset(-1, 0, 6), o.offset(1, 2, 8), Blocks.AIR.defaultBlockState());
				chest(level, o.offset(0, 0, 8), "landmarks/crystal_cathedral");
				set(level, o.offset(0, 0, -5), Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK, true));
				if (level.getBlockEntity(o.offset(0, 0, -5)) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern) {
					lectern.setBook(Exploration.tuning_notes(o));
				}
				Landmarks.record("cathedral", o);
			}
			case "sulfur_works" -> {
				hollow(level, o, 4, 5, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState());
				set(level, o.offset(0, 0, -4), Blocks.AIR.defaultBlockState());
				set(level, o.offset(0, 1, -4), Blocks.AIR.defaultBlockState());
				for (int x = -2; x <= 2; x += 2) {
					set(level, o.offset(x, 0, 2), Blocks.BREWING_STAND.defaultBlockState());
					set(level, o.offset(x, 0, 3), Blocks.CAULDRON.defaultBlockState());
				}
				set(level, o.offset(-3, 0, -2), Blocks.BARREL.defaultBlockState());
				set(level, o.offset(-3, 0, -1), Blocks.SULFUR.defaultBlockState());
				chest(level, o.offset(3, 0, -2), "landmarks/sulfur_works");
			}
			case "tide_ruin" -> {
				if (!level.getBlockState(o).is(Blocks.LAVA)) {
					return false;
				}
				for (int x = -3; x <= 3; x++) {
					for (int z = -3; z <= 3; z++) {
						for (int y = -1; y > -40; y--) {
							BlockPos pos = o.offset(x, y, z);
							BlockState there = level.getBlockState(pos);
							if (y < -1 && !there.isAir() && !there.is(Blocks.LAVA)) {
								break;
							}
							set(level, pos, (x + z) % 3 == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
							if (y < -1 && Math.abs(x) < 3 && Math.abs(z) < 3) {
								break;
							}
						}
						boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
						if (edge && random.nextInt(3) > 0) {
							set(level, o.offset(x, 0, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
							if (random.nextBoolean()) {
								set(level, o.offset(x, 1, z), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
							}
						}
					}
				}
				for (int x = -2; x <= 2; x++) {
					if (random.nextInt(3) > 0) {
						set(level, o.offset(x, 0, 1), Blocks.RAIL.defaultBlockState());
					}
				}
				chest(level, o, "landmarks/tide_ruin");
				Landmarks.record("tide_ruin", o);
			}
			case "bridge_pylons" -> {
				for (int p = 0; p < 4; p++) {
					BlockPos base = o.offset(p * 5 - 7, 0, 0);
					for (int y = -1; y > -40; y--) {
						BlockPos pos = base.offset(0, y, 0);
						BlockState there = level.getBlockState(pos);
						if (!there.isAir() && !there.is(Blocks.LAVA)) {
							break;
						}
						fill(level, pos.offset(-1, 0, -1), pos.offset(1, 0, 1), Blocks.STONE_BRICKS.defaultBlockState());
					}
					fill(level, base.offset(-1, 0, -1), base.offset(1, p == 3 ? 2 : 4, 1), Blocks.STONE_BRICKS.defaultBlockState());
				}
				fill(level, o.offset(-7, 5, -1), o.offset(-2, 5, 1), Blocks.STONE_BRICK_SLAB.defaultBlockState());
				chest(level, o.offset(-6, 6, 0), "landmarks/bridge_pylons");
			}
			case "sculk_shrine" -> {
				clear(level, o, 4, 4);
				fill(level, o.offset(-3, -1, -3), o.offset(3, -1, 3), Blocks.DEEPSLATE_TILES.defaultBlockState());
				for (int i = 0; i < 10; i++) {
					set(level, o.offset(random.nextInt(7) - 3, -1, random.nextInt(7) - 3), Blocks.SCULK.defaultBlockState());
				}
				set(level, o, Blocks.SCULK_SHRIEKER.defaultBlockState());
				set(level, o.offset(2, 0, 2), Blocks.CANDLE.defaultBlockState());
				set(level, o.offset(-2, 0, 2), Blocks.CANDLE.defaultBlockState());
				chest(level, o.offset(0, 0, 3), "landmarks/sculk_shrine");
			}
			case "mantle_forge" -> {
				hollow(level, o, 4, 5, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
				set(level, o.offset(0, 0, -4), Blocks.AIR.defaultBlockState());
				set(level, o.offset(0, 1, -4), Blocks.AIR.defaultBlockState());
				set(level, o.offset(0, 0, 2), Content.blocks.get("hellforge").defaultBlockState().setValue(ForgeBlock.lit, true).setValue(ForgeBlock.facing, Direction.NORTH));
				fill(level, o.offset(-3, -1, 3), o.offset(3, -1, 3), Blocks.LAVA.defaultBlockState());
				fill(level, o.offset(-3, -2, 3), o.offset(3, -2, 3), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
				chest(level, o.offset(-3, 0, 0), "landmarks/mantle_forge");
			}
			case "the_gate" -> {
				clear(level, o, 6, 9);
				fill(level, o.offset(-6, -1, -6), o.offset(6, -1, 6), Blocks.MAGMA_BLOCK.defaultBlockState());
				for (int x = -2; x <= 2; x++) {
					for (int y = 0; y <= 7; y++) {
						boolean frame = Math.abs(x) == 2 || y == 0 || y == 7;
						if (frame && random.nextFloat() < 0.85F) {
							set(level, o.offset(x, y, 0), random.nextFloat() < 0.4F ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
						}
					}
				}
				for (int i = 0; i < 6; i++) {
					set(level, o.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), Blocks.IRON_BARS.defaultBlockState());
				}
				set(level, o.offset(3, 0, 3), Blocks.ANVIL.defaultBlockState());
				chest(level, o.offset(-3, 0, 3), "landmarks/the_gate");
			}
			default -> {
				return false;
			}
		}
		Landmarks.record(kind, o);
		return true;
	}
}
