package dev.kaileh57.deepworld.transit;

import dev.kaileh57.deepworld.DeepWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RailBeds {
	public static final TagKey<Block> copper = TagKey.create(Registries.BLOCK, DeepWorld.id("rail_bed_copper"));
	public static final TagKey<Block> gold = TagKey.create(Registries.BLOCK, DeepWorld.id("rail_bed_gold"));
	public static final TagKey<Block> cobalt = TagKey.create(Registries.BLOCK, DeepWorld.id("rail_bed_cobalt"));
	public static final double vanilla_speed = 0.4;

	private RailBeds() {
	}

	public static double speed_for(Level level, BlockPos rail_pos) {
		BlockState bed = level.getBlockState(rail_pos.below());
		if (bed.is(cobalt)) {
			return 2.4;
		}
		if (bed.is(gold)) {
			return 1.6;
		}
		if (bed.is(copper)) {
			return 0.8;
		}
		return vanilla_speed;
	}
}
