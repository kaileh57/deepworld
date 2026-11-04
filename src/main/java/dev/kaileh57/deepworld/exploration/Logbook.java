package dev.kaileh57.deepworld.exploration;

import com.mojang.serialization.Codec;
import dev.kaileh57.deepworld.DeepWorld;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

public final class Logbook {
	public static final AttachmentType<List<String>> entries = AttachmentRegistry.create(DeepWorld.id("logbook"), builder -> builder.persistent(Codec.STRING.listOf()).initializer(ArrayList::new).copyOnDeath());

	private Logbook() {
	}

	public static void add(ServerPlayer player, String text) {
		if (player == null) {
			return;
		}
		List<String> list = new ArrayList<>(player.getAttachedOrCreate(entries));
		long day = player.level().getGameTime() / 24000L + 1;
		list.add("day " + day + ": " + text);
		while (list.size() > 200) {
			list.remove(0);
		}
		player.setAttached(entries, list);
	}

	public static List<String> of(ServerPlayer player) {
		return player.getAttachedOrCreate(entries);
	}
}
