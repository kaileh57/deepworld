package dev.kaileh57.deepworld.mixin;

import dev.kaileh57.deepworld.world.ColumnFluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public class AquiferMixin {
	@Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
	private void deepworld_dry_deep_caves(int block_x, int block_y, int block_z, double density, CallbackInfoReturnable<BlockState> cir) {
		if (block_y < ColumnFluids.deep_line) {
			cir.setReturnValue(density > 0.0 ? null : ColumnFluids.fluid_at(block_x, block_y, block_z));
		}
	}
}
