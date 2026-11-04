package dev.kaileh57.deepworld.hazard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.kaileh57.deepworld.DeepWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class HazardConfig {
	public boolean cave_ins = true;
	public boolean firedamp = true;
	public boolean cold_water = true;
	public boolean echoes = true;
	public boolean fumes = true;
	public boolean heat = true;
	public boolean magma_tides = true;
	public boolean darkness = true;
	public boolean sound = true;
	public boolean spoilage = true;
	public boolean tremors = true;

	public static HazardConfig current = new HazardConfig();

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("deepworld.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(file)) {
				current = gson.fromJson(Files.readString(file), HazardConfig.class);
			}
			Files.writeString(file, gson.toJson(current));
		} catch (Exception error) {
			DeepWorld.logger.error("could not read deepworld.json, using defaults", error);
		}
	}
}
