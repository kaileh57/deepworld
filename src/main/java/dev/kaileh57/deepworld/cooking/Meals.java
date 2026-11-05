package dev.kaileh57.deepworld.cooking;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.hud.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public final class Meals {
	public record Meal(String id, String name, String kind, Map<String, Integer> buffs, String special, int seconds) {
		public static final Codec<Meal> codec = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Meal::id),
			Codec.STRING.fieldOf("name").forGetter(Meal::name),
			Codec.STRING.fieldOf("kind").forGetter(Meal::kind),
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("buffs").forGetter(Meal::buffs),
			Codec.STRING.fieldOf("special").forGetter(Meal::special),
			Codec.INT.fieldOf("seconds").forGetter(Meal::seconds)
		).apply(i, Meal::new));

		public Meal tick() {
			return new Meal(id, name, kind, buffs, special, seconds - 1);
		}
	}

	public static final AttachmentType<List<Meal>> meals = AttachmentRegistry.create(DeepWorld.id("meals"), builder -> builder.persistent(Meal.codec.listOf()).initializer(ArrayList::new));
	private static final Identifier warming_modifier = DeepWorld.id("warming");
	public static int server_bonus_slots;
	public static boolean temp_bonus;

	private Meals() {
	}

	public static void init() {
	}

	public static int slot_count() {
		return 3 + server_bonus_slots + (temp_bonus ? 1 : 0);
	}

	public static List<Meal> of(ServerPlayer player) {
		return player.getAttachedOrCreate(meals);
	}

	public static void feast(ServerPlayer player, Map<String, Integer> buffs, int seconds, int bonus_hearts) {
		List<Meal> list = new ArrayList<>(of(player));
		list.removeIf(m -> m.id().equals("feast"));
		List<Meal> dishes = list.stream().filter(m -> !m.kind().equals("drink")).toList();
		if (dishes.size() >= slot_count()) {
			list.remove(dishes.stream().min((a, b) -> Integer.compare(a.seconds(), b.seconds())).orElseThrow());
		}
		list.add(new Meal("feast", "feast", "dish", buffs, "", seconds));
		player.setAttached(meals, list);
		apply_on_eat(player, buffs, "", seconds);
		if (bonus_hearts > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, seconds * 20, Math.max(0, bonus_hearts - 1), true, false, true));
		}
	}

	public static void eat(ServerPlayer player, ItemStack stack) {
		CompoundTag tag = CookData.tag(stack);
		String id = tag.getStringOr("dish", "");
		if (id.isEmpty()) {
			return;
		}
		int quality = tag.getIntOr("quality", 2);
		if (quality <= 0) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
			return;
		}
		Map<String, Integer> buffs = new HashMap<>();
		CompoundTag buff_tag = tag.getCompoundOrEmpty("buffs");
		for (String key : buff_tag.keySet()) {
			buffs.put(key, buff_tag.getIntOr(key, 1));
		}
		String kind = tag.getStringOr("kind", "dish");
		String special = tag.getStringOr("special", "");
		String name = stack.getHoverName().getString();
		int seconds = Math.max(30, tag.getIntOr("duration", 300));
		apply_on_eat(player, buffs, special, seconds);
		List<Meal> list = new ArrayList<>(of(player));
		Meal meal = new Meal(id, name, kind, buffs, special, seconds);
		if (kind.equals("drink")) {
			list.removeIf(m -> m.kind().equals("drink"));
			list.add(meal);
		} else {
			List<Meal> dishes = list.stream().filter(m -> !m.kind().equals("drink")).toList();
			String primary = buffs.keySet().stream().findFirst().orElse("");
			Meal replace = null;
			for (Meal existing : dishes) {
				if (existing.id().equals(id) || (!primary.isEmpty() && existing.buffs().containsKey(primary))) {
					replace = existing;
					break;
				}
			}
			if (replace == null && dishes.size() >= slot_count()) {
				replace = dishes.stream().min((a, b) -> Integer.compare(a.seconds(), b.seconds())).orElse(null);
			}
			if (replace != null) {
				list.remove(replace);
			}
			list.add(meal);
		}
		player.setAttached(meals, list);
	}

	private static void apply_on_eat(ServerPlayer player, Map<String, Integer> buffs, String special, int seconds) {
		int rich = buffs.getOrDefault("rich", 0);
		if (rich > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, Math.min(seconds, 600) * 20, rich - 1, true, false, true));
		}
		int bitter = buffs.getOrDefault("bitter", 0);
		if (special.equals("cleanse_all") || bitter >= 3) {
			cleanse(player, 99);
		} else if (special.equals("cleanse_one") || bitter > 0) {
			cleanse(player, bitter > 0 ? bitter : 1);
		}
		if (special.equals("mend_tool")) {
			ItemStack tool = player.getItemInHand(InteractionHand.OFF_HAND);
			if (tool.isDamageableItem()) {
				tool.setDamageValue(0);
				Hud.notice(player, Component.literal("your " + tool.getHoverName().getString() + " feels new"));
			}
		}
	}

	private static void cleanse(ServerPlayer player, int count) {
		List<Holder<MobEffect>> harmful = new ArrayList<>();
		for (MobEffectInstance instance : player.getActiveEffects()) {
			if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				harmful.add(instance.getEffect());
			}
		}
		for (int index = 0; index < Math.min(count, harmful.size()); index++) {
			player.removeEffect(harmful.get(index));
		}
	}

	public static int strength(ServerPlayer player, String buff) {
		int best = 0;
		for (Meal meal : of(player)) {
			best = Math.max(best, meal.buffs().getOrDefault(buff, 0));
		}
		return best;
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<Meal> list = of(player);
			if (list.isEmpty()) {
				Hud.set(player, "meals", null);
				Hud.set(player, "drink", null);
				clear_attributes(player);
				continue;
			}
			List<Meal> next = new ArrayList<>();
			for (Meal meal : list) {
				Meal ticked = meal.tick();
				if (ticked.seconds() > 0) {
					next.add(ticked);
				} else if (meal.special().equals("crash")) {
					player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0));
				}
			}
			player.setAttached(meals, next);
			apply_buffs(player, tick);
			show(player, next);
		}
	}

	private static void effect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int ticks) {
		player.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true));
	}

	private static void apply_buffs(ServerPlayer player, long tick) {
		int hearty = strength(player, "hearty");
		if (hearty > 0 && tick % 200 == 0) {
			var food = player.getFoodData();
			food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + hearty * 0.6F));
		}
		int fresh = strength(player, "fresh");
		if (fresh > 0 && tick % (100 / fresh) < 20 && player.getHealth() < player.getMaxHealth()) {
			player.heal(1.0F);
		}
		int sweet = strength(player, "sweet");
		if (sweet > 0) {
			effect(player, MobEffects.SPEED, sweet >= 3 ? 1 : 0, 60);
		}
		int glowing = strength(player, "glowing");
		if (glowing > 0) {
			effect(player, MobEffects.NIGHT_VISION, 0, 300);
		}
		int briny = strength(player, "briny");
		if (briny > 0) {
			effect(player, MobEffects.WATER_BREATHING, 0, 60);
			if (briny >= 2) {
				effect(player, MobEffects.DOLPHINS_GRACE, 0, 60);
			}
		}
		int earthy = strength(player, "earthy");
		if (earthy > 0) {
			effect(player, MobEffects.HASTE, earthy >= 3 ? 1 : 0, 60);
			effect(player, MobEffects.LUCK, 0, 60);
		}
		int haste = strength(player, "haste");
		if (haste > 0) {
			effect(player, MobEffects.HASTE, haste - 1, 60);
		}
		int resistance = strength(player, "resistance");
		if (resistance > 0) {
			effect(player, MobEffects.RESISTANCE, resistance - 1, 60);
		}
		int strength_buff = strength(player, "strength");
		if (strength_buff > 0) {
			effect(player, MobEffects.STRENGTH, strength_buff - 1, 60);
		}
		if (strength(player, "fire_resistance") > 0) {
			effect(player, MobEffects.FIRE_RESISTANCE, 0, 60);
		}
		if (strength(player, "bitter") > 0) {
			player.removeEffect(MobEffects.POISON);
		}
		AttributeInstance attack_speed = player.getAttribute(Attributes.ATTACK_SPEED);
		int warming = strength(player, "warming");
		if (attack_speed != null) {
			if (warming > 0) {
				attack_speed.addOrUpdateTransientModifier(new AttributeModifier(warming_modifier, 0.08 * warming, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			} else {
				attack_speed.removeModifier(warming_modifier);
			}
		}
	}

	private static void clear_attributes(ServerPlayer player) {
		AttributeInstance attack_speed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attack_speed != null) {
			attack_speed.removeModifier(warming_modifier);
		}
	}

	private static void show(ServerPlayer player, List<Meal> list) {
		MutableComponent dishes = Component.empty();
		int shown = 0;
		Meal drink = null;
		for (Meal meal : list) {
			if (meal.kind().equals("drink")) {
				drink = meal;
				continue;
			}
			if (shown > 0) {
				dishes.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			}
			dishes.append(Component.literal(meal.name() + " " + Dishes.minutes(meal.seconds())).withStyle(ChatFormatting.YELLOW));
			shown++;
		}
		for (int empty = shown; empty < slot_count(); empty++) {
			dishes.append(Component.literal(empty == 0 && shown == 0 ? "○" : " ○").withStyle(ChatFormatting.DARK_GRAY));
		}
		Hud.set(player, "meals", dishes);
		Hud.set(player, "drink", drink == null ? null : Component.literal(drink.name() + " " + Dishes.minutes(drink.seconds())).withStyle(ChatFormatting.AQUA));
	}
}
