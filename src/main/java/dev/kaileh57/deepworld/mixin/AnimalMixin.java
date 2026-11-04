package dev.kaileh57.deepworld.mixin;

import dev.kaileh57.deepworld.farming.Husbandry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public class AnimalMixin {
	@Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"))
	private void deepworld_traits(ServerLevel level, Animal partner, AgeableMob offspring, CallbackInfo ci) {
		Husbandry.on_breed(level, (Animal) (Object) this, partner, offspring);
	}
}
