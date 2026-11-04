package dev.kaileh57.deepworld.cooking;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ProcessGui extends SimpleGui {
	private final CookData.Station station;
	private final BlockPos pos;
	private final SimpleContainer work = new SimpleContainer(2);
	private int labor;

	public ProcessGui(ServerPlayer player, CookData.Station station, BlockPos pos) {
		super(MenuType.GENERIC_9x3, player, false);
		this.station = station;
		this.pos = pos;
		setTitle(Component.literal(station.name()));
		for (int index = 0; index < 27; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		setSlot(11, new Slot(work, 0, 0, 0));
		setSlot(15, new Slot(work, 1, 0, 0) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		refresh();
	}

	private CookData.Process recipe(ItemStack input) {
		String key = BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
		for (CookData.Process process : CookData.processes) {
			if (process.station().equals(station.station()) && process.input().equals(key)) {
				return process;
			}
		}
		return null;
	}

	private void refresh() {
		ItemStack input = work.getItem(0);
		String hint;
		int needed;
		if (station.kind().equals("chop")) {
			needed = 2;
			hint = input.isEmpty() ? "place an ingredient" : CookData.is_ingredient(input) && !CookData.chopped(input) ? "chop" : "can't chop that";
		} else {
			CookData.Process process = input.isEmpty() ? null : recipe(input);
			needed = process == null ? 0 : process.labor();
			hint = input.isEmpty() ? "place an input" : process == null ? "nothing to make" : "work " + labor + "/" + needed;
		}
		String verb = switch (station.station()) {
			case "cutting_board" -> "chop";
			case "mill" -> "turn the stone";
			case "churn" -> "churn";
			case "mortar" -> "grind";
			default -> "press";
		};
		setSlot(13, new GuiElementBuilder(Items.IRON_AXE).setName(Component.literal(verb)).addLoreLine(Component.literal(hint)).setCallback((index, type, action, gui) -> {
			work_once();
			refresh();
		}).build());
	}

	private void work_once() {
		ItemStack input = work.getItem(0);
		if (input.isEmpty()) {
			return;
		}
		ItemStack output = work.getItem(1);
		if (station.kind().equals("chop")) {
			if (!CookData.is_ingredient(input) || CookData.chopped(input)) {
				return;
			}
			labor++;
			player.level().playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6F, 1.3F);
			if (labor < 2) {
				return;
			}
			labor = 0;
			ItemStack chopped = input.copyWithCount(1);
			var tag = CookData.tag(chopped);
			tag.putBoolean("chopped", true);
			CookData.set_tag(chopped, tag);
			if (!output.isEmpty() && !ItemStack.isSameItemSameComponents(output, chopped)) {
				return;
			}
			input.shrink(1);
			if (output.isEmpty()) {
				work.setItem(1, chopped);
			} else {
				output.grow(1);
			}
			return;
		}
		CookData.Process process = recipe(input);
		if (process == null) {
			return;
		}
		labor++;
		player.level().playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.4F, 1.2F);
		if (labor < process.labor()) {
			return;
		}
		labor = 0;
		ItemStack result = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(process.output())), process.count());
		if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result) || output.getCount() + result.getCount() > output.getMaxStackSize())) {
			return;
		}
		input.shrink(1);
		if (output.isEmpty()) {
			work.setItem(1, result);
		} else {
			output.grow(result.getCount());
		}
		if (!process.remainder().isEmpty()) {
			ItemStack remainder = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(process.remainder())));
			if (!player.getInventory().add(remainder)) {
				player.drop(remainder, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
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
