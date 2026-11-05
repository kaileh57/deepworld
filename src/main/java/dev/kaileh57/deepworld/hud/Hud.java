package dev.kaileh57.deepworld.hud;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Hud {
	private record Notice(Component text, long until) {
	}

	private static final Map<UUID, Map<String, Component>> segments = new HashMap<>();
	private static final Map<UUID, Notice> notices = new HashMap<>();
	private static final String[] order = {"heat", "meals", "drink", "hazard"};
	public static final int notice_ticks = 80;

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

	public static void notice(ServerPlayer player, Component text) {
		if (player.level().getServer() == null) {
			return;
		}
		notices.put(player.getUUID(), new Notice(text, player.level().getServer().getTickCount() + notice_ticks));
		player.sendOverlayMessage(text);
	}

	public static void notice_all(MinecraftServer server, Component text) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			notice(player, text);
		}
	}

	public static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	public static void title_all(MinecraftServer server, Component title, Component subtitle) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			title(player, title, subtitle);
		}
	}

	public static void flush(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Notice notice = notices.get(player.getUUID());
			if (notice != null) {
				if (notice.until() > now) {
					player.sendOverlayMessage(notice.text());
					continue;
				}
				notices.remove(player.getUUID());
			}
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
		notices.remove(id);
	}
}
