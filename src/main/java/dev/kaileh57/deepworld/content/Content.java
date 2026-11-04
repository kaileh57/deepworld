package dev.kaileh57.deepworld.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.block.DwOreBlock;
import dev.kaileh57.deepworld.block.DwStoneBlock;
import dev.kaileh57.deepworld.forge.ForgeBlock;
import dev.kaileh57.deepworld.forge.ForgeBlockEntity;
import dev.kaileh57.deepworld.item.DwArmorItem;
import dev.kaileh57.deepworld.item.DwToolItem;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public final class Content {
	public static final Map<String, Item> items = new LinkedHashMap<>();
	public static final Map<String, Block> blocks = new LinkedHashMap<>();
	public static final Map<Item, Double> armor_heat = new HashMap<>();
	public static final Map<String, Map<Item, Integer>> station_fuel = new HashMap<>();
	public static BlockEntityType<ForgeBlockEntity> forge_entity;
	private static JsonObject data;

	private Content() {
	}

	public static JsonObject data() {
		if (data == null) {
			try (Reader reader = new InputStreamReader(Content.class.getResourceAsStream("/deepworld_content.json"), StandardCharsets.UTF_8)) {
				data = JsonParser.parseReader(reader).getAsJsonObject();
			} catch (Exception error) {
				throw new IllegalStateException("could not read content", error);
			}
		}
		return data;
	}

	public static Item item(String path) {
		return items.get(path);
	}

	public static void register() {
		JsonObject content = data();
		for (JsonElement element : content.getAsJsonArray("items")) {
			JsonObject entry = element.getAsJsonObject();
			Item base = vanilla_item(entry.get("base").getAsString());
			String id = entry.get("id").getAsString();
			if (id.endsWith("_charge")) {
				String kind = id.substring(0, id.length() - "_charge".length());
				register_item(id, properties -> new dev.kaileh57.deepworld.alchemy.ChargeItem(properties, kind, base));
			} else if (id.equals("spoiled_food")) {
				register_item(id, properties -> new SimplePolymerItem(properties.food(new net.minecraft.world.food.FoodProperties(2, 0.1F, false), net.minecraft.world.item.component.Consumable.builder().onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(java.util.List.of(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, 200, 0), new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER, 400, 0)))).build()), base));
			} else {
				register_item(id, properties -> new SimplePolymerItem(properties, base));
			}
		}
		for (JsonElement element : content.getAsJsonArray("stones")) {
			JsonObject entry = element.getAsJsonObject();
			String id = entry.get("id").getAsString();
			Block block = register_block(id, properties -> new DwStoneBlock(properties, DeepWorld.id("block/" + id)), stone_properties(entry));
			block_item(id, block, vanilla_item(entry.get("base").getAsString()));
		}
		for (JsonElement element : content.getAsJsonArray("ores")) {
			JsonObject entry = element.getAsJsonObject();
			String id = entry.get("id").getAsString();
			JsonArray xp = entry.getAsJsonArray("xp");
			Block block = register_block(id, properties -> new DwOreBlock(UniformInt.of(xp.get(0).getAsInt(), xp.get(1).getAsInt()), properties, DeepWorld.id("block/" + id)), stone_properties(entry));
			block_item(id, block, vanilla_item(entry.get("base").getAsString()));
		}
		for (JsonElement element : content.getAsJsonArray("stations")) {
			JsonObject entry = element.getAsJsonObject();
			String id = entry.get("id").getAsString();
			Block block = register_block(id, properties -> new ForgeBlock(properties, id, DeepWorld.id("block/" + id)), BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.5F).sound(SoundType.STONE).lightLevel(state -> state.getValue(ForgeBlock.lit) ? 13 : 0));
			block_item(id, block, vanilla_item(entry.get("base").getAsString()));
			Map<Item, Integer> fuel = new HashMap<>();
			for (Map.Entry<String, JsonElement> fuel_entry : entry.getAsJsonObject("fuel").entrySet()) {
				fuel.put(vanilla_item(fuel_entry.getKey()), fuel_entry.getValue().getAsInt());
			}
			station_fuel.put(id, fuel);
		}
		Block[] station_blocks = content.getAsJsonArray("stations").asList().stream().map(e -> blocks.get(e.getAsJsonObject().get("id").getAsString())).toArray(Block[]::new);
		forge_entity = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, DeepWorld.id("forge"), new BlockEntityType<>(ForgeBlockEntity::new, Set.of(station_blocks)));
		PolymerBlockUtils.registerBlockEntity(forge_entity);
		for (JsonElement element : content.getAsJsonArray("tools")) {
			JsonObject entry = element.getAsJsonObject();
			String tier = entry.get("tier").getAsString();
			ToolMaterial material = new ToolMaterial(
				TagKey.create(Registries.BLOCK, DeepWorld.id("incorrect_for_" + tier + "_tool")),
				entry.get("durability").getAsInt(),
				entry.get("speed").getAsFloat(),
				entry.get("damage").getAsFloat(),
				12,
				TagKey.create(Registries.ITEM, DeepWorld.id(tier + "_tool_materials"))
			);
			Item base = vanilla_item(entry.get("base").getAsString());
			Item tool = register_item(entry.get("id").getAsString(), properties -> new DwToolItem(properties.pickaxe(material, 1.0F, -2.8F), base));
			PolymerItemUtils.syncDefaultComponent(tool, DataComponents.TOOL);
		}
		for (JsonElement element : content.getAsJsonArray("armor")) {
			JsonObject entry = element.getAsJsonObject();
			String set = entry.get("set").getAsString();
			boolean netherite = entry.get("base").getAsString().equals("netherite");
			Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
			defense.put(ArmorType.BOOTS, 3);
			defense.put(ArmorType.LEGGINGS, 6);
			defense.put(ArmorType.CHESTPLATE, 8);
			defense.put(ArmorType.HELMET, 3);
			defense.put(ArmorType.BODY, 11);
			ResourceKey<EquipmentAsset> asset = ResourceKey.create(EquipmentAssets.ROOT_ID, DeepWorld.id(set));
			ArmorMaterial material = new ArmorMaterial(entry.get("durability").getAsInt(), defense, 12, netherite ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.ARMOR_EQUIP_DIAMOND, 2.0F, netherite ? 0.1F : 0.0F, TagKey.create(Registries.ITEM, DeepWorld.id("repairs_" + set + "_armor")), asset);
			double heat = entry.get("heat").getAsDouble();
			for (ArmorType type : new ArmorType[] {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS}) {
				String piece = type.getName();
				Item base = vanilla_item("minecraft:" + (netherite ? "netherite_" : "diamond_") + piece);
				Item armor = register_item(set + "_" + piece, properties -> new DwArmorItem(properties.humanoidArmor(material, type), base));
				PolymerItemUtils.syncDefaultComponent(armor, DataComponents.EQUIPPABLE);
				armor_heat.put(armor, heat);
			}
		}
	}

	private static BlockBehaviour.Properties stone_properties(JsonObject entry) {
		float hardness = entry.get("hardness").getAsFloat();
		float resistance = entry.has("resistance") ? entry.get("resistance").getAsFloat() : hardness;
		return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(hardness, resistance).sound(SoundType.DEEPSLATE);
	}

	private static Item vanilla_item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}

	public static Item register_item(String path, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DeepWorld.id(path));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
		items.put(path, item);
		return item;
	}

	public static Block register_block(String path, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, DeepWorld.id(path));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		blocks.put(path, block);
		return block;
	}

	public static Item block_item(String path, Block block, Item base) {
		return register_item(path, properties -> new PolymerBlockItem(block, properties.useBlockDescriptionPrefix(), base));
	}
}
