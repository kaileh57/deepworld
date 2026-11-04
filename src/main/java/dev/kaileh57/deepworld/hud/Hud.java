package dev.kaileh57.deepworld.hud;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Hud {
	private static final Map<UUID, Map<String, Component>> segments = new HashMap<>();
	private static final String[] order = {"heat", "meals", "drink", "hazard"};

	private Hud() {
	}

	public static void set(ServerPlayer player, String key, Component value) {
		Map<String, Component> map = segments.computeIfAbsent(player.getUUID(), id -> new LinkedHashMap<>());
		if (value == null) {
			map.remove(key);
		} else {
			map.put(key, value);
		}
	}

	public static void flush(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<String, Component> map = segments.get(player.getUUID());
			if (map == null || map.isEmpty()) {
				continue;
			}
			MutableComponent line = Component.empty();
			boolean first = true;
			for (String key : order) {
				Component part = map.get(key);
				if (part == null) {
					continue;
				}
				if (!first) {
					line.append(Component.literal("   "));
				}
				line.append(part);
				first = false;
			}
			player.sendOverlayMessage(line);
		}
	}

	public static void forget(UUID id) {
		segments.remove(id);
	}
}
