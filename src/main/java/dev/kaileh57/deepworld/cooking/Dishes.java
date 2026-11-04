package dev.kaileh57.deepworld.cooking;

import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;

public final class Dishes {
	public static final String[] quality_names = {"burnt", "plain", "good", "fine", "masterwork"};
	public static final double[] quality_duration = {0.0, 0.75, 1.0, 1.25, 1.5};
	private static final Map<String, String> station_words = Map.of("pot", "stew", "skillet", "fry", "oven", "bake", "smokehouse", "smoke", "crock", "pickle", "drying_rack", "jerky", "still", "tonic", "ice_box", "chill", "spit", "roast");

	private Dishes() {
	}

	public static ItemStack make(CookData.Dish dish, int quality, String cook) {
		boolean burnt = quality <= 0;
		ItemStack stack = new ItemStack(Content.item(dish.drink() ? "drink" : "dish"));
		CompoundTag tag = new CompoundTag();
		tag.putString("dish", dish.id());
		tag.putString("kind", dish.kind());
		tag.putInt("quality", quality);
		tag.putInt("duration", (int) Math.round(dish.duration() * quality_duration[Math.max(0, quality)]));
		tag.putBoolean("preserved", dish.preserved() || burnt);
		tag.putString("special", burnt ? "" : dish.special());
		CompoundTag buffs = new CompoundTag();
		if (!burnt) {
			dish.buffs().forEach(buffs::putInt);
			if (quality >= 3 && !dish.buffs().containsKey("fresh")) {
				buffs.putInt("fresh", 1);
			}
		}
		tag.put("buffs", buffs);
		if (!cook.isEmpty()) {
			tag.putString("cook", cook);
		}
		CookData.set_tag(stack, tag);
		String name = burnt ? "burnt " + dish.name() : dish.name();
		stack.set(DataComponents.ITEM_NAME, Component.literal(name));
		stack.set(DataComponents.ITEM_MODEL, DeepWorld.id("dish/" + (burnt ? "generic_burnt" : icon(dish))));
		int nutrition = burnt ? 2 : dish.nutrition();
		float saturation = burnt ? 0.1F : dish.saturation();
		stack.set(DataComponents.FOOD, new FoodProperties(nutrition, nutrition * saturation, true));
		stack.set(DataComponents.CONSUMABLE, Consumable.builder().consumeSeconds(dish.drink() ? 1.2F : 1.6F).animation(dish.drink() ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT).sound(dish.drink() ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT).build());
		stack.set(DataComponents.MAX_STACK_SIZE, 16);
		stack.set(DataComponents.LORE, new ItemLore(lore(tag, dish, quality, cook)));
		return stack;
	}

	private static String icon(CookData.Dish dish) {
		return dish.id().startsWith("generic_") ? dish.id() : dish.id();
	}

	private static List<Component> lore(CompoundTag tag, CookData.Dish dish, int quality, String cook) {
		List<Component> lines = new ArrayList<>();
		ChatFormatting color = switch (quality) {
			case 0 -> ChatFormatting.DARK_GRAY;
			case 1 -> ChatFormatting.GRAY;
			case 2 -> ChatFormatting.WHITE;
			case 3 -> ChatFormatting.AQUA;
			default -> ChatFormatting.GOLD;
		};
		lines.add(Component.literal(quality_names[quality] + (dish.drink() ? " drink" : "")).withStyle(color));
		CompoundTag buffs = tag.getCompoundOrEmpty("buffs");
		if (!buffs.isEmpty()) {
			StringBuilder text = new StringBuilder();
			for (String key : buffs.keySet()) {
				if (!text.isEmpty()) {
					text.append(", ");
				}
				text.append(key.replace('_', ' ')).append(' ').append(roman(buffs.getIntOr(key, 1)));
			}
			lines.add(Component.literal(text.toString()).withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(minutes(tag.getIntOr("duration", 0))).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (tag.getBooleanOr("preserved", false)) {
			lines.add(Component.literal("preserved").withStyle(ChatFormatting.DARK_GREEN));
		}
		if (quality >= 4 && !cook.isEmpty()) {
			lines.add(Component.literal("by " + cook).withStyle(ChatFormatting.GOLD));
		}
		return lines;
	}

	public static String minutes(int seconds) {
		return seconds >= 60 ? (seconds / 60) + "m" : seconds + "s";
	}

	public static String roman(int value) {
		return switch (value) {
			case 1 -> "i";
			case 2 -> "ii";
			case 3 -> "iii";
			default -> String.valueOf(value);
		};
	}

	public static CookData.Dish generic(String station, List<ItemStack> inputs) {
		Map<String, Integer> sums = new LinkedHashMap<>();
		int portions = 0;
		boolean any_unpreserved = false;
		for (ItemStack stack : inputs) {
			int weight = CookData.chopped(stack) ? 2 : 1;
			portions += weight;
			for (Map.Entry<String, Integer> prop : CookData.props(stack).entrySet()) {
				sums.merge(prop.getKey(), prop.getValue() * weight, Integer::sum);
			}
			if (!CookData.preserved(stack)) {
				any_unpreserved = true;
			}
		}
		boolean preserved = sums.getOrDefault("preserved", 0) >= 2 || !any_unpreserved;
		sums.remove("preserved");
		List<Map.Entry<String, Integer>> top = sums.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed()).limit(2).toList();
		Map<String, Integer> buffs = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : top) {
			if (entry.getValue() >= 2) {
				buffs.put(entry.getKey(), Math.min(2, Math.max(1, entry.getValue() / 3)));
			}
		}
		String word = station_words.getOrDefault(station, "dish");
		String lead = top.isEmpty() ? "plain" : top.getFirst().getKey();
		int nutrition = Math.min(10, 2 + portions + portions / 2);
		boolean drink = station.equals("still");
		return new CookData.Dish("generic_" + word, lead + " " + word, station, List.of(), buffs, nutrition, 0.6F, 0, 300, preserved, "low", drink ? "drink" : "dish", "", "", 1);
	}

	public static CookData.Dish match(String station, List<ItemStack> inputs) {
		List<String> keys = new ArrayList<>();
		for (ItemStack stack : inputs) {
			String key = CookData.key(stack);
			if (!keys.contains(key)) {
				keys.add(key);
			}
		}
		for (CookData.Dish dish : CookData.dishes.values()) {
			if (!dish.station().equals(station) || dish.ingredients().size() != keys.size()) {
				continue;
			}
			List<String> remaining = new ArrayList<>(keys);
			boolean ok = true;
			for (String wanted : dish.ingredients()) {
				String hit = null;
				for (String have : remaining) {
					if (have.equals(wanted) || (wanted.equals("#meat") && CookData.meats.contains(have))) {
						hit = have;
						break;
					}
				}
				if (hit == null) {
					ok = false;
					break;
				}
				remaining.remove(hit);
			}
			if (ok) {
				return dish;
			}
		}
		return null;
	}
}
