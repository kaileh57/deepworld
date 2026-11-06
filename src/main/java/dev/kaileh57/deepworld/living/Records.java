package dev.kaileh57.deepworld.living;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kaileh57.deepworld.DeepWorld;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class Records extends SavedData {
	public record Entry(String name, int value) {
		public static final Codec<Entry> codec = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Entry::name),
			Codec.INT.fieldOf("value").forGetter(Entry::value)
		).apply(i, Entry::new));
	}

	public static final Map<String, String> tracked = new LinkedHashMap<>();

	static {
		tracked.put("deepest", "deepest descent");
		tracked.put("dishes_cooked", "dishes cooked");
		tracked.put("feasts", "feasts served");
		tracked.put("landmarks_found", "landmarks found");
		tracked.put("pages_found", "journal pages");
		tracked.put("commissions_done", "commissions done");
		tracked.put("forged", "items forged");
		tracked.put("masterworks", "masterworks");
		tracked.put("crops_harvested", "crops harvested");
		tracked.put("animals_bred", "animals bred");
		tracked.put("deep_kills", "deep kills");
		tracked.put("trophies", "trophies taken");
		tracked.put("rails_laid", "rails laid");
	}

	public Map<String, Entry> best = new HashMap<>();
	public static final Codec<Records> codec = Codec.unboundedMap(Codec.STRING, Entry.codec).xmap(map -> {
		Records r = new Records();
		r.best.putAll(map);
		return r;
	}, r -> r.best);
	public static final SavedDataType<Records> type = new SavedDataType<>(DeepWorld.id("records"), Records::new, codec, null);

	public static Records get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(type);
	}

	public static boolean offer(MinecraftServer server, String name, String counter, int value) {
		if (!tracked.containsKey(counter)) {
			return false;
		}
		Records records = get(server);
		Entry current = records.best.get(counter);
		if (current != null && current.value() >= value) {
			return false;
		}
		records.best.put(counter, new Entry(name, value));
		records.setDirty();
		return true;
	}
}
