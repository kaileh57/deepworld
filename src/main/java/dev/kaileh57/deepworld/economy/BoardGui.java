package dev.kaileh57.deepworld.economy;

import dev.kaileh57.deepworld.guild.Guild;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BoardGui extends dev.kaileh57.deepworld.hud.DwGui {
	private String note = "";

	public BoardGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x6, player, false);
		setTitle(Component.literal("commissions"));
		draw();
	}

	private void draw() {
		for (int index = 0; index < 54; index++) {
			setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
		}
		Commissions board = Commissions.get(player.level().getServer());
		String me = player.getUUID().toString();
		int slot = 0;
		for (Commissions.Commission commission : new ArrayList<>(board.open)) {
			if (slot >= 36) {
				break;
			}
			boolean mine = commission.poster().equals(me);
			boolean taken = !commission.acceptor().isEmpty();
			boolean my_job = commission.acceptor().equals(me);
			GuiElementBuilder button = new GuiElementBuilder(commission.wanted_item(), Math.min(64, commission.count()))
				.setName(Component.literal("wanted: " + commission.count() + " " + new ItemStack(commission.wanted_item()).getHoverName().getString().toLowerCase()))
				.addLoreLine(Component.literal("reward: " + summary(commission.reward())).withStyle(ChatFormatting.GOLD))
				.addLoreLine(Component.literal("from " + commission.poster_name()).withStyle(ChatFormatting.GRAY))
				.addLoreLine(Component.literal(my_job ? "yours, click to deliver" : taken ? "taken by " + commission.acceptor_name() : mine ? "click to cancel" : "click to accept").withStyle(ChatFormatting.YELLOW));
			if (my_job) {
				button.glow();
			}
			button.setCallback((index, type, action, gui) -> {
				note = act(commission);
				draw();
			});
			setSlot(slot++, button.build());
		}
		List<ItemStack> mail = board.mail.getOrDefault(me, List.of());
		setSlot(48, new GuiElementBuilder(Items.WRITABLE_BOOK).setName(Component.literal("post a commission")).addLoreLine(Component.literal(note).withStyle(ChatFormatting.YELLOW)).setCallback((index, type, action, gui) -> new PostGui(player).open()).build());
		setSlot(50, new GuiElementBuilder(mail.isEmpty() ? Items.CHEST : Items.ENDER_CHEST).setName(Component.literal("collect deliveries (" + mail.size() + ")")).setCallback((index, type, action, gui) -> {
			List<ItemStack> items = board.mail.remove(me);
			if (items != null) {
				for (ItemStack stack : items) {
					if (!player.getInventory().add(stack)) {
						player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
					}
				}
				board.setDirty();
			}
			draw();
		}).build());
	}

	private static String summary(List<ItemStack> reward) {
		StringBuilder text = new StringBuilder();
		for (ItemStack stack : reward) {
			if (stack.isEmpty()) {
				continue;
			}
			if (!text.isEmpty()) {
				text.append(", ");
			}
			text.append(stack.getCount()).append(' ').append(stack.getHoverName().getString().toLowerCase());
		}
		return text.isEmpty() ? "nothing" : text.toString();
	}

	private int count(net.minecraft.world.item.Item item) {
		int total = 0;
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item) && !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private String act(Commissions.Commission commission) {
		Commissions board = Commissions.get(player.level().getServer());
		String me = player.getUUID().toString();
		int index = board.open.indexOf(commission);
		if (index < 0) {
			return "that one is gone";
		}
		if (commission.acceptor().isEmpty()) {
			if (commission.poster().equals(me)) {
				board.open.remove(index);
				for (ItemStack stack : commission.reward()) {
					board.send_mail(me, stack.copy());
				}
				board.setDirty();
				return "cancelled, reward is in your deliveries";
			}
			board.open.set(index, commission.accepted_by(me, player.getGameProfile().name()));
			board.setDirty();
			return "accepted";
		}
		if (!commission.acceptor().equals(me)) {
			return "someone else took that";
		}
		if (count(commission.wanted_item()) < commission.count()) {
			return "you need " + commission.count() + " " + new ItemStack(commission.wanted_item()).getHoverName().getString().toLowerCase();
		}
		int left = commission.count();
		var inventory = player.getInventory();
		List<ItemStack> delivered = new ArrayList<>();
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(commission.wanted_item()) && !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
				int take = Math.min(left, stack.getCount());
				delivered.add(stack.split(take));
				left -= take;
			}
		}
		board.open.remove(index);
		if (!commission.npc()) {
			for (ItemStack stack : delivered) {
				board.send_mail(commission.poster(), stack);
			}
		}
		for (ItemStack stack : commission.reward()) {
			if (!player.getInventory().add(stack.copy())) {
				player.drop(stack.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		board.setDirty();
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.8F, 1.2F);
		Guild.add(player, "commissions_done", 1);
		return "delivered, reward paid";
	}
}
