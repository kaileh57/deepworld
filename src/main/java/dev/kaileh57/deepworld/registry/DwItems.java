package dev.kaileh57.deepworld.registry;

import dev.kaileh57.deepworld.DeepWorld;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class DwItems {
	public static final Item rock_salt = register("rock_salt", properties -> new SimplePolymerItem(properties, Items.SUGAR));

	private DwItems() {
	}

	private static Item register(String path, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DeepWorld.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	public static void register() {
	}
}
