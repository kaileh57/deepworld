package dev.kaileh57.deepworld.mixin;

import com.mojang.datafixers.util.Pair;
import dev.kaileh57.deepworld.world.LayerBiomes;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverworldBiomeBuilder.class)
public class OverworldBiomeBuilderMixin {
	@Inject(method = "addBiomes", at = @At("TAIL"))
	private void deepworld_add_layers(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, CallbackInfo ci) {
		LayerBiomes.add_all(biomes);
	}

	@Redirect(method = "addUndergroundBiomes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/OverworldBiomeBuilder;addBottomBiome(Ljava/util/function/Consumer;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;Lnet/minecraft/world/level/biome/Climate$Parameter;FLnet/minecraft/resources/ResourceKey;)V"))
	private void deepworld_skip_deep_dark(OverworldBiomeBuilder builder, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes, Climate.Parameter temperature, Climate.Parameter humidity, Climate.Parameter continentalness, Climate.Parameter erosion, Climate.Parameter weirdness, float offset, ResourceKey<Biome> biome) {
	}
}
