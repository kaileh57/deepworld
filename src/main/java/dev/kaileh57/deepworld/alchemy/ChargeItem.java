package dev.kaileh57.deepworld.alchemy;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public class ChargeItem extends Item implements PolymerItem {
	private final String kind;
	private final Item base;

	public ChargeItem(Properties properties, String kind, Item base) {
		super(properties);
		this.kind = kind;
		this.base = base;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return base;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Blasting.arm(level, context.getClickedPos(), context.getClickedFace(), kind, context.getPlayer() instanceof ServerPlayer player ? player : null);
		context.getItemInHand().consume(1, context.getPlayer());
		return InteractionResult.SUCCESS_SERVER;
	}
}
