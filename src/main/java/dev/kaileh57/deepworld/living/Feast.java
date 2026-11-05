package dev.kaileh57.deepworld.living;

import com.google.gson.JsonElement;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.cooking.Meals;
import dev.kaileh57.deepworld.exploration.Logbook;
import dev.kaileh57.deepworld.guild.Guild;
import dev.kaileh57.deepworld.world.ServerState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class Feast {
	private Feast() {
	}

	public static List<List<String>> tasting_layers() {
		List<List<String>> out = new ArrayList<>();
		for (JsonElement element : Content.data().getAsJsonArray("tasting_layers")) {
			List<String> keys = new ArrayList<>();
			element.getAsJsonObject().getAsJsonArray("keys").forEach(k -> keys.add(k.getAsString()));
			out.add(keys);
		}
		return out;
	}

	public static boolean covers_all_layers(List<String> dish_ids) {
		List<List<String>> layers = tasting_layers();
		List<List<Integer>> options = new ArrayList<>();
		for (String id : dish_ids) {
			CookData.Dish dish = CookData.dishes.get(id);
			List<Integer> covered = new ArrayList<>();
			for (int index = 0; index < layers.size(); index++) {
				for (String key : layers.get(index)) {
					if (dish != null && dish.ingredients().contains(key)) {
						covered.add(index);
						break;
					}
				}
			}
			options.add(covered);
		}
		return assign(options, 0, new boolean[layers.size()], layers.size());
	}

	private static boolean assign(List<List<Integer>> options, int dish, boolean[] used, int layers) {
		int filled = 0;
		for (boolean value : used) {
			filled += value ? 1 : 0;
		}
		if (filled == layers) {
			return true;
		}
		if (dish >= options.size()) {
			return false;
		}
		for (int layer : options.get(dish)) {
			if (!used[layer]) {
				used[layer] = true;
				if (assign(options, dish + 1, used, layers)) {
					return true;
				}
				used[layer] = false;
			}
		}
		return assign(options, dish + 1, used, layers);
	}

	public static String serve(ServerPlayer host, ServerLevel level, BlockPos table, List<ItemStack> plates) {
		Map<String, Integer> buffs = new HashMap<>();
		int longest = 0;
		List<String> ids = new ArrayList<>();
		for (ItemStack plate : plates) {
			CompoundTag tag = CookData.tag(plate);
			String id = tag.getStringOr("dish", "");
			if (id.isEmpty() || tag.getIntOr("quality", 0) <= 0) {
				continue;
			}
			ids.add(id);
			CompoundTag buff_tag = tag.getCompoundOrEmpty("buffs");
			for (String key : buff_tag.keySet()) {
				buffs.merge(key, buff_tag.getIntOr(key, 1), Math::max);
			}
			longest = Math.max(longest, tag.getIntOr("duration", 300));
		}
		if (ids.size() < 2) {
			return "a feast needs at least two dishes";
		}
		int seconds = (int) (longest * 1.25);
		int hearts = ids.size() / 4;
		List<ServerPlayer> guests = new ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, new AABB(table).inflate(8)));
		if (!guests.contains(host)) {
			guests.add(host);
		}
		for (ServerPlayer guest : guests) {
			Meals.feast(guest, buffs, seconds, hearts);
			dev.kaileh57.deepworld.hud.Hud.notice(guest, Component.literal("you share a feast of " + ids.size() + " dishes").withStyle(ChatFormatting.GOLD));
			Logbook.add(guest, "shared a feast of " + ids.size() + " dishes");
		}
		level.playSound(null, table, SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0F, 1.0F);
		Guild.add(host, "feasts", 1);
		if (ids.size() >= 10 && covers_all_layers(ids)) {
			ServerState state = ServerState.get(level.getServer());
			Guild.add(host, "tasting_menu", 1);
			if (!state.tasting_awarded) {
				state.tasting_awarded = true;
				state.bonus_meal_slots++;
				Meals.server_bonus_slots = state.bonus_meal_slots;
				state.log(host.getGameProfile().name() + " served the tasting menu");
				state.setDirty();
				dev.kaileh57.deepworld.hud.Hud.title_all(level.getServer(), Component.literal("tasting menu").withStyle(ChatFormatting.GOLD), Component.literal(host.getGameProfile().name() + " served it. everyone gains a meal slot").withStyle(ChatFormatting.GRAY));
			}
			return "the tasting menu is complete";
		}
		return "served " + ids.size() + " dishes to " + guests.size() + (guests.size() == 1 ? " guest" : " guests");
	}
}
