package dev.kaileh57.deepworld.guild;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class JournalGui extends SimpleGui {
	private String open_discipline = "";
	private String note = "";

	public JournalGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x6, player, false);
		setTitle(Component.literal("guild journal"));
		draw();
	}

	private void draw() {
		for (int index = 0; index < 54; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		Guild.State state = Guild.of(player);
		int slot = 0;
		for (Guild.Discipline discipline : Guild.disciplines.values()) {
			boolean focused = state.focus().contains(discipline.id());
			long done = Guild.milestones.stream().filter(m -> m.discipline().equals(discipline.id()) && state.milestones().contains(m.id())).count();
			long total = Guild.milestones.stream().filter(m -> m.discipline().equals(discipline.id())).count();
			GuiElementBuilder button = new GuiElementBuilder(BuiltInRegistries.ITEM.getValue(Identifier.parse(discipline.icon())))
				.setName(Component.literal(discipline.name()).withStyle(focused ? ChatFormatting.GOLD : ChatFormatting.WHITE))
				.addLoreLine(Component.literal(done + "/" + total + " milestones").withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal(focused ? "focused · " + discipline.perk() : "focus: " + discipline.perk()).withStyle(focused ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY))
				.setCallback((index, type, action, gui) -> {
					open_discipline = discipline.id();
					draw();
				});
			if (focused) {
				button.glow();
			}
			setSlot(slot, button.build());
			slot++;
		}
		if (!open_discipline.isEmpty()) {
			Guild.Discipline discipline = Guild.disciplines.get(open_discipline);
			int row = 18;
			for (Guild.Milestone milestone : Guild.milestones) {
				if (!milestone.discipline().equals(open_discipline)) {
					continue;
				}
				boolean done = state.milestones().contains(milestone.id());
				int progress = Math.min(milestone.target(), state.counters().getOrDefault(milestone.counter(), 0));
				setSlot(row++, new GuiElementBuilder(done ? Items.ENCHANTED_BOOK : Items.BOOK)
					.setName(Component.literal(milestone.name()).withStyle(done ? ChatFormatting.GOLD : ChatFormatting.WHITE))
					.addLoreLine(Component.literal(milestone.text()).withStyle(ChatFormatting.GRAY))
					.addLoreLine(Component.literal(progress + " / " + milestone.target()).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
					.build());
			}
			boolean focused = state.focus().contains(open_discipline);
			setSlot(49, new GuiElementBuilder(focused ? Items.REDSTONE_TORCH : Items.TORCH)
				.setName(Component.literal(focused ? "drop focus" : "focus on " + discipline.name()))
				.addLoreLine(Component.literal(discipline.perk()).withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal(note).withStyle(ChatFormatting.YELLOW))
				.setCallback((index, type, action, gui) -> {
					note = Guild.toggle_focus(player, open_discipline);
					draw();
				}).build());
		}
	}
}
