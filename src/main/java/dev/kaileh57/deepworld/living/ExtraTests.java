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
