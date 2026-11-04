package dev.kaileh57.deepworld.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ShopBlockEntity extends BlockEntity implements Container {
	public static final int stock_first = 0;
	public static final int stock_count = 18;
	public static final int till_first = 18;
	public static final int till_count = 9;
	public static final int config_first = 27;
	public static final int trades = 4;
	private final NonNullList<ItemStack> stacks = NonNullList.withSize(config_first + trades * 2, ItemStack.EMPTY);
	public String owner = "";
	public String owner_name = "";

	public ShopBlockEntity(BlockPos pos, BlockState state) {
		super(Economy.shop_entity, pos, state);
	}

	public ItemStack offer(int trade) {
		return stacks.get(config_first + trade * 2);
	}

	public ItemStack price(int trade) {
		return stacks.get(config_first + trade * 2 + 1);
	}

	public int stock_of(ItemStack wanted) {
		int total = 0;
		for (int slot = stock_first; slot < stock_first + stock_count; slot++) {
			if (ItemStack.isSameItemSameComponents(stacks.get(slot), wanted)) {
				total += stacks.get(slot).getCount();
			}
		}
		return total;
	}

	public String buy(Player buyer, int trade) {
		ItemStack offer = offer(trade);
		ItemStack price = price(trade);
		if (offer.isEmpty() || price.isEmpty()) {
			return "nothing for sale there";
		}
		if (stock_of(offer) < offer.getCount()) {
			return "out of stock";
		}
		var inventory = buyer.getInventory();
		int have = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (ItemStack.isSameItemSameComponents(inventory.getItem(slot), price)) {
				have += inventory.getItem(slot).getCount();
			}
		}
		if (have < price.getCount()) {
			return "you need " + price.getCount() + " " + price.getHoverName().getString().toLowerCase();
		}
		ItemStack payment = price.copy();
		if (!fits_till(payment)) {
			return "the till is full";
		}
		int left = price.getCount();
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (ItemStack.isSameItemSameComponents(stack, price)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		add_to_till(payment);
		int give = offer.getCount();
		for (int slot = stock_first; slot < stock_first + stock_count && give > 0; slot++) {
			ItemStack stack = stacks.get(slot);
			if (ItemStack.isSameItemSameComponents(stack, offer)) {
				int take = Math.min(give, stack.getCount());
				ItemStack bought = stack.split(take);
				give -= take;
				if (!buyer.getInventory().add(bought)) {
					buyer.drop(bought, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
		}
		setChanged();
		return "bought " + offer.getCount() + " " + offer.getHoverName().getString().toLowerCase();
	}

	private boolean fits_till(ItemStack payment) {
		int room = 0;
		for (int slot = till_first; slot < till_first + till_count; slot++) {
			ItemStack stack = stacks.get(slot);
			if (stack.isEmpty()) {
				room += payment.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(stack, payment)) {
				room += stack.getMaxStackSize() - stack.getCount();
			}
		}
		return room >= payment.getCount();
	}

	private void add_to_till(ItemStack payment) {
		for (int slot = till_first; slot < till_first + till_count && !payment.isEmpty(); slot++) {
			ItemStack stack = stacks.get(slot);
			if (stack.isEmpty()) {
				stacks.set(slot, payment.copy());
				payment.setCount(0);
			} else if (ItemStack.isSameItemSameComponents(stack, payment)) {
				int move = Math.min(payment.getCount(), stack.getMaxStackSize() - stack.getCount());
				stack.grow(move);
				payment.shrink(move);
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		ContainerHelper.loadAllItems(input, stacks);
		owner = input.getStringOr("owner", "");
		owner_name = input.getStringOr("owner_name", "");
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, stacks);
		output.putString("owner", owner);
		output.putString("owner_name", owner_name);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			for (int slot = 0; slot < config_first; slot++) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stacks.get(slot));
			}
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	public int getContainerSize() {
		return stacks.size();
	}

	@Override
	public boolean isEmpty() {
		return stacks.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return stacks.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack result = ContainerHelper.removeItem(stacks, slot, count);
		setChanged();
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(stacks, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		stacks.set(slot, stack);
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		stacks.clear();
	}
}
