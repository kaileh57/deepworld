package dev.kaileh57.deepworld.cooking;

import com.mojang.serialization.Codec;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.guild.Guild;
import dev.kaileh57.deepworld.hud.DwGui;
import dev.kaileh57.deepworld.hud.Hud;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Cookbook {
	public static final AttachmentType<List<String>> learned = AttachmentRegistry.create(DeepWorld.id("learned_recipes"), builder -> builder.persistent(Codec.STRING.listOf()).initializer(ArrayList::new).copyOnDeath());
	public static final int page_size = 45;

	private Cookbook() {
	}

	public static boolean knows(ServerPlayer player, String dish) {
		return player.getAttachedOrCreate(learned).contains(dish) || Guild.counter(player, "cooked_" + dish) > 0;
	}

	public static List<CookData.Dish> known(ServerPlayer player) {
		List<CookData.Dish> out = new ArrayList<>();
		for (CookData.Dish dish : CookData.dishes.values()) {
			if (knows(player, dish.id())) {
				out.add(dish);
			}
		}
		return out;
	}

	public static void teach(ServerPlayer player, String dish) {
		List<String> list = new ArrayList<>(player.getAttachedOrCreate(learned));
		if (!list.contains(dish)) {
			list.add(dish);
			player.setAttached(learned, list);
		}
	}

	public static CookData.Dish learn_random(ServerPlayer player, int tier) {
		List<CookData.Dish> options = new ArrayList<>();
		for (CookData.Dish dish : CookData.dishes.values()) {
			if (dish.tier() <= tier && dish.tier() > 0 && !knows(player, dish.id()) && !dish.station().equals("feast")) {
				options.add(dish);
			}
		}
		if (options.isEmpty()) {
			return null;
		}
		options.sort((a, b) -> Integer.compare(b.tier(), a.tier()));
		int top = options.get(0).tier();
		List<CookData.Dish> best = options.stream().filter(d -> d.tier() >= top - 1).toList();
		CookData.Dish pick = best.get(player.getRandom().nextInt(best.size()));
		teach(player, pick.id());
		return pick;
	}

	public static String ingredient_name(String key) {
		if (key.equals("#meat")) {
			return "any meat";
		}
		if (key.startsWith("deepworld:dish/") || key.startsWith("drink:")) {
			CookData.Dish dish = CookData.dishes.get(key.substring(key.indexOf(key.startsWith("drink:") ? ":" : "/") + 1));
			return dish == null ? key : dish.name();
		}
		var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
		return new ItemStack(item).getHoverName().getString().toLowerCase();
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer server_player)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(Content.item("cooking_notes"))) {
				int tier = CookData.tag(stack).getIntOr("tier", 2);
				CookData.Dish dish = learn_random(server_player, tier);
				if (dish == null) {
					Hud.notice(server_player, Component.literal("nothing in these notes you don't already know"));
				} else {
					stack.shrink(1);
					Hud.notice(server_player, Component.literal("you learn " + dish.name()).withStyle(ChatFormatting.GOLD));
					level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
			if (stack.is(Content.item("cookbook"))) {
				open(server_player, 0);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
	}

	public static void open(ServerPlayer player, int page) {
		List<CookData.Dish> list = known(player);
		int pages = Math.max(1, (list.size() + page_size - 1) / page_size);
		int current = Math.max(0, Math.min(page, pages - 1));
		DwGui gui = new DwGui(MenuType.GENERIC_9x6, player, false);
		gui.setTitle(Component.literal("cookbook " + list.size() + "/" + CookData.dishes.size()));
		for (int index = 0; index < 54; index++) {
			gui.setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		for (int index = 0; index < page_size && current * page_size + index < list.size(); index++) {
			CookData.Dish dish = list.get(current * page_size + index);
			ItemStack icon = Dishes.make(dish, 2, "");
			GuiElementBuilder button = GuiElementBuilder.from(icon).setName(Component.literal(dish.name()));
			button.setLore(new ArrayList<>());
			button.addLoreLine(Component.literal(dish.station() + (dish.heat().isEmpty() ? "" : ", " + dish.heat() + " heat")).withStyle(ChatFormatting.GRAY));
			for (String key : dish.ingredients()) {
				button.addLoreLine(Component.literal("· " + ingredient_name(key)).withStyle(ChatFormatting.WHITE));
			}
			StringBuilder buffs = new StringBuilder();
			for (Map.Entry<String, Integer> buff : dish.buffs().entrySet()) {
				buffs.append(buffs.isEmpty() ? "" : ", ").append(buff.getKey()).append(" ").append(Dishes.roman(buff.getValue()));
			}
			if (!buffs.isEmpty()) {
				button.addLoreLine(Component.literal(buffs.toString()).withStyle(ChatFormatting.GREEN));
			}
			button.addLoreLine(Component.literal("middle-click for the wiki").withStyle(ChatFormatting.DARK_GRAY));
			gui.setSlot(index, button.build());
		}
		if (current > 0) {
			gui.setSlot(45, new GuiElementBuilder(Items.ARROW).setName(Component.literal("previous")).setCallback((i, t, a, g) -> open(player, current - 1)).build());
		}
		gui.setSlot(49, new GuiElementBuilder(Items.BOOK).setName(Component.literal("page " + (current + 1) + "/" + pages)).addLoreLine(Component.literal("cook new combinations or read cooking notes to learn more")).build());
		if (current < pages - 1) {
			gui.setSlot(53, new GuiElementBuilder(Items.ARROW).setName(Component.literal("next")).setCallback((i, t, a, g) -> open(player, current + 1)).build());
		}
		gui.open();
	}
}
