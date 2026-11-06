package dev.kaileh57.deepworld.living;

import dev.kaileh57.deepworld.command.SelfTest;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.cooking.Dishes;
import dev.kaileh57.deepworld.cooking.Meals;
import dev.kaileh57.deepworld.world.Layers;
import dev.kaileh57.deepworld.world.ServerState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class Living {
	private Living() {
	}

	public static List<String> tasting_set() {
		List<List<String>> layers = Feast.tasting_layers();
		List<String> picked = new ArrayList<>();
		for (List<String> keys : layers) {
			for (CookData.Dish dish : CookData.dishes.values()) {
				if (!picked.contains(dish.id()) && dish.ingredients().stream().anyMatch(keys::contains)) {
					picked.add(dish.id());
					break;
				}
			}
		}
		return picked;
	}

	public static void register() {
		ExtraTests.register();
		Events.register();
		SelfTest.add("feast feeds the table with merged buffs", server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = SelfTest.test_pos(level, 30);
			FakePlayer host = FakePlayer.get(level);
			host.removeAllEffects();
			List<ItemStack> plates = new ArrayList<>();
			Map<String, Integer> expected = new HashMap<>();
			for (CookData.Dish dish : CookData.dishes.values()) {
				if (plates.size() >= 4 || dish.buffs().isEmpty() || !dish.kind().equals("dish")) {
					continue;
				}
				plates.add(Dishes.make(dish, 3, "tester"));
				dish.buffs().forEach((key, value) -> expected.merge(key, value, Math::max));
			}
			String note = Feast.serve(host, level, pos, plates);
			Meals.Meal feast = Meals.of(host).stream().filter(m -> m.id().equals("feast")).findFirst().orElse(null);
			boolean absorbed = host.hasEffect(MobEffects.ABSORPTION);
			host.setAttached(Meals.meals, new ArrayList<>());
			host.removeAllEffects();
			if (feast == null) {
				return "no feast meal: " + note;
			}
			return feast.buffs().keySet().containsAll(expected.keySet()) && absorbed ? null : "buffs " + feast.buffs() + " expected " + expected + " absorbed " + absorbed;
		});
		SelfTest.add("tasting menu needs every layer", server -> {
			List<String> full = tasting_set();
			if (full.size() < Feast.tasting_layers().size()) {
				return "only " + full.size() + " layers have dishes";
			}
			boolean complete = Feast.covers_all_layers(full);
			boolean short_menu = Feast.covers_all_layers(full.subList(0, full.size() - 1));
			return complete && !short_menu ? null : "complete " + complete + " short " + short_menu;
		});
		SelfTest.add("records keep the best", server -> {
			Records records = Records.get(server);
			Records.Entry before = records.best.get("feasts");
			int base = before == null ? 0 : before.value();
			boolean first = Records.offer(server, "tester_a", "feasts", base + 5);
			boolean lower = Records.offer(server, "tester_b", "feasts", base + 2);
			String name = records.best.get("feasts").name();
			boolean live = Live.snapshot(server).getAsJsonArray("records").toString().contains("tester_a");
			if (before == null) {
				records.best.remove("feasts");
			} else {
				records.best.put("feasts", before);
			}
			records.setDirty();
			return first && !lower && name.equals("tester_a") && live ? null : "first " + first + " lower " + lower + " holder " + name + " live " + live;
		});
		SelfTest.add("weekly goal fills and grants a slot", server -> {
			ServerState state = ServerState.get(server);
			String counter = state.goal_counter;
			int target = state.goal_target;
			int progress = state.goal_progress;
			long bonus = state.bonus_until;
			int slots_before = Meals.slot_count();
			state.goal_counter = "selftest_goal";
			state.goal_target = 3;
			state.goal_progress = 0;
			state.bonus_until = 0;
			Meals.temp_bonus = false;
			Events.goal_add(server, "selftest_goal", 2);
			boolean partial = state.goal_progress == 2 && !Meals.temp_bonus;
			Events.goal_add(server, "selftest_goal", 5);
			boolean met = state.goal_progress == 3 && Meals.temp_bonus && Meals.slot_count() == slots_before + 1;
			state.goal_counter = counter;
			state.goal_target = target;
			state.goal_progress = progress;
			state.bonus_until = bonus;
			Meals.temp_bonus = bonus > System.currentTimeMillis();
			state.events.removeIf(e -> e.equals("the server goal was met"));
			state.setDirty();
			return partial && met ? null : "partial " + partial + " met " + met;
		});
		SelfTest.add("tremors open fresh seams", server -> {
			ServerLevel level = server.overworld();
			Layers.Layer layer = Layers.all().get(1);
			BlockPos center = new BlockPos(SelfTest.test_pos(level, 31).getX(), layer.min_y() + 20, 0);
			Map<BlockPos, BlockState> saved = new HashMap<>();
			for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -4, -4), center.offset(4, 4, 4))) {
				saved.put(pos.immutable(), level.getBlockState(pos));
				level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
			}
			int placed = Events.seam(level, center, layer, RandomSource.create(7), 2);
			int ores = 0;
			for (BlockPos pos : saved.keySet()) {
				if (layer.ores().stream().anyMatch(id -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString().equals(id))) {
					ores++;
				}
			}
			saved.forEach((pos, state) -> level.setBlock(pos, state, 2));
			return placed > 0 && ores == placed ? null : "placed " + placed + " found " + ores + " in " + layer.name();
		});
		SelfTest.add("tremors leave the surface alone", server -> {
			ServerLevel level = server.overworld();
			BlockPos column = SelfTest.test_pos(level, 32);
			int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, column.getX(), column.getZ());
			boolean surface = Events.underground(level, new BlockPos(column.getX(), top - 2, column.getZ()));
			boolean deep = Events.underground(level, new BlockPos(column.getX(), top - 80, column.getZ()));
			return !surface && deep ? null : "surface " + surface + " deep " + deep + " top " + top;
		});
		SelfTest.add("wiki links resolve names", server -> {
			String dish = CookData.dishes.keySet().iterator().next();
			String block = Content.blocks.keySet().iterator().next();
			String a = WikiCommand.path_for(dish);
			String b = WikiCommand.path_for(block);
			String c = WikiCommand.path_for("not a thing");
			return ("ref/dishes/" + dish + "/").equals(a) && ("ref/blocks/" + block + "/").equals(b) && c == null ? null : a + " " + b + " " + c;
		});
	}
}
