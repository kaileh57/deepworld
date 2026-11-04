package dev.kaileh57.deepworld.cooking;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kaileh57.deepworld.content.Content;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;

public final class CookData {
	public static final List<String> properties = List.of("hearty", "rich", "fresh", "sweet", "warming", "cooling", "glowing", "briny", "earthy", "bitter", "hushed", "preserved");
	public static final Set<String> meats = Set.of("minecraft:beef", "minecraft:porkchop", "minecraft:mutton", "minecraft:chicken", "minecraft:rabbit", "deepworld:hoglin_chop", "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_mutton");

	public record Ingredient(String key, Map<String, Integer> props, int fresh_minutes) {
	}

	public record Dish(String id, String name, String station, List<String> ingredients, Map<String, Integer> buffs, int nutrition, float saturation, int tier, int duration, boolean preserved, String heat, String kind, String special, String result, int count) {
		public boolean drink() {
			return kind.equals("drink");
		}

		public String key() {
			return drink() ? "drink:" + id : "deepworld:dish/" + id;
		}
	}

	public record Station(String id, String name, String kind, String station, int slots, int max_heat, float speed, int time, String icon, List<JsonObject> visual) {
	}

	public record Process(String station, String input, String output, int count, int labor, String remainder) {
	}

	public static final Map<String, Ingredient> ingredients = new HashMap<>();
	public static final Map<String, Dish> dishes = new LinkedHashMap<>();
	public static final Map<String, Dish> dishes_by_key = new HashMap<>();
	public static final Map<String, Station> stations = new LinkedHashMap<>();
	public static final List<Process> processes = new ArrayList<>();

	private CookData() {
	}

	public static int heat_level(String name) {
		return switch (name) {
			case "none" -> 0;
			case "low", "soul" -> 1;
			case "medium" -> 2;
			case "high" -> 3;
			case "extreme" -> 4;
			default -> 1;
		};
	}

	public static void load() {
		JsonObject content = Content.data();
		for (Map.Entry<String, JsonElement> entry : content.getAsJsonObject("ingredients").entrySet()) {
			JsonObject value = entry.getValue().getAsJsonObject();
			Map<String, Integer> props = new LinkedHashMap<>();
			value.getAsJsonObject("properties").entrySet().forEach(p -> props.put(p.getKey(), p.getValue().getAsInt()));
			ingredients.put(entry.getKey(), new Ingredient(entry.getKey(), props, value.get("fresh").getAsInt() / 60));
		}
		for (JsonElement element : content.getAsJsonArray("dishes")) {
			JsonObject d = element.getAsJsonObject();
			List<String> ingredient_keys = new ArrayList<>();
			d.getAsJsonArray("ingredients").forEach(i -> ingredient_keys.add(i.getAsString()));
			Map<String, Integer> buffs = new LinkedHashMap<>();
			d.getAsJsonObject("buffs").entrySet().forEach(b -> buffs.put(b.getKey(), b.getValue().getAsInt()));
			Dish dish = new Dish(d.get("id").getAsString(), d.get("name").getAsString(), d.get("station").getAsString(), ingredient_keys, buffs, d.get("nutrition").getAsInt(), d.get("saturation").getAsFloat(), d.get("tier").getAsInt(), d.get("duration").getAsInt(), d.get("preserved").getAsBoolean(), d.get("heat").getAsString(), d.get("kind").getAsString(), d.has("special") ? d.get("special").getAsString() : "", d.has("result") ? d.get("result").getAsString() : "", d.has("count") ? d.get("count").getAsInt() : 1);
			dishes.put(dish.id(), dish);
			dishes_by_key.put(dish.key(), dish);
		}
		for (JsonElement element : content.getAsJsonArray("cook_stations")) {
			JsonObject s = element.getAsJsonObject();
			List<JsonObject> visual = new ArrayList<>();
			s.getAsJsonArray("visual").forEach(v -> visual.add(v.getAsJsonObject()));
			stations.put(s.get("id").getAsString(), new Station(s.get("id").getAsString(), s.get("name").getAsString(), s.get("kind").getAsString(), s.get("station").getAsString(), s.has("slots") ? s.get("slots").getAsInt() : 1, s.has("max_heat") ? heat_level(s.get("max_heat").getAsString()) : 0, s.has("speed") ? s.get("speed").getAsFloat() : 1.0F, s.has("time") ? s.get("time").getAsInt() : 0, s.get("icon").getAsString(), visual));
		}
		for (JsonElement element : content.getAsJsonArray("processing")) {
			JsonObject p = element.getAsJsonObject();
			processes.add(new Process(p.get("station").getAsString(), p.get("input").getAsString(), p.get("output").getAsString(), p.get("count").getAsInt(), p.get("labor").getAsInt(), p.has("remainder") ? p.get("remainder").getAsString() : ""));
		}
	}

	public static CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompoundOrEmpty("deepworld");
	}

	public static void set_tag(ItemStack stack, CompoundTag deepworld) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag root = data == null ? new CompoundTag() : data.copyTag();
		root.put("deepworld", deepworld);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
	}

	public static String key(ItemStack stack) {
		CompoundTag tag = tag(stack);
		String dish = tag.getStringOr("dish", "");
		if (!dish.isEmpty()) {
			Dish def = dishes.get(dish);
			if (def != null) {
				return def.key();
			}
			return "deepworld:dish/" + dish;
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static boolean chopped(ItemStack stack) {
		return tag(stack).getBooleanOr("chopped", false);
	}

	public static Map<String, Integer> props(ItemStack stack) {
		String key = key(stack);
		Ingredient ingredient = ingredients.get(key);
		if (ingredient != null) {
			return ingredient.props();
		}
		Dish dish = dishes_by_key.get(key);
		if (dish != null) {
			Map<String, Integer> out = new LinkedHashMap<>();
			dish.buffs().forEach((name, value) -> {
				if (properties.contains(name)) {
					out.put(name, value);
				}
			});
			return out;
		}
		return Map.of();
	}

	public static boolean is_ingredient(ItemStack stack) {
		String key = key(stack);
		return ingredients.containsKey(key) || dishes_by_key.containsKey(key);
	}

	public static boolean preserved(ItemStack stack) {
		CompoundTag tag = tag(stack);
		if (!tag.getStringOr("dish", "").isEmpty()) {
			return tag.getBooleanOr("preserved", false);
		}
		Ingredient ingredient = ingredients.get(key(stack));
		return ingredient == null || ingredient.fresh_minutes() <= 0 || ingredient.props().containsKey("preserved");
	}
}
