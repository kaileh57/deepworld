package dev.kaileh57.deepworld.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class DwToolItem extends Item implements PolymerItem {
	private final Item base;

	public DwToolItem(Properties properties, Item base) {
		super(properties);
		this.base = base;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return base;
	}
}
