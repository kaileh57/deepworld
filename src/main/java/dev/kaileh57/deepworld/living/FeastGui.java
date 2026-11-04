package dev.kaileh57.deepworld.living;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class FeastGui extends SimpleGui {
	private final SimpleContainer plates = new SimpleContainer(10);
	private final BlockPos table;
	private String note = "place up to 10 dishes, then serve";

	public FeastGui(ServerPlayer player, BlockPos table) {
		super(MenuType.GENERIC_9x3, player, false);
		this.table = table;
		setTitle(Component.literal("feast table"));
		for (int index = 0; index < 27; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		for (int slot = 0; slot < 9; slot++) {
			setSlot(slot, new Slot(plates, slot, 0, 0));
		}
		setSlot(9, new Slot(plates, 9, 0, 0));
		draw();
	}

	private void draw() {
		setSlot(22, new GuiElementBuilder(Items.CAKE).setName(Component.literal("serve")).addLoreLine(Component.literal("everyone within 8 blocks eats every dish at once")).addLoreLine(Component.literal("+1 absorption heart per 4 dishes")).addLoreLine(Component.literal("ten dishes covering every layer: the tasting menu")).addLoreLine(Component.literal(note)).setCallback((i, t, a, g) -> {
			List<ItemStack> served = new ArrayList<>();
			for (int slot = 0; slot < 10; slot++) {
				if (!plates.getItem(slot).isEmpty()) {
					served.add(plates.getItem(slot).copyWithCount(1));
				}
			}
			note = Feast.serve(player, player.level(), table, served);
			if (!note.startsWith("a feast needs")) {
				for (int slot = 0; slot < 10; slot++) {
					plates.getItem(slot).shrink(1);
				}
			}
			draw();
		}).build());
	}

	@Override
	public void onRemoved() {
		for (int slot = 0; slot < 10; slot++) {
			ItemStack stack = plates.removeItemNoUpdate(slot);
			if (!stack.isEmpty() && !player.getInventory().add(stack)) {
				player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
	}
}
