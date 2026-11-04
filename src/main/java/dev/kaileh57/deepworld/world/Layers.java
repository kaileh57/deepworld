package dev.kaileh57.deepworld.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Layers {
	public record Layer(int number, String name, int min_y, int max_y, List<String> biomes, List<String> ores) {
		public boolean contains(int y) {
			return y >= min_y && y < max_y;
		}
	}

	private static List<Layer> all;

	private Layers() {
	}

	public static synchronized List<Layer> all() {
		if (all != null) {
			return all;
		}
		List<Layer> layers = new ArrayList<>();
		try (Reader reader = new InputStreamReader(Layers.class.getResourceAsStream("/deepworld_layers.json"), StandardCharsets.UTF_8)) {
			JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
			int number = 9;
			for (JsonElement element : array) {
				JsonObject entry = element.getAsJsonObject();
				JsonArray range = entry.getAsJsonArray("y");
				layers.add(new Layer(number, entry.get("name").getAsString(), range.get(0).getAsInt(), range.get(1).getAsInt(), strings(entry.getAsJsonArray("biomes")), strings(entry.getAsJsonArray("ores"))));
				number--;
			}
		} catch (Exception error) {
			throw new IllegalStateException("could not read layers", error);
		}
		all = layers;
		return all;
	}

	public static Layer at(int y) {
		for (Layer layer : all()) {
			if (layer.contains(y)) {
				return layer;
			}
		}
		return null;
	}

	private static List<String> strings(JsonArray array) {
		List<String> out = new ArrayList<>();
		for (JsonElement element : array) {
			out.add(element.getAsString());
		}
		return out;
	}
}
