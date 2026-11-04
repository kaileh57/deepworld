package dev.kaileh57.deepworld.cooking;

import dev.kaileh57.deepworld.command.SelfTest;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.heat.HeatSystem;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import java.util.ArrayList;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

public final class CookingTests {
	private CookingTests() {
	}

	private static CookBlockEntity place(ServerLevel level, BlockPos pos, String station) {
		level.setBlockAndUpdate(pos.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		level.setBlockAndUpdate(pos, Content.blocks.get(station).defaultBlockState());
		return level.getBlockEntity(pos) instanceof CookBlockEntity cook ? cook : null;
	}

	private static void clear(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof CookBlockEntity cook) {
			cook.clearContent();
		}
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
	}

	private static ItemStack run_until_done(CookBlockEntity cook, ServerLevel level, BlockPos pos, FakePlayer player, boolean stir) {
		for (int tick = 0; tick < 4000 && cook.status.equals("cooking"); tick++) {
			cook.server_tick(level, pos);
			if (stir && cook.window_active()) {
				cook.stir(player);
			}
		}
		return cook.getItem(CookBlockEntity.output_slot).copy();
	}

	public static void register() {
		SelfTest.add("pot cooks field stew with stirs", server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = SelfTest.test_pos(level, 1);
			FakePlayer player = FakePlayer.get(level);
			CookBlockEntity cook = place(level, pos, "iron_pot");
			if (cook == null) {
				return "no pot entity";
			}
			cook.setItem(0, new ItemStack(Items.CARROT));
			cook.setItem(1, new ItemStack(Items.POTATO));
			cook.setItem(2, new ItemStack(Items.BROWN_MUSHROOM));
			cook.setItem(3, Dishes.make(CookData.dishes.get("bone_broth"), 2, ""));
			cook.start(player);
			if (!cook.status.equals("cooking")) {
				clear(level, pos);
				return "did not start: " + cook.message;
			}
			ItemStack out = run_until_done(cook, level, pos, player, true);
			clear(level, pos);
			CompoundTag tag = CookData.tag(out);
			if (!tag.getStringOr("dish", "").equals("field_stew")) {
				return "made " + out.getHoverName().getString();
			}
			return tag.getIntOr("quality", 0) >= 3 ? null : "quality " + tag.getIntOr("quality", 0);
		});
		SelfTest.add("unknown mix makes a generic dish", server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = SelfTest.test_pos(level, 2);
			FakePlayer player = FakePlayer.get(level);
			CookBlockEntity cook = place(level, pos, "iron_pot");
			cook.setItem(0, new ItemStack(Items.BEEF));
			cook.setItem(1, new ItemStack(Items.BREAD));
			cook.start(player);
			ItemStack out = run_until_done(cook, level, pos, player, false);
			clear(level, pos);
			return out.getHoverName().getString().equals("hearty stew") ? null : "made " + out.getHoverName().getString();
		});
		SelfTest.add("meal slots refresh and cap", server -> {
			FakePlayer player = FakePlayer.get(server.overworld());
			player.setAttached(Meals.meals, new ArrayList<>());
			for (String id : new String[] {"field_stew", "chili_broth", "cave_chowder", "glowcap_omelette", "miners_pasty"}) {
				Meals.eat(player, Dishes.make(CookData.dishes.get(id), 2, ""));
			}
			int dishes = (int) Meals.of(player).stream().filter(m -> !m.kind().equals("drink")).count();
			Meals.eat(player, Dishes.make(CookData.dishes.get("coffee"), 2, ""));
			int drinks = (int) Meals.of(player).stream().filter(m -> m.kind().equals("drink")).count();
			player.setAttached(Meals.meals, new ArrayList<>());
			if (dishes != 3) {
				return dishes + " dish slots used";
			}
			return drinks == 1 ? null : drinks + " drinks";
		});
		SelfTest.add("cooling food counters heat", server -> {
			FakePlayer player = FakePlayer.get(server.overworld());
			player.setAttached(Meals.meals, new ArrayList<>());
			int before = HeatSystem.protection(player);
			Meals.eat(player, Dishes.make(CookData.dishes.get("frost_mint_sorbet"), 2, ""));
			int after = HeatSystem.protection(player);
			player.setAttached(Meals.meals, new ArrayList<>());
			return after - before == 2 ? null : "sorbet adds " + (after - before);
		});
		SelfTest.add("food spoils, preserved food does not", server -> {
			FakePlayer player = FakePlayer.get(server.overworld());
			player.setPos(0.5, 1300, 0.5);
			player.getInventory().clearContent();
			ItemStack chili = new ItemStack(Content.item("chili"), 3);
			CompoundTag tag = CookData.tag(chili);
			tag.putInt("fresh", 1);
			CookData.set_tag(chili, tag);
			player.getInventory().setItem(0, chili);
			player.getInventory().setItem(1, new ItemStack(Content.item("salt"), 3));
			Spoilage.tick_player(player);
			ItemStack first = player.getInventory().getItem(0).copy();
			ItemStack second = player.getInventory().getItem(1).copy();
			player.getInventory().clearContent();
			if (!first.is(Content.item("spoiled_food")) || first.getCount() != 3) {
				return "chili became " + first;
			}
			return second.is(Content.item("salt")) ? null : "salt became " + second;
		});
		SelfTest.add("forage plants have textured client blocks", server -> {
			int left = PolymerBlockResourceUtils.getBlocksLeft(BlockModelType.TRIPWIRE);
			for (String id : new String[] {"wild_rice", "glowcap_patch", "forge_fig_bush", "truffle_patch"}) {
				var block = Content.blocks.get(id);
				var client = ((PlantBlock) block).getPolymerBlockState(block.defaultBlockState(), null);
				if (client.is(Blocks.SHORT_GRASS)) {
					return id + " fell back to grass, " + left + " slots left";
				}
			}
			return null;
		});
	}
}
