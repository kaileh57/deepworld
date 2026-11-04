package dev.kaileh57.deepworld.economy;

import dev.kaileh57.deepworld.command.SelfTest;
import dev.kaileh57.deepworld.content.Content;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class EconomyTests {
	private EconomyTests() {
	}

	public static void register() {
		SelfTest.add("serving counter sells from stock to till", server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = SelfTest.test_pos(level, 7);
			level.setBlockAndUpdate(pos, Content.blocks.get("serving_counter").defaultBlockState());
			if (!(level.getBlockEntity(pos) instanceof ShopBlockEntity shop)) {
				return "no counter entity";
			}
			shop.owner = "owner";
			shop.setItem(ShopBlockEntity.stock_first, new ItemStack(Items.BREAD, 10));
			shop.setItem(ShopBlockEntity.config_first, new ItemStack(Items.BREAD, 2));
			shop.setItem(ShopBlockEntity.config_first + 1, new ItemStack(Items.EMERALD, 1));
			FakePlayer buyer = FakePlayer.get(level);
			buyer.getInventory().clearContent();
			buyer.getInventory().add(new ItemStack(Items.EMERALD, 3));
			String result = shop.buy(buyer, 0);
			int bread = buyer.getInventory().countItem(Items.BREAD);
			int till = shop.getItem(ShopBlockEntity.till_first).getCount();
			buyer.getInventory().clearContent();
			shop.clearContent();
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			return bread == 2 && till == 1 ? null : result + ": bread " + bread + " till " + till;
		});
		SelfTest.add("commissions post, accept and pay", server -> {
			Commissions board = Commissions.get(server);
			int before = board.open.size();
			board.post(new Commissions.Commission(board.next_id, "poster", "tester", "minecraft:cobblestone", 4, List.of(new ItemStack(Items.DIAMOND)), "", "", false));
			Commissions.Commission posted = board.open.get(board.open.size() - 1);
			board.open.set(board.open.size() - 1, posted.accepted_by("someone", "someone"));
			boolean accepted = board.open.get(board.open.size() - 1).acceptor().equals("someone");
			board.open.remove(board.open.size() - 1);
			board.setDirty();
			return accepted && board.open.size() == before ? null : "accepted " + accepted;
		});
		SelfTest.add("villager trades are seeded by uuid", server -> {
			ServerLevel level = server.overworld();
			Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.FARMER));
			var first = villager.getOffers().stream().map(o -> o.getResult().getItem().toString()).toList();
			Villager twin = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			twin.setUUID(villager.getUUID());
			twin.setVillagerData(twin.getVillagerData().withProfession(level.registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.FARMER));
			var second = twin.getOffers().stream().map(o -> o.getResult().getItem().toString()).toList();
			boolean crops_for_emeralds = villager.getOffers().stream().anyMatch(o -> o.getResult().is(Items.EMERALD) && o.getBaseCostA().is(Items.WHEAT));
			return first.equals(second) && !crops_for_emeralds ? null : "first " + first + " second " + second + " wheat→emerald " + crops_for_emeralds;
		});
	}
}
