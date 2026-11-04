package dev.kaileh57.deepworld.cooking;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.heat.HeatSystem;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

public final class Cooking {
	public static BlockEntityType<CookBlockEntity> cook_entity;

	private Cooking() {
	}

	public static void register() {
		CookData.load();
		Content.register_item("dish", properties -> new DishItem(properties, Items.COOKED_BEEF));
		Content.register_item("drink", properties -> new DishItem(properties, Items.HONEY_BOTTLE));
		List<Block> cook_blocks = new ArrayList<>();
		for (CookData.Station station : CookData.stations.values()) {
			Block block = Content.register_block(station.id(), properties -> new StationBlock(properties, station), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion());
			Content.register_item(station.id(), properties -> new PolymerBlockItem(block, properties.useBlockDescriptionPrefix(), BuiltInRegistries.ITEM.getValue(Identifier.parse(station.icon()))));
			if (station.kind().equals("cook")) {
				cook_blocks.add(block);
			}
		}
		cook_entity = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, DeepWorld.id("cook_station"), new BlockEntityType<>(CookBlockEntity::new, Set.copyOf(cook_blocks)));
		PolymerBlockUtils.registerBlockEntity(cook_entity);
		for (JsonElement element : Content.data().getAsJsonArray("plants")) {
			JsonObject plant = element.getAsJsonObject();
			String id = plant.get("id").getAsString();
			Block block = Content.register_block(id, properties -> new PlantBlock(properties, DeepWorld.id("block/" + id)), BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.SWEET_BERRY_BUSH).offsetType(BlockBehaviour.OffsetType.XZ));
			Content.register_item(id, properties -> new PolymerBlockItem(block, properties.useBlockDescriptionPrefix(), Items.SHORT_GRASS));
			if (plant.has("biomes")) {
				List<ResourceKey<Biome>> biomes = new ArrayList<>();
				plant.getAsJsonArray("biomes").forEach(b -> biomes.add(ResourceKey.create(Registries.BIOME, Identifier.parse(b.getAsString()))));
				BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes), GenerationStep.Decoration.VEGETAL_DECORATION, ResourceKey.create(Registries.PLACED_FEATURE, DeepWorld.id("forage/" + id)));
			}
		}
		Item spoiled = Content.item("spoiled_food");
		HeatSystem.add_protection(player -> {
			int cooling = Meals.strength(player, "cooling");
			return cooling >= 3 ? 2 : cooling > 0 ? 1 : 0;
		});
		Meals.init();
		CookingTests.register();
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			String path = key.identifier().toString();
			switch (path) {
				case "minecraft:entities/glow_squid" -> builder.withPool(pool(Content.item("blind_eel"), 0.6F, 1, 2));
				case "minecraft:entities/strider" -> builder.withPool(pool(Content.item("lava_fish"), 0.5F, 1, 2));
				case "minecraft:entities/hoglin" -> builder.withPool(pool(Content.item("hoglin_chop"), 1.0F, 1, 3));
				case "minecraft:entities/drowned" -> builder.withPool(pool(Content.item("blind_eel"), 0.15F, 1, 1));
				case "minecraft:blocks/bone_block" -> builder.withPool(pool(Content.item("fossil_marrow"), 0.3F, 1, 1));
				case "minecraft:blocks/amethyst_cluster" -> builder.withPool(pool(Content.item("geode_nectar"), 0.3F, 1, 1));
				default -> {
				}
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, server));
	}

	private static LootPool.Builder pool(Item item, float chance, int min, int max) {
		LootPool.Builder builder = LootPool.lootPool().add(LootItem.lootTableItem(item)).when(LootItemRandomChanceCondition.randomChance(chance));
		return builder;
	}
}
