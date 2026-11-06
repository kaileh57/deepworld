package dev.kaileh57.deepworld.mixin;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {
	@Invoker("createInventory")
	void deepworld_create_inventory();
}
