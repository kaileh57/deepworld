package dev.kaileh57.deepworld.living;

import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.command.SelfTest;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.cooking.Dishes;
import dev.kaileh57.deepworld.cooking.FoodContainers;
import dev.kaileh57.deepworld.economy.ShopBlockEntity;
import dev.kaileh57.deepworld.economy.Shops;
import dev.kaileh57.deepworld.exploration.Exploration;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class ExtraTests {
	private ExtraTests() {
	}

	private static ItemStack dish(String id) {
		return Dishes.make(CookData.dishes.get(id), 2, "tester");
	}

	public static void register() {
		SelfTest.add("lunchbox packs a miner's bento", server -> {
			List<ItemStack> inputs = List.of(dish("sulfur_cured_jerky"), dish("pickled_glowcaps"), new ItemStack(Content.item("rice")), dish("aged_cave_cheese"));
			CookData.Dish result = Dishes.match("lunchbox", inputs);
			return result != null && result.id().equals("miners_bento") ? null : "got " + (result == null ? "nothing" : result.id());
		});
		SelfTest.add("jars preserve dishes", server -> {
			ItemStack jar = new ItemStack(Content.item("jar"), 2);
			ItemStack stew = dish("field_stew");
			String note = FoodContainers.seal(jar, stew);
			boolean ration_ok = FoodContainers.accepts(new ItemStack(Content.item("ration_pack")), 0, stew);
			boolean ration_fresh = FoodContainers.accepts(new ItemStack(Content.item("ration_pack")), 0, dish("field_stew"));
			return CookData.preserved(stew) && jar.getCount() == 1 && ration_ok && !ration_fresh ? null : note + " preserved " + CookData.preserved(stew) + " ration " + ration_ok + "/" + ration_fresh;
		});
		SelfTest.add("hamper keeps food while iced", server -> {
			ItemStack hamper = new ItemStack(Content.item("insulated_hamper"));
			FoodContainers.store(hamper, List.of(new ItemStack(Items.PACKED_ICE), dish("field_stew"), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
			FoodContainers.chill(hamper, 10);
			int iced = CookData.tag(FoodContainers.contents(hamper).get(1)).getIntOr("fresh", -1);
			NonNullList<ItemStack> list = FoodContainers.contents(hamper);
			list.set(0, ItemStack.EMPTY);
			FoodContainers.store(hamper, list);
			FoodContainers.chill(hamper, 10);
			int melted = CookData.tag(FoodContainers.contents(hamper).get(1)).getIntOr("fresh", -1);
			return iced == -1 && melted > 0 ? null : "iced " + iced + " melted " + melted;
		});
		SelfTest.add("cooking notes teach recipes up to their tier", server -> {
			net.fabricmc.fabric.api.entity.FakePlayer cook = net.fabricmc.fabric.api.entity.FakePlayer.get(server.overworld());
			cook.setAttached(dev.kaileh57.deepworld.cooking.Cookbook.learned, new java.util.ArrayList<>());
			int before = dev.kaileh57.deepworld.cooking.Cookbook.known(cook).size();
			CookData.Dish first = dev.kaileh57.deepworld.cooking.Cookbook.learn_random(cook, 1);
			boolean knows = first != null && dev.kaileh57.deepworld.cooking.Cookbook.knows(cook, first.id());
			int after = dev.kaileh57.deepworld.cooking.Cookbook.known(cook).size();
			String name = dev.kaileh57.deepworld.cooking.Cookbook.ingredient_name("deepworld:dish/bone_broth") + "|" + dev.kaileh57.deepworld.cooking.Cookbook.ingredient_name("minecraft:potato");
			cook.setAttached(dev.kaileh57.deepworld.cooking.Cookbook.learned, new java.util.ArrayList<>());
			return knows && first.tier() <= 1 && after == before + 1 && name.equals("bone broth|potato") ? null : "first " + (first == null ? "none" : first.id() + " t" + first.tier()) + " " + before + "->" + after + " " + name;
		});
		SelfTest.add("tide ruins build in lava", server -> {
			ServerLevel level = server.overworld();
			BlockPos origin = new BlockPos(-60, 1490, 40);
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, -4, -5), origin.offset(5, 3, 5))) {
				level.setBlock(pos, pos.getY() < origin.getY() - 2 ? Blocks.STONE.defaultBlockState() : pos.getY() <= origin.getY() ? Blocks.LAVA.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
			}
			boolean built = new dev.kaileh57.deepworld.exploration.LandmarkFeature("tide_ruin").build(level, level.getRandom(), origin);
			boolean chest = level.getBlockState(origin).is(Blocks.CHEST);
			if (level.getBlockEntity(origin) instanceof net.minecraft.world.Container container) {
				container.clearContent();
			}
			for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, -4, -5), origin.offset(5, 3, 5))) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
			return built && chest ? null : "built " + built + " chest " + chest;
		});
		SelfTest.add("explorer maps point at the nearest station", server -> {
			ServerLevel level = server.overworld();
			BlockPos target = dev.kaileh57.deepworld.exploration.ExplorerMaps.target(level, new BlockPos(200, 1300, 30), "terminal");
			ItemStack map = dev.kaileh57.deepworld.exploration.ExplorerMaps.draw(level, target, "terminal");
			return target.getX() == 250 && map.is(Items.FILLED_MAP) && map.getHoverName().getString().contains("deepline station") ? null : "target " + target + " " + map;
		});
		SelfTest.add("breakthrough boosts nether loot near the breach", server -> {
			dev.kaileh57.deepworld.world.ServerState state = dev.kaileh57.deepworld.world.ServerState.get(server);
			boolean done = state.breakthrough_done;
			long until = state.boost_until;
			long breach = state.breach;
			state.breakthrough_done = true;
			state.boost_until = server.overworld().getGameTime() + 100;
			state.breach = new BlockPos(0, -20, 0).asLong();
			boolean near = Events.boosted(server, new net.minecraft.world.phys.Vec3(30, -40, 30));
			boolean far = Events.boosted(server, new net.minecraft.world.phys.Vec3(2000, -40, 30));
			state.breakthrough_done = done;
			state.boost_until = until;
			state.breach = breach;
			state.setDirty();
			return near && !far ? null : "near " + near + " far " + far;
		});
		SelfTest.add("settlements grow stalls and wells", server -> {
			ServerLevel level = server.overworld();
			BlockPos a = new BlockPos(-20, 1500, 40);
			BlockPos b = new BlockPos(-10, 1500, 40);
			for (BlockPos spot : List.of(a, b)) {
				for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-2, -2, -2), spot.offset(2, 4, 2))) {
					level.setBlock(pos, pos.getY() < spot.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
			boolean stall = dev.kaileh57.deepworld.economy.Settlements.stall(level, a) && level.getBlockState(a).is(Blocks.BARREL);
			boolean well = dev.kaileh57.deepworld.economy.Settlements.well(level, b) && level.getBlockState(b.below()).is(Blocks.WATER);
			for (BlockPos spot : List.of(a, b)) {
				for (BlockPos pos : BlockPos.betweenClosed(spot.offset(-2, -2, -2), spot.offset(2, 4, 2))) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
			return stall && well ? null : "stall " + stall + " well " + well;
		});
		SelfTest.add("world border is the column width", server -> {
			double size = server.overworld().getWorldBorder().getSize();
			return size <= DeepWorld.world_border ? null : "border " + size;
		});
		SelfTest.add("cathedral lectern holds the tuning", server -> {
			BlockPos origin = new BlockPos(100, 750, 100);
			int[] notes = Exploration.cathedral_notes(origin);
			String text = Exploration.tuning_notes(origin).get(DataComponents.WRITTEN_BOOK_CONTENT).pages().get(0).raw().getString();
			return text.contains("north-west: " + notes[0]) && text.contains("south-east: " + notes[3]) ? null : text;
		});
		SelfTest.add("shops list on the live page", server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = SelfTest.test_pos(level, 33);
			level.setBlockAndUpdate(pos, Content.blocks.get("serving_counter").defaultBlockState());
			if (!(level.getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
				return "no counter";
			}
			shop.owner_name = "tester";
			shop.setItem(ShopBlockEntity.config_first, new ItemStack(Items.BREAD, 2));
			shop.setItem(ShopBlockEntity.config_first + 1, new ItemStack(Items.EMERALD, 1));
			Shops.record(server, pos, shop);
			boolean listed = Live.snapshot(server).getAsJsonArray("shops").toString().contains("tester");
			shop.clearContent();
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			boolean gone = !Shops.get(server).listings.containsKey(Shops.key(pos));
			return listed && gone ? null : "listed " + listed + " gone " + gone;
		});
	}
}
