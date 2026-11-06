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
	public String goal_counter = "";
	public int goal_target;
	public int goal_progress;
	public long goal_ends;
	public long bonus_until;
	public long next_tremor;
	public long boost_until;
	public long breach;

	public static final Codec<ServerState> codec = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("bonus_meal_slots", 0).forGetter(s -> s.bonus_meal_slots),
		Codec.BOOL.optionalFieldOf("gourmet_awarded", false).forGetter(s -> s.gourmet_awarded),
		Codec.BOOL.optionalFieldOf("tasting_awarded", false).forGetter(s -> s.tasting_awarded),
		Codec.BOOL.optionalFieldOf("breakthrough_done", false).forGetter(s -> s.breakthrough_done),
		Codec.STRING.optionalFieldOf("breakthrough_player", "").forGetter(s -> s.breakthrough_player),
		Codec.STRING.listOf().optionalFieldOf("events", List.of()).forGetter(s -> s.events),
		Codec.STRING.optionalFieldOf("goal_counter", "").forGetter(s -> s.goal_counter),
		Codec.INT.optionalFieldOf("goal_target", 0).forGetter(s -> s.goal_target),
		Codec.INT.optionalFieldOf("goal_progress", 0).forGetter(s -> s.goal_progress),
		Codec.LONG.optionalFieldOf("goal_ends", 0L).forGetter(s -> s.goal_ends),
		Codec.LONG.optionalFieldOf("bonus_until", 0L).forGetter(s -> s.bonus_until),
		Codec.LONG.optionalFieldOf("next_tremor", 0L).forGetter(s -> s.next_tremor),
		Codec.LONG.optionalFieldOf("boost_until", 0L).forGetter(s -> s.boost_until),
		Codec.LONG.optionalFieldOf("breach", 0L).forGetter(s -> s.breach)
	).apply(i, (slots, gourmet, tasting, breakthrough, who, events, goal_counter, goal_target, goal_progress, goal_ends, bonus_until, next_tremor, boost_until, breach) -> {
		ServerState state = new ServerState();
		state.bonus_meal_slots = slots;
		state.gourmet_awarded = gourmet;
		state.tasting_awarded = tasting;
		state.breakthrough_done = breakthrough;
		state.breakthrough_player = who;
		state.events = new ArrayList<>(events);
		state.goal_counter = goal_counter;
		state.goal_target = goal_target;
		state.goal_progress = goal_progress;
		state.goal_ends = goal_ends;
		state.bonus_until = bonus_until;
		state.next_tremor = next_tremor;
		state.boost_until = boost_until;
		state.breach = breach;
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
