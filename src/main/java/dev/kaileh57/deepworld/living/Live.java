package dev.kaileh57.deepworld.living;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.economy.Commissions;
import dev.kaileh57.deepworld.exploration.Landmarks;
import dev.kaileh57.deepworld.hazard.Hazards;
import dev.kaileh57.deepworld.world.Layers;
import dev.kaileh57.deepworld.world.ServerState;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Live {
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private static volatile String cached = "{}";
	private static HttpServer http;

	private Live() {
	}

	public static JsonObject snapshot(MinecraftServer server) {
		JsonObject root = new JsonObject();
		root.addProperty("updated", System.currentTimeMillis());
		JsonArray players = new JsonArray();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("name", player.getGameProfile().name());
			Layers.Layer layer = player.level() == server.overworld() ? Layers.at(player.getBlockY()) : null;
			entry.addProperty("layer", layer == null ? (player.level() == server.overworld() ? "surface" : player.level().dimension().identifier().getPath()) : layer.name());
			players.add(entry);
		}
		root.add("players", players);
		JsonArray records = new JsonArray();
		Records saved = Records.get(server);
		for (Map.Entry<String, String> tracked : Records.tracked.entrySet()) {
			Records.Entry best = saved.best.get(tracked.getKey());
			if (best != null) {
				JsonObject entry = new JsonObject();
				entry.addProperty("record", tracked.getValue());
				entry.addProperty("name", best.name());
				entry.addProperty("value", best.value());
				records.add(entry);
			}
		}
		root.add("records", records);
		JsonArray commissions = new JsonArray();
		for (Commissions.Commission commission : Commissions.get(server).open) {
			JsonObject entry = new JsonObject();
			entry.addProperty("wanted", commission.wanted());
			entry.addProperty("count", commission.count());
			entry.addProperty("poster", commission.poster_name());
			entry.addProperty("taken", !commission.acceptor().isEmpty());
			commissions.add(entry);
		}
		root.add("commissions", commissions);
		ServerState state = ServerState.get(server);
		Events.describe_goal(server).ifPresent(text -> root.addProperty("goal", text));
		root.addProperty("goal_met", state.goal_target > 0 && state.goal_progress >= state.goal_target);
		root.addProperty("breakthrough", state.breakthrough_done ? state.breakthrough_player : "");
		root.addProperty("landmarks", Landmarks.all(server).size());
		JsonArray events = new JsonArray();
		state.events.stream().limit(20).forEach(events::add);
		root.add("events", events);
		return root;
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 200 != 0) {
			return;
		}
		cached = gson.toJson(snapshot(server));
		try {
			Files.writeString(server.getServerDirectory().resolve("deepworld_live.json"), cached);
		} catch (Exception error) {
			DeepWorld.logger.warn("could not write deepworld_live.json");
		}
	}

	public static void start(MinecraftServer server) {
		cached = gson.toJson(snapshot(server));
		int port = Hazards.config().live_port;
		if (port <= 0) {
			return;
		}
		try {
			http = HttpServer.create(new InetSocketAddress(port), 0);
			http.createContext("/live.json", exchange -> {
				byte[] body = cached.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("content-type", "application/json");
				exchange.getResponseHeaders().add("access-control-allow-origin", "*");
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
			});
			http.start();
			DeepWorld.logger.info("live data on port {}", port);
		} catch (Exception error) {
			DeepWorld.logger.warn("could not open live data port {}", port);
			http = null;
		}
	}

	public static void stop() {
		if (http != null) {
			http.stop(0);
			http = null;
		}
	}
}
