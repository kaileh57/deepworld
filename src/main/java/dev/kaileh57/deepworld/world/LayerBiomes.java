package dev.kaileh57.deepworld.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

public final class LayerBiomes {
	private static List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> entries;

	private LayerBiomes() {
	}

	public static void add_all(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> biomes) {
		for (Pair<Climate.ParameterPoint, ResourceKey<Biome>> entry : load()) {
			biomes.accept(entry);
		}
	}

	private static synchronized List<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> load() {
		if (entries != null) {
			return entries;
		}
		entries = new ArrayList<>();
		try (Reader reader = new InputStreamReader(LayerBiomes.class.getResourceAsStream("/deepworld_biome_params.json"), StandardCharsets.UTF_8)) {
			JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
			Climate.Parameter full = Climate.Parameter.span(-1.0F, 1.0F);
			for (JsonElement element : array) {
				JsonObject entry = element.getAsJsonObject();
				ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(entry.get("biome").getAsString()));
				Climate.ParameterPoint point = Climate.parameters(
					span(entry, "temperature"),
					span(entry, "humidity"),
					full,
					full,
					span(entry, "depth"),
					full,
					0.0F
				);
				entries.add(Pair.of(point, key));
			}
		} catch (Exception error) {
			throw new IllegalStateException("could not read layer biomes", error);
		}
		return entries;
	}

	private static Climate.Parameter span(JsonObject entry, String field) {
		JsonArray range = entry.getAsJsonArray(field);
		return Climate.Parameter.span(range.get(0).getAsFloat(), range.get(1).getAsFloat());
	}
}
