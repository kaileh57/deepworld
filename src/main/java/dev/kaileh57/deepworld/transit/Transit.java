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
	public static net.minecraft.world.level.block.entity.BlockEntityType<RailDevice> rail_entity;

	private Transit() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, DeepWorld.id("deepline_lines"), DeeplineFeature.codec);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION, ResourceKey.create(Registries.PLACED_FEATURE, DeepWorld.id("deepline_lines")));
		Lifts.register();
		Carts.register();
		net.minecraft.world.level.block.Block[] devices = dev.kaileh57.deepworld.cooking.CookData.stations.values().stream().filter(s -> dev.kaileh57.deepworld.cooking.StationBlock.rail_kinds.contains(s.kind())).map(s -> dev.kaileh57.deepworld.content.Content.blocks.get(s.id())).toArray(net.minecraft.world.level.block.Block[]::new);
		rail_entity = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, DeepWorld.id("rail_device"), new net.minecraft.world.level.block.entity.BlockEntityType<>(RailDevice::new, java.util.Set.of(devices)));
		eu.pb4.polymer.core.api.block.PolymerBlockUtils.registerBlockEntity(rail_entity);
		TransitTests.register();
		TrainTests.register();
	}
}
