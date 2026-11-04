package dev.kaileh57.deepworld.transit;

import dev.kaileh57.deepworld.DeepWorld;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;

public final class Transit {
	private Transit() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, DeepWorld.id("deepline_lines"), DeeplineFeature.codec);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION, ResourceKey.create(Registries.PLACED_FEATURE, DeepWorld.id("deepline_lines")));
		Lifts.register();
		TransitTests.register();
	}
}
