package dev.kaileh57.deepworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kaileh57.deepworld.DeepWorld;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class ServerState extends SavedData {
	public int bonus_meal_slots;
	public boolean gourmet_awarded;
	public boolean tasting_awarded;
	public boolean breakthrough_done;
	public String breakthrough_player = "";
	public List<String> events = new ArrayList<>();

	public static final Codec<ServerState> codec = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("bonus_meal_slots", 0).forGetter(s -> s.bonus_meal_slots),
		Codec.BOOL.optionalFieldOf("gourmet_awarded", false).forGetter(s -> s.gourmet_awarded),
		Codec.BOOL.optionalFieldOf("tasting_awarded", false).forGetter(s -> s.tasting_awarded),
		Codec.BOOL.optionalFieldOf("breakthrough_done", false).forGetter(s -> s.breakthrough_done),
		Codec.STRING.optionalFieldOf("breakthrough_player", "").forGetter(s -> s.breakthrough_player),
		Codec.STRING.listOf().optionalFieldOf("events", List.of()).forGetter(s -> s.events)
	).apply(i, (slots, gourmet, tasting, breakthrough, who, events) -> {
		ServerState state = new ServerState();
		state.bonus_meal_slots = slots;
		state.gourmet_awarded = gourmet;
		state.tasting_awarded = tasting;
		state.breakthrough_done = breakthrough;
		state.breakthrough_player = who;
		state.events = new ArrayList<>(events);
		return state;
	}));

	public static final SavedDataType<ServerState> type = new SavedDataType<>(DeepWorld.id("server_state"), ServerState::new, codec, null);

	public static ServerState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(type);
	}

	public void log(String event) {
		events.add(0, event);
		while (events.size() > 50) {
			events.remove(events.size() - 1);
		}
		setDirty();
	}
}
