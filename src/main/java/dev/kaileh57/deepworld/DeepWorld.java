package dev.kaileh57.deepworld;

import dev.kaileh57.deepworld.command.SurveyCommand;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.Cooking;
import dev.kaileh57.deepworld.cooking.Meals;
import dev.kaileh57.deepworld.cooking.Spoilage;
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
		ServerLifecycleEvents.SERVER_STARTED.register(ForgingRecipes::reload);
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> ForgingRecipes.reload(server));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long tick = server.getTickCount();
			HeatSystem.tick(server, tick);
			Meals.tick(server, tick);
			Spoilage.tick(server, tick);
			if (tick % 20 == 0) {
				Hud.flush(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Hud.forget(handler.getPlayer().getUUID()));
		DataExport.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registry_access, environment) -> SurveyCommand.register(dispatcher));
	}
}
