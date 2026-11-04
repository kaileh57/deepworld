package dev.kaileh57.deepworld.command;

import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.forge.ForgeBlockEntity;
import dev.kaileh57.deepworld.heat.HeatSystem;
import dev.kaileh57.deepworld.world.Layers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class SelfTest {
	private static final Map<String, Function<MinecraftServer, String>> checks = new LinkedHashMap<>();

	private SelfTest() {
	}

	public static void add(String name, Function<MinecraftServer, String> check) {
		checks.put(name, check);
	}

	public static List<String> run(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		for (Map.Entry<String, Function<MinecraftServer, String>> entry : checks.entrySet()) {
			String failure;
			try {
				failure = entry.getValue().apply(server);
			} catch (Exception error) {
				failure = "threw " + error;
			}
			lines.add((failure == null ? "pass " : "FAIL ") + entry.getKey() + (failure == null ? "" : ": " + failure));
		}
		return lines;
	}

	public static BlockPos test_pos(ServerLevel level, int offset) {
		return new BlockPos(offset * 3, 1500, 0);
	}

	static {
		add("heat by layer", server -> {
			ServerLevel level = server.overworld();
			FakePlayer player = FakePlayer.get(level);
			for (Layers.Layer layer : Layers.all()) {
				player.setPos(0.5, (layer.min_y() + layer.max_y()) / 2.0, 0.5);
				int expected = Math.max(0, 5 - layer.number());
				int actual = HeatSystem.ambient_heat(player);
				if (actual != expected) {
					return layer.name() + " heat " + actual + " expected " + expected;
				}
			}
			player.setPos(0.5, 1250, 0.5);
			return HeatSystem.ambient_heat(player) == 0 ? null : "surface is hot";
		});
		add("armor and fire resistance protect", server -> {
			FakePlayer player = FakePlayer.get(server.overworld());
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Content.item("cobalt_helmet")));
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Content.item("cobalt_chestplate")));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Content.item("cobalt_leggings")));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Content.item("cobalt_boots")));
			int armored = HeatSystem.protection(player);
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100));
			int with_effect = HeatSystem.protection(player);
			player.removeAllEffects();
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
			if (armored != 4) {
				return "cobalt set gives " + armored;
			}
			return with_effect == 6 ? null : "fire resistance gives " + with_effect;
		});
		add("pickaxe tiers gate rock", server -> {
			String[][] cases = {
				{"minecraft:iron_pickaxe", "minecraft:blackstone", "false"},
				{"deepworld:steel_pickaxe", "minecraft:blackstone", "true"},
				{"minecraft:wooden_pickaxe", "minecraft:granite", "false"},
				{"minecraft:copper_pickaxe", "minecraft:granite", "true"},
				{"minecraft:diamond_pickaxe", "deepworld:mantle_rock", "false"},
				{"deepworld:mantle_alloy_pickaxe", "deepworld:mantle_rock", "true"},
				{"deepworld:mantle_alloy_pickaxe", "deepworld:scorched_rock", "false"},
				{"deepworld:cobalt_steel_pickaxe", "deepworld:scorched_rock", "true"},
				{"minecraft:iron_pickaxe", "deepworld:gloomstone", "false"},
				{"minecraft:diamond_pickaxe", "deepworld:gloomstone", "true"}
			};
			for (String[] test : cases) {
				ItemStack tool = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(test[0])));
				var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(test[1]));
				boolean correct = tool.isCorrectToolForDrops(block.defaultBlockState());
				if (correct != Boolean.parseBoolean(test[2])) {
					return test[0] + " on " + test[1] + " gives " + correct;
				}
			}
			return null;
		});
		add("forge makes steel", server -> {
			ServerLevel level = server.overworld();
			BlockPos pos = test_pos(level, 0);
			level.setBlockAndUpdate(pos, Content.blocks.get("forge").defaultBlockState());
			if (!(level.getBlockEntity(pos) instanceof ForgeBlockEntity forge)) {
				return "no forge entity";
			}
			forge.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
			forge.setItem(1, new ItemStack(Items.COAL, 1));
			forge.setItem(2, new ItemStack(Content.item("flux"), 1));
			forge.setItem(ForgeBlockEntity.fuel_slot, new ItemStack(Items.COAL, 1));
			for (int tick = 0; tick < 400; tick++) {
				forge.server_tick(level, pos, level.getBlockState(pos));
			}
			ItemStack output = forge.getItem(ForgeBlockEntity.output_slot).copy();
			forge.clearContent();
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			return output.is(Content.item("steel_ingot")) && output.getCount() == 2 ? null : "output " + output;
		});
	}
}
