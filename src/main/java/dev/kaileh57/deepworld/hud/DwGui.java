package dev.kaileh57.deepworld.hud;

import dev.kaileh57.deepworld.living.WikiCommand;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class DwGui extends SimpleGui {
	public DwGui(MenuType<?> type, ServerPlayer player, boolean include_player_inventory) {
		super(type, player, include_player_inventory);
	}

	@Override
	public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
		if (type == ClickType.MOUSE_MIDDLE) {
			ItemStack stack = ItemStack.EMPTY;
			GuiElement element = index >= 0 && index < getVirtualSize() ? getGuiElement(index) : null;
			if (element != null) {
				stack = element.getItemStack();
			} else if (index >= 0) {
				Slot slot = getSlotRedirectOrPlayer(index);
				if (slot != null) {
					stack = slot.getItem();
				}
			}
			if (!stack.isEmpty()) {
				WikiCommand.link(player, stack);
				return false;
			}
		}
		return super.onAnyClick(index, type, action);
	}
}
