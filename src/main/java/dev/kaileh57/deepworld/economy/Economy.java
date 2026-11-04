package dev.kaileh57.deepworld.economy;

import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class Economy {
	public static BlockEntityType<ShopBlockEntity> shop_entity;

	private Economy() {
	}

	public static void register() {
		shop_entity = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, DeepWorld.id("serving_counter"), new BlockEntityType<>(ShopBlockEntity::new, Set.of(Content.blocks.get("serving_counter"))));
		PolymerBlockUtils.registerBlockEntity(shop_entity);
		EconomyTests.register();
	}
}
