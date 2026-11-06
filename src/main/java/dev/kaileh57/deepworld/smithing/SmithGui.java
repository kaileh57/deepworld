package dev.kaileh57.deepworld.smithing;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SmithGui extends dev.kaileh57.deepworld.hud.DwGui {
	private final SimpleContainer work = new SimpleContainer(2);
	private String note = "tool left, quench liquid or gem right";

	public SmithGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x3, player, false);
		setTitle(Component.literal("smithing bench"));
		for (int index = 0; index < 27; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		setSlot(11, new Slot(work, 0, 0, 0));
		setSlot(15, new Slot(work, 1, 0, 0));
		draw();
	}

	private void draw() {
		setSlot(13, new GuiElementBuilder(Items.ANVIL).setName(Component.literal("quench or socket")).addLoreLine(Component.literal(note)).addLoreLine(Component.literal("quench: water, rendered fat, brine, lava, ghast tear cordial")).addLoreLine(Component.literal("socket: resonant shard, lapis, diamond, quartz, echo shard")).setCallback((index, type, action, gui) -> {
			note = Smithing.apply(player, work.getItem(0), work.getItem(1));
			draw();
		}).build());
	}

	@Override
	public void onRemoved() {
		for (int slot = 0; slot < 2; slot++) {
			ItemStack stack = work.removeItemNoUpdate(slot);
			if (!stack.isEmpty() && !player.getInventory().add(stack)) {
				player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
	}
}
