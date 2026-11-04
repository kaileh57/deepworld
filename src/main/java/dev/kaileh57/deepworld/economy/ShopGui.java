package dev.kaileh57.deepworld.economy;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ShopGui extends SimpleGui {
	private final ShopBlockEntity shop;
	private final boolean owner_mode;
	private String note = "";

	public ShopGui(ServerPlayer player, ShopBlockEntity shop) {
		super(shop.owner.equals(player.getUUID().toString()) || shop.owner.isEmpty() ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3, player, false);
		this.shop = shop;
		if (shop.owner.isEmpty()) {
			shop.owner = player.getUUID().toString();
			shop.owner_name = player.getGameProfile().name();
			shop.setChanged();
		}
		this.owner_mode = shop.owner.equals(player.getUUID().toString());
		setTitle(Component.literal(owner_mode ? "your counter" : shop.owner_name + "'s counter"));
		draw();
	}

	private void draw() {
		int size = owner_mode ? 54 : 27;
		for (int index = 0; index < size; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		if (owner_mode) {
			for (int slot = 0; slot < ShopBlockEntity.stock_count; slot++) {
				setSlot(slot, new Slot(shop, ShopBlockEntity.stock_first + slot, 0, 0));
			}
			for (int slot = 0; slot < ShopBlockEntity.till_count; slot++) {
				setSlot(27 + slot, new Slot(shop, ShopBlockEntity.till_first + slot, 0, 0) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return false;
					}
				});
			}
			setSlot(18, new GuiElementBuilder(Items.CHEST).setName(Component.literal("stock above · till below")).build());
			for (int trade = 0; trade < ShopBlockEntity.trades; trade++) {
				int base = 37 + trade * 2 + (trade >= 2 ? 1 : 0);
				setSlot(base, new Slot(shop, ShopBlockEntity.config_first + trade * 2, 0, 0));
				setSlot(base + 1 + 9, new Slot(shop, ShopBlockEntity.config_first + trade * 2 + 1, 0, 0));
			}
			setSlot(36, new GuiElementBuilder(Items.NAME_TAG).setName(Component.literal("sells →")).addLoreLine(Component.literal("put an example stack per trade")).build());
			setSlot(45, new GuiElementBuilder(Items.EMERALD).setName(Component.literal("for →")).addLoreLine(Component.literal("put the price stack under it, offset right")).build());
		} else {
			for (int trade = 0; trade < ShopBlockEntity.trades; trade++) {
				ItemStack offer = shop.offer(trade);
				ItemStack price = shop.price(trade);
				int slot = 10 + trade * 2;
				if (offer.isEmpty() || price.isEmpty()) {
					continue;
				}
				int stock = shop.stock_of(offer);
				int index = trade;
				setSlot(slot, GuiElementBuilder.from(offer.copy())
					.addLoreLine(Component.literal("costs " + price.getCount() + " " + price.getHoverName().getString().toLowerCase()).withStyle(ChatFormatting.GOLD))
					.addLoreLine(Component.literal(stock >= offer.getCount() ? "in stock" : "sold out").withStyle(stock >= offer.getCount() ? ChatFormatting.GREEN : ChatFormatting.RED))
					.addLoreLine(Component.literal(note).withStyle(ChatFormatting.YELLOW))
					.setCallback((i, t, a, g) -> {
						note = shop.buy(player, index);
						draw();
					}).build());
			}
		}
	}

	@Override
	public void onTick() {
		if (shop.isRemoved()) {
			close();
		}
	}
}
