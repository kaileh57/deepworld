package dev.kaileh57.deepworld.living;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.hazard.Hazards;
import java.net.URI;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class WikiCommand {
	private WikiCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("wiki")
			.executes(context -> {
				ServerPlayer player = context.getSource().getPlayer();
				String name = "";
				if (player != null) {
					ItemStack held = player.getMainHandItem();
					String dish = CookData.tag(held).getStringOr("dish", "");
					name = !dish.isEmpty() ? dish : BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace().equals("deepworld") ? BuiltInRegistries.ITEM.getKey(held.getItem()).getPath() : "";
				}
				return send(context.getSource(), name);
			})
			.then(Commands.argument("name", StringArgumentType.greedyString())
				.executes(context -> send(context.getSource(), StringArgumentType.getString(context, "name")))));
	}

	public static String path_for(String raw) {
		String name = raw.trim().toLowerCase().replace(' ', '_');
		if (name.contains(":")) {
			name = name.substring(name.indexOf(':') + 1);
		}
		if (name.isEmpty()) {
			return "";
		}
		for (String dish : CookData.dishes.keySet()) {
			if (dish.equals(name) || dish.endsWith(":" + name)) {
				return "ref/dishes/" + name + "/";
			}
		}
		if (Content.blocks.containsKey(name)) {
			return "ref/blocks/" + name + "/";
		}
		if (Content.items.containsKey(name)) {
			return "ref/items/" + name + "/";
		}
		return null;
	}

	private static int send(CommandSourceStack source, String name) {
		String base = Hazards.config().wiki_url.replaceAll("/+$", "") + "/";
		String path = path_for(name);
		if (path == null) {
			source.sendFailure(Component.literal("no wiki page for " + name));
			path = "";
		}
		String url = base + path;
		Component link = Component.literal(url).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));
		source.sendSuccess(() -> Component.literal("wiki: ").withStyle(ChatFormatting.GRAY).append(link), false);
		return 1;
	}
}
