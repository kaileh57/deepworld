package dev.kaileh57.deepworld.guild;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class Guild {
	public record State(Map<String, Integer> counters, List<String> milestones, List<String> focus, long focus_changed) {
		public static final Codec<State> codec = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("counters").forGetter(State::counters),
			Codec.STRING.listOf().fieldOf("milestones").forGetter(State::milestones),
			Codec.STRING.listOf().fieldOf("focus").forGetter(State::focus),
			Codec.LONG.fieldOf("focus_changed").forGetter(State::focus_changed)
		).apply(i, State::new));

		public static State empty() {
			return new State(new HashMap<>(), new ArrayList<>(), new ArrayList<>(), -1000000L);
		}
	}

	public record Discipline(String id, String name, String icon, String perk) {
	}

	public record Milestone(String discipline, String id, String name, String counter, int target, String text) {
	}

	public static final AttachmentType<State> state = AttachmentRegistry.create(DeepWorld.id("guild"), builder -> builder.persistent(State.codec).initializer(State::empty).copyOnDeath());
	public static final Map<String, Discipline> disciplines = new LinkedHashMap<>();
	public static final List<Milestone> milestones = new ArrayList<>();
	public static final int max_focus = 2;
	public static final long focus_cooldown = 24000L;

	private Guild() {
	}

	public static void load() {
		JsonObject content = Content.data();
		for (JsonElement element : content.getAsJsonArray("disciplines")) {
			JsonObject d = element.getAsJsonObject();
			disciplines.put(d.get("id").getAsString(), new Discipline(d.get("id").getAsString(), d.get("name").getAsString(), d.get("icon").getAsString(), d.get("perk").getAsString()));
		}
		for (JsonElement element : content.getAsJsonArray("milestones")) {
			JsonObject m = element.getAsJsonObject();
			milestones.add(new Milestone(m.get("discipline").getAsString(), m.get("id").getAsString(), m.get("name").getAsString(), m.get("counter").getAsString(), m.get("target").getAsInt(), m.get("text").getAsString()));
		}
	}

	public static State of(ServerPlayer player) {
		return player.getAttachedOrCreate(state);
	}

	private static State copy(State current) {
		return new State(new HashMap<>(current.counters()), new ArrayList<>(current.milestones()), new ArrayList<>(current.focus()), current.focus_changed());
	}

	public static boolean focused(ServerPlayer player, String discipline) {
		return player != null && of(player).focus().contains(discipline);
	}

	public static int counter(ServerPlayer player, String counter) {
		return of(player).counters().getOrDefault(counter, 0);
	}

	public static void add(ServerPlayer player, String counter, int amount) {
		if (player == null || amount == 0) {
			return;
		}
		State next = copy(of(player));
		next.counters().merge(counter, amount, Integer::sum);
		player.setAttached(state, next);
		check(player);
	}

	public static void set_max(ServerPlayer player, String counter, int value) {
		if (player == null || value <= counter(player, counter)) {
			return;
		}
		State next = copy(of(player));
		next.counters().put(counter, value);
		player.setAttached(state, next);
		check(player);
	}

	private static void check(ServerPlayer player) {
		State current = of(player);
		for (Milestone milestone : milestones) {
			if (current.milestones().contains(milestone.id()) || current.counters().getOrDefault(milestone.counter(), 0) < milestone.target()) {
				continue;
			}
			State next = copy(of(player));
			next.milestones().add(milestone.id());
			player.setAttached(state, next);
			Discipline discipline = disciplines.get(milestone.discipline());
			player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().name() + " earned ").withStyle(ChatFormatting.GRAY).append(Component.literal(milestone.name()).withStyle(ChatFormatting.GOLD)).append(Component.literal(" (" + (discipline == null ? milestone.discipline() : discipline.name()) + ")").withStyle(ChatFormatting.GRAY)), false);
			player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.0F);
			Rewards.grant(player, milestone);
		}
	}

	public static String toggle_focus(ServerPlayer player, String discipline) {
		State current = of(player);
		long now = player.level().getGameTime();
		State next = copy(current);
		if (current.focus().contains(discipline)) {
			next.focus().remove(discipline);
			player.setAttached(state, next);
			return "dropped " + discipline;
		}
		if (current.focus().size() >= max_focus) {
			return "drop a focus first (max " + max_focus + ")";
		}
		if (now - current.focus_changed() < focus_cooldown && !current.focus().isEmpty()) {
			return "you can change focus again in " + ((focus_cooldown - (now - current.focus_changed())) / 1200) + " min";
		}
		next.focus().add(discipline);
		player.setAttached(state, new State(next.counters(), next.milestones(), next.focus(), now));
		return "now focused on " + discipline;
	}
}
