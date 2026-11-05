package dev.kaileh57.deepworld;

import dev.kaileh57.deepworld.command.SurveyCommand;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.Cooking;
import dev.kaileh57.deepworld.cooking.Meals;
import dev.kaileh57.deepworld.cooking.Spoilage;
import dev.kaileh57.deepworld.transit.Lifts;
import dev.kaileh57.deepworld.transit.Roads;
import dev.kaileh57.deepworld.transit.Transit;
import dev.kaileh57.deepworld.alchemy.Alchemy;
import dev.kaileh57.deepworld.alchemy.Blasting;
import dev.kaileh57.deepworld.farming.Farming;
import dev.kaileh57.deepworld.farming.Fishing;
import dev.kaileh57.deepworld.farming.Husbandry;
import dev.kaileh57.deepworld.guild.GuildHooks;
import dev.kaileh57.deepworld.smithing.Smithing;
import dev.kaileh57.deepworld.world.ServerState;
import dev.kaileh57.deepworld.exploration.Exploration;
import dev.kaileh57.deepworld.exploration.Landmarks;
import dev.kaileh57.deepworld.hazard.Hazards;
import dev.kaileh57.deepworld.economy.Economy;
import dev.kaileh57.deepworld.economy.Settlements;
import dev.kaileh57.deepworld.forge.ForgingRecipes;
import dev.kaileh57.deepworld.heat.HeatSystem;
import dev.kaileh57.deepworld.hud.Hud;
import dev.kaileh57.deepworld.export.DataExport;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeepWorld implements ModInitializer {
	public static final String mod_id = "deepworld";
	public static final Logger logger = LoggerFactory.getLogger(mod_id);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(mod_id, path);
	}

	@Override
	public void onInitialize() {
		PolymerResourcePackUtils.addModAssets(mod_id);
		PolymerResourcePackUtils.markAsRequired();
		Content.register();
		Cooking.register();
		Transit.register();
		GuildHooks.register();
		Farming.register();
		Husbandry.register();
		Fishing.register();
		Smithing.register();
		Alchemy.register();
		Exploration.register();
		Hazards.register();
		Economy.register();
		dev.kaileh57.deepworld.living.Living.register();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> Meals.server_bonus_slots = ServerState.get(server).bonus_meal_slots);
		ServerLifecycleEvents.SERVER_STARTED.register(ForgingRecipes::reload);
		ServerLifecycleEvents.SERVER_STARTED.register(dev.kaileh57.deepworld.living.Live::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> dev.kaileh57.deepworld.living.Live.stop());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> ForgingRecipes.reload(server));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tick = server.getTickCount();
			HeatSystem.tick(server, tick);
			Meals.tick(server, tick);
			Spoilage.tick(server, tick);
			Lifts.tick(server);
			Roads.tick(server, tick);
			GuildHooks.tick(server, tick);
			Smithing.tick(server, tick);
			Blasting.tick(server);
			Landmarks.tick(server, tick);
			Exploration.tick(server, tick);
			Hazards.tick(server, tick);
			Settlements.tick(server, tick);
			dev.kaileh57.deepworld.living.Events.tick(server, tick);
			dev.kaileh57.deepworld.living.Live.tick(server, tick);
			dev.kaileh57.deepworld.hud.Shake.tick(server);
			if (tick % 20 == 0) {
				Hud.flush(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Hud.forget(handler.getPlayer().getUUID());
			dev.kaileh57.deepworld.hud.Shake.forget(handler.getPlayer().getUUID());
		});
		DataExport.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registry_access, environment) -> {
			SurveyCommand.register(dispatcher);
			dev.kaileh57.deepworld.living.WikiCommand.register(dispatcher);
		});
	}
}
