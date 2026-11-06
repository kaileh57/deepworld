package dev.kaileh57.deepworld.transit;

import com.mojang.serialization.Codec;
import dev.kaileh57.deepworld.DeepWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class RailNetwork extends SavedData {
	public Map<String, Long> stations = new TreeMap<>();
	public static final Codec<RailNetwork> codec = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(map -> {
		RailNetwork n = new RailNetwork();
		n.stations.putAll(map);
		return n;
	}, n -> n.stations);
	public static final SavedDataType<RailNetwork> type = new SavedDataType<>(DeepWorld.id("rail_network"), RailNetwork::new, codec, null);

	public static RailNetwork get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(type);
	}

	public static List<String> names(MinecraftServer server) {
		return new ArrayList<>(get(server).stations.keySet());
	}

	public static void put(MinecraftServer server, String name, BlockPos pos) {
		RailNetwork network = get(server);
		network.stations.values().removeIf(value -> value == pos.asLong());
		if (!name.isEmpty()) {
			network.stations.put(name, pos.asLong());
		}
		network.setDirty();
	}

	public static void remove(MinecraftServer server, BlockPos pos) {
		RailNetwork network = get(server);
		if (network.stations.values().removeIf(value -> value == pos.asLong())) {
			network.setDirty();
		}
	}
}
