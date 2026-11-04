package dev.kaileh57.deepworld;

import dev.kaileh57.deepworld.command.SurveyCommand;
import dev.kaileh57.deepworld.export.DataExport;
import dev.kaileh57.deepworld.registry.DwItems;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
		DwItems.register();
		DataExport.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registry_access, environment) -> SurveyCommand.register(dispatcher));
	}
}
