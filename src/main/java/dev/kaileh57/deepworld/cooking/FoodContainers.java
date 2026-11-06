package dev.kaileh57.deepworld.cooking;

import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.hud.DwGui;
import dev.kaileh57.deepworld.hud.Hud;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;

public final class FoodContainers {
	public static final int hamper_day_minutes = 20;

	private FoodContainers() {
	}

	public static int size(ItemStack stack) {
		if (stack.is(Content.item("lunchbox"))) {
			return 4;
		}
		if (stack.is(Content.item("ration_pack"))) {
			return 8;
		}
		if (stack.is(Content.item("insulated_hamper"))) {
			return 9;
		}
		return 0;
	}

	public static boolean is_food(ItemStack stack) {
		return stack.is(Content.item("dish")) || stack.is(Content.item("drink")) || CookData.is_ingredient(stack) || stack.has(DataComponents.FOOD);
	}

	public static boolean accepts(ItemStack box, int slot, ItemStack stack) {
		if (size(stack) > 0) {
			return false;
		}
		if (box.is(Content.item("insulated_hamper")) && slot == 0) {
			return stack.is(Items.ICE) || stack.is(Items.PACKED_ICE) || stack.is(Items.BLUE_ICE);
		}
		if (box.is(Content.item("ration_pack"))) {
			return CookData.preserved(stack) && is_food(stack);
		}
		return is_food(stack);
	}

	public static NonNullList<ItemStack> contents(ItemStack box) {
		NonNullList<ItemStack> list = NonNullList.withSize(size(box), ItemStack.EMPTY);
		box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(list);
		return list;
	}

	public static void store(ItemStack box, List<ItemStack> list) {
		box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list));
		int count = 0;
		for (ItemStack stack : list) {
			count += stack.isEmpty() ? 0 : 1;
		}
		box.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(count + "/" + list.size() + " filled").withStyle(ChatFormatting.GRAY))));
	}

	public static void chill(ItemStack hamper, int rate) {
		NonNullList<ItemStack> list = contents(hamper);
		CompoundTag tag = CookData.tag(hamper);
		boolean iced = !list.get(0).isEmpty();
		if (iced) {
			int minutes = tag.getIntOr("chill", 0) + 1;
			if (minutes >= hamper_day_minutes) {
				minutes = 0;
				list.get(0).shrink(1);
			}
			tag.putInt("chill", minutes);
			CookData.set_tag(hamper, tag);
		} else {
			for (int slot = 1; slot < list.size(); slot++) {
				list.set(slot, Spoilage.age(list.get(slot), rate));
			}
		}
		store(hamper, list);
	}

	public static String seal(ItemStack jar, ItemStack food) {
		CompoundTag tag = CookData.tag(food);
		if (tag.getStringOr("dish", "").isEmpty()) {
			return "only cooked dishes go in a jar";
		}
		if (tag.getBooleanOr("preserved", false)) {
			return "that's already preserved";
		}
		tag.putBoolean("preserved", true);
		tag.remove("fresh");
		CookData.set_tag(food, tag);
		List<Component> lines = new ArrayList<>();
		ItemLore lore = food.get(DataComponents.LORE);
		if (lore != null) {
			lines.addAll(lore.lines());
		}
		lines.add(Component.literal("jarred").withStyle(ChatFormatting.AQUA));
		food.set(DataComponents.LORE, new ItemLore(lines));
		jar.shrink(1);
		return "sealed in a jar";
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer server_player)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(Content.item("jar"))) {
				ItemStack food = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
				Hud.notice(server_player, Component.literal(seal(stack, food)));
				return InteractionResult.SUCCESS_SERVER;
			}
			if (size(stack) > 0) {
				new Gui(server_player, stack, hand).open();
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
	}

	public static class Gui extends DwGui {
		private final ItemStack box;
		private final SimpleContainer slots;

		public Gui(ServerPlayer player, ItemStack box, InteractionHand hand) {
			super(MenuType.GENERIC_9x1, player, false);
			this.box = box;
			NonNullList<ItemStack> list = contents(box);
			this.slots = new SimpleContainer(list.size());
			for (int slot = 0; slot < list.size(); slot++) {
				slots.setItem(slot, list.get(slot));
			}
			setTitle(box.getHoverName());
			for (int index = 0; index < 9; index++) {
				setSlot(index, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).hideTooltip().build());
			}
			for (int slot = 0; slot < list.size(); slot++) {
				final int index = slot;
				setSlot(slot, new Slot(slots, slot, 0, 0) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return accepts(box, index, stack);
					}
				});
			}
			if (box.is(Content.item("lunchbox"))) {
				setSlot(8, new GuiElementBuilder(Items.BOWL).setName(Component.literal("assemble")).addLoreLine(Component.literal("turns a known set into one boxed meal")).setCallback((i, t, a, g) -> assemble()).build());
			}
			if (box.is(Content.item("insulated_hamper"))) {
				setSlot(8, new GuiElementBuilder(Items.SNOWBALL).setName(Component.literal("ice goes in the first slot")).addLoreLine(Component.literal("one block lasts a day")).build());
			}
		}

		private void assemble() {
			List<ItemStack> inputs = new ArrayList<>();
			for (int slot = 0; slot < slots.getContainerSize(); slot++) {
				if (!slots.getItem(slot).isEmpty()) {
					inputs.add(slots.getItem(slot));
				}
			}
			CookData.Dish dish = Dishes.match("lunchbox", inputs);
			if (dish == null) {
				Hud.notice(player, Component.literal("that isn't a boxed meal anyone knows"));
				return;
			}
			for (int slot = 0; slot < slots.getContainerSize(); slot++) {
				slots.setItem(slot, ItemStack.EMPTY);
			}
			slots.setItem(0, Dishes.make(dish, 2, player.getGameProfile().name()));
			Hud.notice(player, Component.literal("packed a " + dish.name()));
		}

		@Override
		public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
			if (type.numKey || type == ClickType.OFFHAND_SWAP) {
				return false;
			}
			if (index >= getVirtualSize()) {
				Slot slot = getSlotRedirectOrPlayer(index);
				if (slot != null && slot.getItem() == box) {
					return false;
				}
			}
			return super.onAnyClick(index, type, action);
		}

		@Override
		public void onRemoved() {
			List<ItemStack> list = new ArrayList<>();
			for (int slot = 0; slot < slots.getContainerSize(); slot++) {
				list.add(slots.getItem(slot));
			}
			store(box, list);
		}
	}
}
