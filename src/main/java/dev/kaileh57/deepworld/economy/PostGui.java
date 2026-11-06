package dev.kaileh57.deepworld.economy;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PostGui extends dev.kaileh57.deepworld.hud.DwGui {
	private final SimpleContainer reward = new SimpleContainer(7);
	private final SimpleContainer wanted = new SimpleContainer(1);
	private int count = 1;
	private boolean posted;

	public PostGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x3, player, false);
		setTitle(Component.literal("post a commission"));
		for (int index = 0; index < 27; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		for (int slot = 0; slot < 7; slot++) {
			setSlot(1 + slot, new Slot(reward, slot, 0, 0));
		}
		setSlot(19, new Slot(wanted, 0, 0, 0));
		draw();
	}

	private void draw() {
		setSlot(0, new GuiElementBuilder(Items.EMERALD).setName(Component.literal("reward →")).addLoreLine(Component.literal("put what you'll pay in this row")).build());
		setSlot(18, new GuiElementBuilder(Items.SPYGLASS).setName(Component.literal("wanted →")).addLoreLine(Component.literal("put one of the item you want")).build());
		setSlot(21, new GuiElementBuilder(Items.REDSTONE).setName(Component.literal("-1")).setCallback((i, t, a, g) -> {
			count = Math.max(1, count - 1);
			draw();
		}).build());
		setSlot(22, new GuiElementBuilder(Items.PAPER, Math.min(64, count)).setName(Component.literal("count: " + count)).build());
		setSlot(23, new GuiElementBuilder(Items.GLOWSTONE_DUST).setName(Component.literal("+1")).setCallback((i, t, a, g) -> {
			count = Math.min(576, count + 1);
			draw();
		}).build());
		setSlot(24, new GuiElementBuilder(Items.GLOWSTONE).setName(Component.literal("+16")).setCallback((i, t, a, g) -> {
			count = Math.min(576, count + 16);
			draw();
		}).build());
		setSlot(26, new GuiElementBuilder(Items.WRITABLE_BOOK).setName(Component.literal("post")).setCallback((i, t, a, g) -> {
			if (post()) {
				close();
			}
		}).build());
	}

	private boolean post() {
		ItemStack want = wanted.getItem(0);
		if (want.isEmpty() || reward.isEmpty()) {
			return false;
		}
		List<ItemStack> paid = new ArrayList<>();
		for (int slot = 0; slot < 7; slot++) {
			ItemStack stack = reward.removeItemNoUpdate(slot);
			if (!stack.isEmpty()) {
				paid.add(stack);
			}
		}
		Commissions board = Commissions.get(player.level().getServer());
		board.post(new Commissions.Commission(board.next_id, player.getUUID().toString(), player.getGameProfile().name(), BuiltInRegistries.ITEM.getKey(want.getItem()).toString(), count, paid, "", "", false));
		posted = true;
		return true;
	}

	@Override
	public void onRemoved() {
		for (SimpleContainer container : new SimpleContainer[] {reward, wanted}) {
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.removeItemNoUpdate(slot);
				if (!stack.isEmpty() && !player.getInventory().add(stack)) {
					player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
		}
	}
}
