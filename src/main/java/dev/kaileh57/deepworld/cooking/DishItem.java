package dev.kaileh57.deepworld.cooking;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class DishItem extends Item implements PolymerItem {
	private final Item base;

	public DishItem(Properties properties, Item base) {
		super(properties);
		this.base = base;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return base;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		return model != null ? model : PolymerItem.super.getPolymerItemModel(stack, context, lookup);
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext context, HolderLookup.Provider lookup) {
		copy(out, stack, DataComponents.FOOD);
		copy(out, stack, DataComponents.CONSUMABLE);
		copy(out, stack, DataComponents.MAX_STACK_SIZE);
		copy(out, stack, DataComponents.ITEM_NAME);
		copy(out, stack, DataComponents.LORE);
		int fresh = CookData.tag(stack).getIntOr("fresh", -1);
		int fresh_max = CookData.tag(stack).getIntOr("fresh_max", -1);
		if (fresh >= 0 && fresh_max > 0) {
			out.set(DataComponents.MAX_DAMAGE, fresh_max);
			out.set(DataComponents.DAMAGE, Math.max(0, fresh_max - fresh));
		}
	}

	private static <T> void copy(ItemStack out, ItemStack stack, net.minecraft.core.component.DataComponentType<T> type) {
		T value = stack.get(type);
		if (value != null) {
			out.set(type, value);
		}
	}
}
