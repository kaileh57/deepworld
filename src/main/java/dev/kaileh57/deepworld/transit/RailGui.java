package dev.kaileh57.deepworld.transit;

import dev.kaileh57.deepworld.hud.DwGui;
import dev.kaileh57.deepworld.hud.Hud;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

public final class RailGui {
	private RailGui() {
	}

	private static void fill(DwGui gui, int size) {
		for (int index = 0; index < size; index++) {
			gui.setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
	}

	public static void ticket(ServerPlayer player) {
		DwGui gui = new DwGui(MenuType.GENERIC_9x3, player, false);
		gui.setTitle(Component.literal("ticket machine"));
		fill(gui, 27);
		List<String> names = RailNetwork.names(player.level().getServer());
		String current = player.getAttached(Carts.ticket);
		int index = 0;
		for (String name : names) {
			if (index >= 26) {
				break;
			}
			boolean chosen = name.equals(current);
			gui.setSlot(index++, new GuiElementBuilder(chosen ? Items.FILLED_MAP : Items.PAPER).setName(Component.literal(name)).addLoreLine(Component.literal(chosen ? "your ticket" : "click to buy a ticket")).setCallback((i, t, a, g) -> {
				player.setAttached(Carts.ticket, name);
				Hud.notice(player, Component.literal("ticket to " + name + ". ride a cart and the switches will route it"));
				g.close();
			}).build());
		}
		if (names.isEmpty()) {
			gui.setSlot(13, new GuiElementBuilder(Items.BARRIER).setName(Component.literal("no named stations yet")).build());
		}
		gui.setSlot(26, new GuiElementBuilder(Items.SHEARS).setName(Component.literal("tear up ticket")).setCallback((i, t, a, g) -> {
			player.removeAttached(Carts.ticket);
			g.close();
		}).build());
		gui.open();
	}

	public static void switch_box(ServerPlayer player, RailDevice device) {
		DwGui gui = new DwGui(MenuType.GENERIC_9x3, player, false);
		gui.setTitle(Component.literal("switch box"));
		draw_switch(gui, player, device);
		gui.open();
	}

	private static void draw_switch(DwGui gui, ServerPlayer player, RailDevice device) {
		fill(gui, 27);
		int index = 0;
		for (String name : RailNetwork.names(player.level().getServer())) {
			if (index >= 26) {
				break;
			}
			boolean branch = device.routes.contains(name);
			gui.setSlot(index++, new GuiElementBuilder(branch ? Items.POWERED_RAIL : Items.RAIL).setName(Component.literal(name)).addLoreLine(Component.literal(branch ? "takes the branch" : "goes straight")).addLoreLine(Component.literal("click to change")).setCallback((i, t, a, g) -> {
				if (!device.routes.remove(name)) {
					device.routes.add(name);
				}
				device.setChanged();
				draw_switch(gui, player, device);
			}).build());
		}
		gui.setSlot(26, new GuiElementBuilder(Items.REDSTONE_TORCH).setName(Component.literal(device.powered ? "set to branch" : "set straight")).addLoreLine(Component.literal("place this next to a t-junction rail")).addLoreLine(Component.literal("click to flip by hand")).setCallback((i, t, a, g) -> {
			device.set_powered((ServerLevel) player.level(), !device.powered);
			draw_switch(gui, player, device);
		}).build());
	}

	public static void station(ServerPlayer player, RailDevice device) {
		DwGui gui = new DwGui(MenuType.GENERIC_9x3, player, false);
		gui.setTitle(Component.literal(device.name.isEmpty() ? "rail station" : device.name));
		SimpleContainer filter = new SimpleContainer(9) {
			@Override
			public void setChanged() {
				super.setChanged();
				for (int slot = 0; slot < 9; slot++) {
					device.filter.set(slot, getItem(slot));
				}
				device.setChanged();
			}
		};
		for (int slot = 0; slot < 9; slot++) {
			filter.setItem(slot, device.filter.get(slot));
		}
		draw_station(gui, player, device, filter);
		gui.open();
	}

	private static void draw_station(DwGui gui, ServerPlayer player, RailDevice device, SimpleContainer filter) {
		fill(gui, 27);
		for (int slot = 0; slot < 9; slot++) {
			gui.setSlot(slot, new Slot(filter, slot, 0, 0));
		}
		gui.setSlot(18, new GuiElementBuilder(Items.NAME_TAG).setName(Component.literal(device.name.isEmpty() ? "name this station" : "name: " + device.name)).addLoreLine(Component.literal("carts with a ticket here stop here")).setCallback((i, t, a, g) -> rename(player, device)).build());
		gui.setSlot(19, new GuiElementBuilder(Items.HOPPER).setName(Component.literal("mode: " + device.mode)).addLoreLine(Component.literal("stop: carts bound here stop")).addLoreLine(Component.literal("unload: empty matching cargo into the chest next to it")).addLoreLine(Component.literal("load: fill passing chest carts from that chest")).addLoreLine(Component.literal("dispatch: send a waiting cart on a timer")).addLoreLine(Component.literal("top row is the cargo filter, empty means all")).setCallback((i, t, a, g) -> {
			device.mode = RailDevice.modes.get((RailDevice.modes.indexOf(device.mode) + 1) % RailDevice.modes.size());
			device.setChanged();
			draw_station(gui, player, device, filter);
		}).build());
		if (device.mode.equals("dispatch")) {
			List<String> names = RailNetwork.names(player.level().getServer());
			gui.setSlot(21, new GuiElementBuilder(Items.FILLED_MAP).setName(Component.literal("sends to: " + (device.dispatch_to.isEmpty() ? "nowhere in particular" : device.dispatch_to))).setCallback((i, t, a, g) -> {
				if (!names.isEmpty()) {
					int next = names.indexOf(device.dispatch_to) + 1;
					device.dispatch_to = next >= names.size() ? "" : names.get(next);
					device.setChanged();
				}
				draw_station(gui, player, device, filter);
			}).build());
			gui.setSlot(22, new GuiElementBuilder(Items.CLOCK).setName(Component.literal("every " + device.interval + " min")).setCallback((i, t, a, g) -> {
				int at = 0;
				for (int k = 0; k < RailDevice.intervals.length; k++) {
					if (RailDevice.intervals[k] == device.interval) {
						at = k;
					}
				}
				device.interval = RailDevice.intervals[(at + 1) % RailDevice.intervals.length];
				device.setChanged();
				draw_station(gui, player, device, filter);
			}).build());
			gui.setSlot(23, new GuiElementBuilder(Items.COMPASS).setName(Component.literal("pushes " + device.dispatch_dir.getName())).setCallback((i, t, a, g) -> {
				device.dispatch_dir = device.dispatch_dir.getClockWise();
				device.setChanged();
				draw_station(gui, player, device, filter);
			}).build());
		}
	}

	private static void rename(ServerPlayer player, RailDevice device) {
		AnvilInputGui anvil = new AnvilInputGui(player, false);
		anvil.setTitle(Component.literal("station name"));
		anvil.setDefaultInputValue(device.name.isEmpty() ? "station" : device.name);
		anvil.setSlot(2, new GuiElementBuilder(Items.NAME_TAG).setName(Component.literal("save")).setCallback((i, t, a, g) -> {
			String name = anvil.getInput().trim().toLowerCase();
			if (name.length() > 24) {
				name = name.substring(0, 24);
			}
			if (!name.isEmpty() && RailNetwork.get(player.level().getServer()).stations.containsKey(name) && !name.equals(device.name)) {
				Hud.notice(player, Component.literal("another station already has that name"));
				return;
			}
			device.name = name;
			device.setChanged();
			RailNetwork.put(player.level().getServer(), name, device.getBlockPos());
			anvil.close();
		}).build());
		anvil.open();
	}

	public static void signal(ServerPlayer player) {
		Hud.notice(player, Component.literal("signals hold carts while the track ahead, up to the next signal, is occupied"));
	}
}
