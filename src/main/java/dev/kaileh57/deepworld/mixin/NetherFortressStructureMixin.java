package dev.kaileh57.deepworld.mixin;

import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(NetherFortressStructure.class)
public class NetherFortressStructureMixin {
	@ModifyConstant(method = "findGenerationPoint", constant = @Constant(intValue = 64))
	private int deepworld_start_below_zero(int original) {
		return original - 256;
	}

	@ModifyArgs(method = "generatePieces", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;moveInsideHeights(Lnet/minecraft/util/RandomSource;II)V"))
	private static void deepworld_fortress_heights(Args args) {
		args.set(1, (int) args.get(1) - 256);
		args.set(2, (int) args.get(2) - 256);
	}
}
