package dev.kaileh57.deepworld.cooking;

import dev.kaileh57.deepworld.content.Content;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class Spoilage {
	public static final int dish_minutes = 60;
	public static final int heat_line = 600;
	public static boolean enabled = true;

	private Spoilage() {
	}

	public static int max_minutes(ItemStack stack) {
		CompoundTag tag = CookData.tag(stack);
		if (!tag.getStringOr("dish", "").isEmpty()) {
			return tag.getBooleanOr("preserved", false) ? 0 : dish_minutes;
		}
		CookData.Ingredient ingredient = CookData.ingredients.get(CookData.key(stack));
		if (ingredient == null || ingredient.props().containsKey("preserved")) {
			return 0;
		}
		return ingredient.fresh_minutes();
	}

	public static double fraction(ItemStack stack) {
		int max = max_minutes(stack);
		if (max <= 0) {
			return 1.0;
		}
		int left = CookData.tag(stack).getIntOr("fresh", max);
		return Math.max(0, Math.min(1, left / (double) max));
	}

	public static void tick(MinecraftServer server, long tick) {
		if (!enabled || tick % 1200 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			tick_player(player);
		}
	}

	public static void tick_player(ServerPlayer player) {
		int rate = player.getBlockY() < heat_line ? 10 : 1;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(Content.item("insulated_hamper"))) {
				FoodContainers.chill(stack, rate);
				continue;
			}
			ItemStack aged = age(stack, rate);
			if (aged != stack) {
				inventory.setItem(slot, aged);
			}
		}
	}

	public static ItemStack age(ItemStack stack, int rate) {
		if (stack.isEmpty() || stack.is(Content.item("spoiled_food"))) {
			return stack;
		}
		int max = max_minutes(stack);
		if (max <= 0) {
			return stack;
		}
		CompoundTag tag = CookData.tag(stack);
		int left = tag.getIntOr("fresh", max + rate) - rate;
		if (left <= 0) {
			ItemStack spoiled = new ItemStack(Content.item("spoiled_food"), stack.getCount());
			spoiled.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("was " + stack.getHoverName().getString()).withStyle(ChatFormatting.DARK_GRAY))));
			return spoiled;
		}
		tag.putInt("fresh", left);
		tag.putInt("fresh_max", max);
		CookData.set_tag(stack, tag);
		if (!stack.is(Content.item("dish")) && !stack.is(Content.item("drink"))) {
			List<Component> lines = new ArrayList<>();
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					if (!line.getString().startsWith("fresh ") && !line.getString().startsWith("stale ")) {
						lines.add(line);
					}
				}
			}
			String label = left * 3 > max ? "fresh " : "stale ";
			lines.add(Component.literal(label + Dishes.minutes(left * 60)).withStyle(left * 3 > max ? ChatFormatting.GREEN : ChatFormatting.GOLD));
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
		return stack;
	}
}
