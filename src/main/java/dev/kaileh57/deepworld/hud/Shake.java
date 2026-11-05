package dev.kaileh57.deepworld.hud;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

public final class Shake {
	private static final class State {
		int left;
		final int total;
		final float strength;
		float yaw;
		float pitch;

		State(int ticks, float strength) {
			this.left = ticks;
			this.total = ticks;
			this.strength = strength;
		}
	}

	private static final Map<UUID, State> active = new HashMap<>();
	private static final RandomSource random = RandomSource.create();

	private Shake() {
	}

	public static void start(ServerPlayer player, int ticks, float strength) {
		State previous = active.get(player.getUUID());
		State next = new State(ticks, strength);
		if (previous != null) {
			next.yaw = previous.yaw;
			next.pitch = previous.pitch;
		}
		active.put(player.getUUID(), next);
		player.connection.send(new ClientboundHurtAnimationPacket(player.getId(), random.nextFloat() * 360.0F));
	}

	public static boolean shaking(ServerPlayer player) {
		return active.containsKey(player.getUUID());
	}

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, State>> entries = active.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<UUID, State> entry = entries.next();
			State state = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				entries.remove();
				continue;
			}
			float yaw = 0.0F;
			float pitch = 0.0F;
			state.left--;
			if (state.left > 0) {
				float fade = state.strength * state.left / state.total;
				yaw = (random.nextFloat() * 2.0F - 1.0F) * fade;
				pitch = (random.nextFloat() * 2.0F - 1.0F) * fade * 0.6F;
			}
			player.connection.send(new ClientboundPlayerRotationPacket(yaw - state.yaw, true, pitch - state.pitch, true));
			state.yaw = yaw;
			state.pitch = pitch;
			if (state.left <= 0) {
				entries.remove();
			}
		}
	}

	public static void forget(UUID id) {
		active.remove(id);
	}
}
