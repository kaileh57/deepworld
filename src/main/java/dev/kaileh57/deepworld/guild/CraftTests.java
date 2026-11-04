package dev.kaileh57.deepworld.guild;

import dev.kaileh57.deepworld.alchemy.Blasting;
import dev.kaileh57.deepworld.command.SelfTest;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookBlockEntity;
import dev.kaileh57.deepworld.cooking.PlantBlock;
import dev.kaileh57.deepworld.farming.Farming;
import dev.kaileh57.deepworld.farming.Husbandry;
import dev.kaileh57.deepworld.smithing.Smithing;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;

public final class CraftTests {
	private CraftTests() {
	}

	public static void register() {
		SelfTest.add("cave crop grows and harvests", server -> {
			ServerLevel level = server.overworld();
			BlockPos soil = new BlockPos(5, 749, 5);
			BlockPos spot = soil.above();
			level.setBlockAndUpdate(soil, Blocks.CALCITE.defaultBlockState());
			level.setBlockAndUpdate(spot, Content.blocks.get("glowcap_patch").defaultBlockState().setValue(PlantBlock.age, 0));
			PlantBlock block = (PlantBlock) Content.blocks.get("glowcap_patch");
			boolean can = Farming.can_grow(block, level, spot);
			for (int i = 0; i < 400 && level.getBlockState(spot).getValue(PlantBlock.age) < 3; i++) {
				level.getBlockState(spot).randomTick(level, spot, level.getRandom());
			}
			int grown = level.getBlockState(spot).getValue(PlantBlock.age);
			FakePlayer player = FakePlayer.get(level);
			Farming.harvest(player, level, spot, level.getBlockState(spot));
			int after = level.getBlockState(spot).getValue(PlantBlock.age);
			long drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(2)).stream().filter(e -> e.getItem().is(Content.item("glowcap"))).count();
			level.getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(2)).forEach(ItemEntity::discard);
			level.setBlockAndUpdate(spot, Blocks.AIR.defaultBlockState());
			if (!can || grown != 3) {
				return "can grow " + can + ", reached age " + grown;
			}
			return after == 1 && drops > 0 ? null : "after harvest age " + after + ", drops " + drops;
		});
		SelfTest.add("focus caps at two and milestones fire", server -> {
			FakePlayer player = FakePlayer.get(server.overworld());
			player.setAttached(Guild.state, Guild.State.empty());
			Guild.toggle_focus(player, "cook");
			player.setAttached(Guild.state, new Guild.State(Guild.of(player).counters(), Guild.of(player).milestones(), Guild.of(player).focus(), -1000000L));
			Guild.toggle_focus(player, "smith");
			String third = Guild.toggle_focus(player, "hunter");
			Guild.add(player, "dishes_cooked", 10);
			boolean milestone = Guild.of(player).milestones().contains("line_cook");
			int focus = Guild.of(player).focus().size();
			player.setAttached(Guild.state, Guild.State.empty());
			if (focus != 2 || !third.startsWith("drop")) {
				return "focus " + focus + ", third: " + third;
			}
			return milestone ? null : "line cook milestone missing";
		});
		SelfTest.add("quench and socket change tools", server -> {
			FakePlayer player = FakePlayer.get(server.overworld());
			ItemStack pick = new ItemStack(Content.item("steel_pickaxe"));
			int base = pick.getMaxDamage();
			String quench = Smithing.apply(player, pick, new ItemStack(Content.item("rendered_fat")));
			int tough = pick.getMaxDamage();
			String socket = Smithing.apply(player, pick, new ItemStack(Items.DIAMOND));
			int socketed = pick.getMaxDamage();
			if (tough != (int) (base * 1.5)) {
				return quench + ": " + base + " -> " + tough;
			}
			return socketed == (int) (tough * 1.25) ? null : socket + ": " + tough + " -> " + socketed;
		});
		SelfTest.add("mining charge drops ore, not stone", server -> {
			ServerLevel level = server.overworld();
			BlockPos center = new BlockPos(8, 1450, 8);
			for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
				level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			}
			level.setBlockAndUpdate(center, Blocks.IRON_ORE.defaultBlockState());
			boolean tagged = level.getBlockState(center).is(Smithing.dowsable);
			int raw_drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(center), level, center, null).size();
			Blasting.detonate(level, center, Direction.UP, "mining", null);
			var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(3));
			boolean iron = items.stream().anyMatch(e -> e.getItem().is(Items.RAW_IRON));
			boolean stone = items.stream().anyMatch(e -> e.getItem().is(Items.COBBLESTONE) || e.getItem().is(Items.STONE));
			boolean cleared = level.getBlockState(center).isAir();
			items.forEach(ItemEntity::discard);
			return iron && !stone && cleared ? null : "iron " + iron + " stone " + stone + " cleared " + cleared + " tagged " + tagged + " drops " + raw_drops;
		});
		SelfTest.add("bred animals inherit traits", server -> {
			ServerLevel level = server.overworld();
			Cow a = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			Cow b = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			Cow child = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			Map<String, Integer> traits = new HashMap<>();
			traits.put("plump", 3);
			a.setAttached(Husbandry.attachment, new HashMap<>(traits));
			b.setAttached(Husbandry.attachment, new HashMap<>(traits));
			Husbandry.on_breed(level, a, b, child);
			int plump = Husbandry.of(child).getOrDefault("plump", 0);
			return plump >= 2 ? null : "child plump " + plump;
		});
		SelfTest.add("sulfur fuels brewing stands", server -> new ItemStack(Items.SULFUR).has(DataComponents.BREWING_FUEL) ? null : "sulfur has no brewing fuel");
		SelfTest.add("still distills mint oil", server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = SelfTest.test_pos(level, 5);
			level.setBlockAndUpdate(pos.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
			level.setBlockAndUpdate(pos, Content.blocks.get("still").defaultBlockState());
			if (!(level.getBlockEntity(pos) instanceof CookBlockEntity still)) {
				return "no still entity";
			}
			FakePlayer player = FakePlayer.get(level);
			still.setItem(0, new ItemStack(Content.item("mint")));
			still.start(player);
			for (int i = 0; i < 4000 && still.status.equals("cooking"); i++) {
				still.server_tick(level, pos);
			}
			ItemStack out = still.getItem(CookBlockEntity.output_slot).copy();
			still.clearContent();
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
			return out.is(Content.item("mint_oil")) && out.getCount() == 2 ? null : "made " + out + " (" + still.message + ")";
		});
	}
}
