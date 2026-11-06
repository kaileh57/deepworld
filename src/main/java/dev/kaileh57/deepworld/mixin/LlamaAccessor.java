package dev.kaileh57.deepworld.mixin;

import net.minecraft.world.entity.animal.equine.Llama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Llama.class)
public interface LlamaAccessor {
	@Invoker("setStrength")
	void deepworld_set_strength(int strength);
}
