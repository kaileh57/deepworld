package dev.kaileh57.deepworld.cooking;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CookGui extends dev.kaileh57.deepworld.hud.DwGui {
	private static final int[] input_indexes = {10, 11, 12, 13, 14, 15};
	private static final int heat_index = 19;
	private static final int cook_index = 21;
	private static final int stir_index = 22;
	private static final int progress_index = 23;
	private static final int output_index = 25;
	private final CookBlockEntity cook;

	public CookGui(ServerPlayer player, CookBlockEntity cook) {
		super(MenuType.GENERIC_9x3, player, false);
		this.cook = cook;
		CookData.Station station = cook.station();
		setTitle(Component.literal(station.name()));
		for (int index = 0; index < 27; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		for (int slot = 0; slot < 6; slot++) {
			if (slot < station.slots()) {
				setSlot(input_indexes[slot], new Slot(cook, slot, 0, 0) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return cook.status.equals("idle");
					}
				});
			} else {
				setSlot(input_indexes[slot], new GuiElementBuilder(Items.STAINED_GLASS_PANE.black()).setName(Component.literal("needs better cookware")).build());
			}
		}
		setSlot(output_index, new Slot(cook, CookBlockEntity.output_slot, 0, 0) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		refresh();
	}

	private void refresh() {
		Heat.Source heat = Heat.below(cook.getLevel(), cook.getBlockPos());
		CookData.Station station = cook.station();
		if (station.max_heat() > 0) {
			setSlot(heat_index, new GuiElementBuilder(heat.level() > 0 ? Items.BLAZE_POWDER : Items.GUNPOWDER).setName(Component.literal("heat: " + Heat.name(heat.level()) + (heat.soul() ? " (soul)" : ""))).addLoreLine(Component.literal("max " + Heat.name(station.max_heat()))).build());
		}
		boolean idle = cook.status.equals("idle");
		setSlot(cook_index, new GuiElementBuilder(idle ? Items.DYE.lime() : Items.DYE.gray()).setName(Component.literal(idle ? "cook" : cook.status)).addLoreLine(Component.literal(cook.message)).setCallback((index, type, action, gui) -> {
			cook.start(player);
			refresh();
		}).build());
		if (cook.stirs()) {
			boolean active = cook.status.equals("cooking") && cook.window_active();
			GuiElementBuilder stir = new GuiElementBuilder(active ? Items.GLOW_INK_SAC : Items.STICK).setName(Component.literal(active ? "stir now!" : "stir")).addLoreLine(Component.literal("stirs " + cook.hits + "  missed " + cook.misses)).setCallback((index, type, action, gui) -> {
				cook.stir(player);
				refresh();
			});
			if (active) {
				stir.glow();
			}
			setSlot(stir_index, stir.build());
		}
		int percent = cook.total > 0 ? cook.progress * 100 / cook.total : 0;
		String text = switch (cook.status) {
			case "cooking" -> percent + "%";
			case "done" -> "ready";
			default -> "idle";
		};
		setSlot(progress_index, new GuiElementBuilder(cook.status.equals("cooking") ? Items.CLOCK : Items.BOWL).setName(Component.literal(text)).addLoreLine(Component.literal(cook.message)).build());
	}

	@Override
	public void onTick() {
		if (cook.isRemoved()) {
			close();
			return;
		}
		refresh();
	}
}
