package dev.kaileh57.deepworld.alchemy;

import dev.kaileh57.deepworld.content.Content;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BrewingFuel;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

public final class Alchemy {
	private Alchemy() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(Items.SULFUR, builder -> builder.set(DataComponents.BREWING_FUEL, new BrewingFuel(ContextIntProviders.BREWING_DEFAULT_USES, ContextFloatProviders.BREWING_DEFAULT_SPEED_MULTIPLIER))));
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (source.isBuiltin() && key.identifier().toString().equals("minecraft:blocks/tuff")) {
				builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Content.item("saltpeter"))).when(LootItemRandomChanceCondition.randomChance(0.15F)));
			}
		});
	}
}
