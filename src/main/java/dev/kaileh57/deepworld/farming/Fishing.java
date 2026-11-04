package dev.kaileh57.deepworld.farming;

import dev.kaileh57.deepworld.content.Content;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LocationCheck;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

public final class Fishing {
	private Fishing() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin() || !key.identifier().toString().equals("minecraft:gameplay/fishing")) {
				return;
			}
			builder.withPool(catch_pool(Content.item("blind_eel"), 900, 1000));
			builder.withPool(catch_pool(Content.item("crystal_minnow"), 700, 800));
		});
	}

	private static LootPool.Builder catch_pool(Item item, double low, double high) {
		return LootPool.lootPool()
			.add(LootItem.lootTableItem(item))
			.when(LootItemRandomChanceCondition.randomChance(0.6F))
			.when(LocationCheck.checkLocation(LocationPredicate.Builder.atYLocation(MinMaxBounds.Doubles.between(low, high))));
	}
}
