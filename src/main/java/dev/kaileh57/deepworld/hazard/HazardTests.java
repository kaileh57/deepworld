package dev.kaileh57.deepworld.hazard;

import dev.kaileh57.deepworld.command.SelfTest;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class HazardTests {
	private HazardTests() {
	}

	private static void room(ServerLevel level, BlockPos center) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -1, -6), center.offset(6, 5, 6))) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-5, 0, -5), center.offset(5, 2, 5))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
	}

	private static void clear_room(ServerLevel level, BlockPos center) {
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -1, -6), center.offset(6, 8, 6))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(center).inflate(10)).forEach(FallingBlockEntity::discard);
	}

	public static void register() {
		SelfTest.add("wide unsupported ceilings cave in", server -> {
			ServerLevel level = server.overworld();
			BlockPos center = new BlockPos(8, 1440, 8);
			room(level, center);
			int width = Hazards.span(level, center);
			boolean supported_before = Hazards.supported(level, center);
			int dropped = Hazards.collapse(level, center);
			clear_room(level, center);
			room(level, center);
			level.setBlock(center.offset(2, 0, 2), Blocks.OAK_LOG.defaultBlockState(), 2);
			level.setBlock(center.offset(2, 1, 2), Blocks.OAK_LOG.defaultBlockState(), 2);
			boolean supported_after = Hazards.supported(level, center);
			int shored = Hazards.maybe_cave_in(level, center, 1.0F);
			clear_room(level, center);
			if (width < 5 || supported_before || dropped == 0) {
				return "width " + width + " supported " + supported_before + " dropped " + dropped;
			}
			return supported_after && shored == 0 ? null : "logs did not shore the ceiling";
		});
		SelfTest.add("vents clear the air", server -> {
			ServerLevel level = server.overworld();
			BlockPos spot = new BlockPos(-8, 1450, -8);
			boolean before = Hazards.vented(level, spot);
			level.setBlock(spot.offset(4, 0, 0), Content.blocks.get("vent").defaultBlockState(), 2);
			boolean after = Hazards.vented(level, spot);
			level.setBlock(spot.offset(4, 0, 0), Blocks.AIR.defaultBlockState(), 2);
			return !before && after ? null : "before " + before + " after " + after;
		});
		SelfTest.add("magma tide rises and falls", server -> {
			ServerLevel level = server.overworld();
			BlockPos base = new BlockPos(4, 0, 4);
			for (int y = Hazards.tide_base - 8; y <= Hazards.tide_base + 8; y++) {
				level.setBlock(base.atY(y), y < Hazards.tide_base ? Blocks.LAVA.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
			}
			Hazards.apply_tide(level, base.atY(Hazards.tide_base), Hazards.tide_base + 4);
			boolean high = level.getBlockState(base.atY(Hazards.tide_base + 2)).is(Blocks.LAVA);
			Hazards.apply_tide(level, base.atY(Hazards.tide_base), Hazards.tide_base - 4);
			boolean low = level.getBlockState(base.atY(Hazards.tide_base - 1)).isAir();
			Hazards.apply_tide(level, base.atY(Hazards.tide_base), Hazards.tide_base);
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
			for (long t = 0; t < Hazards.tide_period; t += 1000) {
				min = Math.min(min, Hazards.tide_level(t));
				max = Math.max(max, Hazards.tide_level(t));
			}
			return high && low && max - min == 2 * Hazards.tide_amplitude ? null : "high " + high + " low " + low + " range " + min + ".." + max;
		});
		SelfTest.add("wool-wrapped boots hush", server -> {
			FakePlayer player = FakePlayer.get(server.overworld());
			ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
			var tag = CookData.tag(boots);
			tag.putBoolean("hushed", true);
			CookData.set_tag(boots, tag);
			player.setItemSlot(EquipmentSlot.FEET, boots);
			boolean hushed = Hazards.hushed(player);
			player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			boolean plain = Hazards.hushed(player);
			return hushed && !plain ? null : "hushed " + hushed + " plain " + plain;
		});
	}
}
