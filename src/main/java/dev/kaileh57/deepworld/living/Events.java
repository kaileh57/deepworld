package dev.kaileh57.deepworld.living;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.Meals;
import dev.kaileh57.deepworld.hazard.Hazards;
import dev.kaileh57.deepworld.hud.Hud;
import dev.kaileh57.deepworld.hud.Shake;
import dev.kaileh57.deepworld.world.Layers;
import dev.kaileh57.deepworld.world.ServerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

public final class Events {
	public static final long week_ms = 7L * 24 * 60 * 60 * 1000;
	public static final int breakthrough_y = 0;
	public static final long boost_ticks = 24000L;
	public static final int boost_radius = 512;

	public static boolean boosted(MinecraftServer server, net.minecraft.world.phys.Vec3 where) {
		ServerState state = ServerState.get(server);
		if (!state.breakthrough_done || server.overworld().getGameTime() >= state.boost_until) {
			return false;
		}
		BlockPos breach = BlockPos.of(state.breach);
		return where == null || where.distanceToSqr(breach.getX(), breach.getY(), breach.getZ()) < (double) boost_radius * boost_radius;
	}

	public static void register() {
		net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			var key = table.unwrapKey().orElse(null);
			if (key == null || !(key.equals(net.minecraft.world.level.storage.loot.BuiltInLootTables.NETHER_BRIDGE) || key.identifier().getPath().startsWith("chests/bastion"))) {
				return;
			}
			if (boosted(context.getLevel().getServer(), context.getOptional(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN))) {
				java.util.List<net.minecraft.world.item.ItemStack> extra = new java.util.ArrayList<>();
				for (net.minecraft.world.item.ItemStack stack : drops) {
					extra.add(stack.copy());
				}
				drops.addAll(extra);
			}
		});
	}

	private Events() {
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 100 != 0) {
			return;
		}
		ServerState state = ServerState.get(server);
		long now = System.currentTimeMillis();
		Meals.temp_bonus = state.bonus_until > now;
		if (state.goal_counter.isEmpty() || now > state.goal_ends) {
			new_goal(server, state, now);
		}
		if (!state.breakthrough_done) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.level() == server.overworld() && player.getBlockY() < breakthrough_y && !player.isSpectator()) {
					breakthrough(server, player);
					break;
				}
			}
		}
		ServerLevel level = server.overworld();
		long time = level.getGameTime();
		if (state.next_tremor == 0) {
			state.next_tremor = time + 36000 + level.getRandom().nextInt(36000);
			state.setDirty();
		}
		if (time >= state.next_tremor) {
			state.next_tremor = time + 36000 + level.getRandom().nextInt(36000);
			state.setDirty();
			if (Hazards.config().tremors) {
				List<ServerPlayer> deep = new ArrayList<>();
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.level() == level && player.getBlockY() < 1100 && !player.isSpectator()) {
						deep.add(player);
					}
				}
				if (!deep.isEmpty()) {
					tremor(level, deep.get(level.getRandom().nextInt(deep.size())));
				}
			}
		}
	}

	public static void breakthrough(MinecraftServer server, ServerPlayer player) {
		ServerState state = ServerState.get(server);
		if (state.breakthrough_done) {
			return;
		}
		String name = player.getGameProfile().name();
		state.breakthrough_done = true;
		state.breakthrough_player = name;
		state.boost_until = server.overworld().getGameTime() + boost_ticks;
		state.breach = player.blockPosition().asLong();
		state.log(name + " broke through to the nether stratum");
		state.setDirty();
		Hud.title_all(server, Component.literal("breakthrough").withStyle(ChatFormatting.GOLD), Component.literal(name + " reached the nether stratum").withStyle(ChatFormatting.GRAY));
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			other.level().playSound(null, other.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
		dev.kaileh57.deepworld.exploration.Logbook.add(player, "first to break through to the nether stratum");
	}

	public static boolean underground(ServerLevel level, BlockPos pos) {
		return pos.getY() < level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.getX(), pos.getZ()) - 12 && !level.canSeeSky(pos);
	}

	public static int tremor(ServerLevel level, ServerPlayer center) {
		Layers.Layer layer = Layers.at(center.getBlockY());
		if (layer == null) {
			return 0;
		}
		RandomSource random = level.getRandom();
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (layer.contains(player.getBlockY()) && !player.isSpectator()) {
				near.add(player);
			}
		}
		if (Hazards.config().cave_ins) {
			for (ServerPlayer player : near) {
				for (int attempt = 0; attempt < 6; attempt++) {
					BlockPos pos = player.blockPosition().offset(random.nextInt(25) - 12, 0, random.nextInt(25) - 12);
					if (underground(level, pos) && level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
						Hazards.maybe_cave_in(level, pos, 0.25F);
					}
				}
			}
		}
		int ore = underground(level, center.blockPosition()) ? seam(level, center.blockPosition(), layer, random, 16) : 0;
		Component text = Component.literal("a tremor rolls through the " + layer.name() + (ore > 0 ? ". a fresh seam opened nearby" : "")).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : near) {
			Shake.start(player, 50, 3.0F);
			level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.AMBIENT, 1.2F, 0.4F);
			Hud.notice(player, text);
		}
		ServerState.get(level.getServer()).log("a tremor shook the " + layer.name());
		return ore;
	}

	public static int seam(ServerLevel level, BlockPos around, Layers.Layer layer, RandomSource random, int spread) {
		if (layer.ores().isEmpty()) {
			return 0;
		}
		Block ore = BuiltInRegistries.BLOCK.getValue(Identifier.parse(layer.ores().get(random.nextInt(layer.ores().size()))));
		for (int attempt = 0; attempt < 20; attempt++) {
			BlockPos start = around.offset(random.nextInt(spread * 2 + 1) - spread, random.nextInt(spread + 1) - spread / 2, random.nextInt(spread * 2 + 1) - spread);
			if (!layer.contains(start.getY()) || !buried(level, start)) {
				continue;
			}
			int placed = 0;
			BlockPos.MutableBlockPos pos = start.mutable();
			for (int step = 0; step < 10; step++) {
				if (buried(level, pos)) {
					level.setBlock(pos, ore.defaultBlockState(), Block.UPDATE_CLIENTS);
					placed++;
				}
				pos.move(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
			}
			if (placed > 0) {
				return placed;
			}
		}
		return 0;
	}

	private static boolean buried(ServerLevel level, BlockPos pos) {
		if (!solid_stone(level.getBlockState(pos)) || !underground(level, pos)) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			BlockState next = level.getBlockState(pos.relative(direction));
			if (next.isAir() || !next.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static boolean solid_stone(BlockState state) {
		return state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.getBlock() instanceof dev.kaileh57.deepworld.block.DwStoneBlock;
	}

	public static List<JsonObject> goals() {
		List<JsonObject> out = new ArrayList<>();
		for (JsonElement element : Content.data().getAsJsonArray("weekly_goals")) {
			out.add(element.getAsJsonObject());
		}
		return out;
	}

	public static void new_goal(MinecraftServer server, ServerState state, long now) {
		List<JsonObject> goals = goals();
		int week = (int) (now / week_ms);
		JsonObject goal = goals.get(Math.floorMod(week * 5 + 3, goals.size()));
		state.goal_counter = goal.get("counter").getAsString();
		state.goal_target = goal.get("target").getAsInt();
		state.goal_progress = 0;
		state.goal_ends = (week + 1L) * week_ms;
		state.setDirty();
		Hud.notice_all(server, Component.literal("new weekly goal: " + goal.get("name").getAsString()).withStyle(ChatFormatting.GOLD));
	}

	public static void goal_add(MinecraftServer server, String counter, int amount) {
		if (amount <= 0) {
			return;
		}
		ServerState state = ServerState.get(server);
		if (!counter.equals(state.goal_counter) || state.goal_progress >= state.goal_target) {
			return;
		}
		state.goal_progress = Math.min(state.goal_target, state.goal_progress + amount);
		state.setDirty();
		if (state.goal_progress >= state.goal_target) {
			state.bonus_until = System.currentTimeMillis() + week_ms;
			Meals.temp_bonus = true;
			state.log("the server goal was met");
			Hud.notice_all(server, Component.literal("weekly goal met. an extra meal slot for a week").withStyle(ChatFormatting.GOLD));
		}
	}

	public static Optional<String> describe_goal(MinecraftServer server) {
		ServerState state = ServerState.get(server);
		if (state.goal_counter.isEmpty()) {
			return Optional.empty();
		}
		String name = state.goal_counter;
		for (JsonObject goal : goals()) {
			if (goal.get("counter").getAsString().equals(state.goal_counter)) {
				name = goal.get("name").getAsString();
			}
		}
		return Optional.of(name + ": " + state.goal_progress + "/" + state.goal_target);
	}
}
