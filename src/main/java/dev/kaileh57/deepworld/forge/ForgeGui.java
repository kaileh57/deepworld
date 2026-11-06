package dev.kaileh57.deepworld.forge;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ForgeGui extends dev.kaileh57.deepworld.hud.DwGui {
	private static final int progress_index = 14;
	private static final int flame_index = 21;
	private final ForgeBlockEntity forge;

	public ForgeGui(ServerPlayer player, ForgeBlockEntity forge) {
		super(MenuType.GENERIC_9x3, player, false);
		this.forge = forge;
		forge.last_user = player.getUUID();
		setTitle(Component.translatable(forge.getBlockState().getBlock().getDescriptionId()));
		for (int index = 0; index < 27; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		setSlot(10, new Slot(forge, 0, 0, 0));
		setSlot(11, new Slot(forge, 1, 0, 0));
		setSlot(12, new Slot(forge, 2, 0, 0));
		setSlot(20, new Slot(forge, ForgeBlockEntity.fuel_slot, 0, 0));
		setSlot(16, new Slot(forge, ForgeBlockEntity.output_slot, 0, 0) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		refresh();
	}

	private void refresh() {
		int percent = forge.progress_total > 0 ? forge.progress * 100 / forge.progress_total : 0;
		setSlot(progress_index, new GuiElementBuilder(percent > 0 ? Items.SPECTRAL_ARROW : Items.ARROW).setName(Component.literal("forging " + percent + "%")).build());
		int fuel_percent = forge.burn_total > 0 ? forge.burn_left * 100 / forge.burn_total : 0;
		setSlot(flame_index, new GuiElementBuilder(fuel_percent > 0 ? Items.BLAZE_POWDER : Items.GUNPOWDER).setName(Component.literal(fuel_percent > 0 ? "heat " + fuel_percent + "%" : "no fuel")).build());
	}

	@Override
	public void onTick() {
		if (forge.isRemoved()) {
			close();
			return;
		}
		refresh();
	}
}
