package dev.kaileh57.deepworld.exploration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.guild.Guild;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class Landmarks extends SavedData {
	public record Entry(String kind, BlockPos pos) {
		public static final Codec<Entry> codec = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Entry::kind),
			BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos)
		).apply(i, Entry::new));
	}

	public List<Entry> entries = new ArrayList<>();
	public static final Codec<Landmarks> codec = Entry.codec.listOf().xmap(list -> {
		Landmarks landmarks = new Landmarks();
		landmarks.entries = new ArrayList<>(list);
		return landmarks;
	}, landmarks -> landmarks.entries);
	public static final SavedDataType<Landmarks> type = new SavedDataType<>(DeepWorld.id("landmarks"), Landmarks::new, codec, null);
	private static final ConcurrentLinkedQueue<Entry> incoming = new ConcurrentLinkedQueue<>();

	public static void record(String kind, BlockPos pos) {
		incoming.add(new Entry(kind, pos.immutable()));
	}

	public static Landmarks get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(type);
	}

	public static List<Entry> all(MinecraftServer server) {
		drain(server);
		return get(server).entries;
	}

	private static void drain(MinecraftServer server) {
		Landmarks landmarks = get(server);
		Entry next;
		while ((next = incoming.poll()) != null) {
			Entry entry = next;
			if (landmarks.entries.stream().noneMatch(e -> e.kind().equals(entry.kind()) && e.pos().equals(entry.pos()))) {
				landmarks.entries.add(entry);
				landmarks.setDirty();
			}
		}
	}

	public static String display(String kind) {
		return kind.replace('_', ' ').replace("miners", "miner's");
	}

	public static Entry nearest(MinecraftServer server, BlockPos from, String kind) {
		Entry best = null;
		double best_distance = Double.MAX_VALUE;
		for (Entry entry : all(server)) {
			if (kind != null && !entry.kind().equals(kind)) {
				continue;
			}
			if (entry.kind().equals("pump") || entry.kind().equals("cathedral")) {
				continue;
			}
			double distance = entry.pos().distSqr(from);
			if (distance < best_distance) {
				best_distance = distance;
				best = entry;
			}
		}
		return best;
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 40 != 0) {
			return;
		}
		drain(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (Entry entry : get(server).entries) {
				if (entry.kind().equals("pump") || entry.kind().equals("cathedral")) {
					continue;
				}
				if (entry.pos().distSqr(player.blockPosition()) > 24 * 24) {
					continue;
				}
				String key = "found_" + entry.kind() + "_" + entry.pos().asLong();
				if (Guild.counter(player, key) > 0) {
					continue;
				}
				Guild.add(player, key, 1);
				Guild.add(player, "landmarks_found", 1);
				Logbook.add(player, "found the " + display(entry.kind()) + " at y " + entry.pos().getY());
				dev.kaileh57.deepworld.hud.Hud.notice(player, Component.literal("landmark found: " + display(entry.kind())).withStyle(ChatFormatting.GOLD));
			}
		}
	}
}
