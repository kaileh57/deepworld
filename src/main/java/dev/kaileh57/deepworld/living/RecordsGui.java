package dev.kaileh57.deepworld.living;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class RecordsGui extends SimpleGui {
	private static final int[] slots = {1, 2, 3, 5, 6, 7, 10, 11, 12, 14, 15, 16};
	private static final List<Item> icons = List.of(Items.DEEPSLATE, Items.BOWL, Items.CAKE, Items.COMPASS, Items.WRITABLE_BOOK, Items.EMERALD, Items.ANVIL, Items.NETHERITE_INGOT, Items.WHEAT, Items.EGG, Items.IRON_SWORD, Items.RAIL);

	public RecordsGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x3, player, false);
		setTitle(Component.literal("record board"));
		for (int index = 0; index < 27; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		Records records = Records.get(player.level().getServer());
		int slot = 0;
		for (Map.Entry<String, String> entry : Records.tracked.entrySet()) {
			Records.Entry best = records.best.get(entry.getKey());
			GuiElementBuilder builder = new GuiElementBuilder(icons.get(slot % icons.size())).setName(Component.literal(entry.getValue()));
			if (best == null) {
				builder.addLoreLine(Component.literal("unclaimed"));
			} else {
				builder.addLoreLine(Component.literal(best.name() + ": " + best.value()));
			}
			builder.addLoreLine(Component.literal("you: " + dev.kaileh57.deepworld.guild.Guild.counter(player, entry.getKey())));
			setSlot(slots[slot], builder.build());
			slot++;
		}
		Events.describe_goal(player.level().getServer()).ifPresent(text -> setSlot(22, new GuiElementBuilder(Items.BELL).setName(Component.literal("weekly goal")).addLoreLine(Component.literal(text)).build()));
	}
}
