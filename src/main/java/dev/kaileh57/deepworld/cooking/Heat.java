package dev.kaileh57.deepworld.cooking;

import dev.kaileh57.deepworld.forge.ForgeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Heat {
	public record Source(int level, boolean soul, String name) {
	}

	private Heat() {
	}

	public static Source below(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos.below());
		if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(CampfireBlock.LIT) || state.is(Blocks.SOUL_FIRE)) {
			return new Source(1, true, "soul fire");
		}
		if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT) || state.is(Blocks.FIRE)) {
			return new Source(1, false, "campfire");
		}
		if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) && state.getValue(AbstractFurnaceBlock.LIT) || state.is(Blocks.COAL_BLOCK)) {
			return new Source(2, false, "coal");
		}
		if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.LAVA)) {
			return new Source(3, false, "magma");
		}
		if (state.is(Blocks.ANCIENT_DEBRIS) || state.getBlock() instanceof ForgeBlock && state.getValue(ForgeBlock.lit)) {
			return new Source(4, false, "blaze");
		}
		return new Source(0, false, "none");
	}

	public static String name(int level) {
		return switch (level) {
			case 1 -> "low";
			case 2 -> "medium";
			case 3 -> "high";
			case 4 -> "extreme";
			default -> "none";
		};
	}

	public static float speed(int level) {
		return switch (level) {
			case 1 -> 1.3F;
			case 3 -> 0.8F;
			case 4 -> 0.7F;
			default -> 1.0F;
		};
	}
}
