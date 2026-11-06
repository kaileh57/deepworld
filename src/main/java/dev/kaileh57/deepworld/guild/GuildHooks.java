package dev.kaileh57.deepworld.guild;

import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.heat.HeatSystem;
import dev.kaileh57.deepworld.world.Layers;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GuildHooks {
	private static final Item[] rails = {Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL};

	private GuildHooks() {
	}

	public static void register() {
		Guild.load();
		CraftTests.register();
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player instanceof ServerPlayer server_player && stack.is(Content.item("guild_journal"))) {
				new JournalGui(server_player).open();
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer player)) {
				return;
			}
			if (entity.getY() < 1000) {
				Guild.add(player, "deep_kills", 1);
			}
			if (entity.getType() == EntityTypes.WARDEN) {
				Guild.add(player, "wardens", 1);
			}
		});
		HeatSystem.add_protection(player -> Guild.focused(player, "delver") ? 1 : 0);
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 40 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Guild.set_max(player, "deepest", 1200 - player.getBlockY());
			Layers.Layer layer = Layers.at(player.getBlockY());
			if (layer != null) {
				String key = "seen_layer_" + layer.number();
				if (Guild.counter(player, key) == 0) {
					Guild.add(player, key, 1);
					Guild.add(player, "layers_seen", 1);
				}
			}
			int rails_used = 0;
			for (Item rail : rails) {
				rails_used += player.getStats().getValue(Stats.ITEM_USED.get(rail));
			}
			Guild.set_max(player, "rails_laid", rails_used);
		}
	}
}
