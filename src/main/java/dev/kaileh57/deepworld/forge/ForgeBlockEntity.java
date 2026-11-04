package dev.kaileh57.deepworld.forge;

import dev.kaileh57.deepworld.content.Content;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ForgeBlockEntity extends BlockEntity implements Container {
	public static final int input_first = 0;
	public static final int input_count = 3;
	public static final int fuel_slot = 3;
	public static final int output_slot = 4;
	private final NonNullList<ItemStack> stacks = NonNullList.withSize(5, ItemStack.EMPTY);
	public int burn_left;
	public int burn_total;
	public int progress;
	public int progress_total;

	public ForgeBlockEntity(BlockPos pos, BlockState state) {
		super(Content.forge_entity, pos, state);
	}

	public String station() {
		return ((ForgeBlock) getBlockState().getBlock()).station();
	}

	public void server_tick(ServerLevel level, BlockPos pos, BlockState state) {
		boolean was_lit = burn_left > 0;
		if (burn_left > 0) {
			burn_left--;
		}
		ForgingRecipes.Recipe recipe = ForgingRecipes.find(station(), this, input_first, input_count);
		boolean can_output = recipe != null && fits(recipe);
		if (can_output && burn_left == 0) {
			ItemStack fuel = stacks.get(fuel_slot);
			Map<Item, Integer> fuels = Content.station_fuel.getOrDefault(station(), Map.of());
			Integer value = fuels.get(fuel.getItem());
			if (value != null) {
				burn_left = value;
				burn_total = value;
				if (fuel.is(Items.LAVA_BUCKET)) {
					stacks.set(fuel_slot, new ItemStack(Items.BUCKET));
				} else {
					fuel.shrink(1);
				}
			}
		}
		if (can_output && burn_left > 0) {
			progress_total = recipe.time();
			progress++;
			if (progress >= progress_total) {
				recipe.consume(this, input_first, input_count);
				ItemStack output = stacks.get(output_slot);
				if (output.isEmpty()) {
					stacks.set(output_slot, new ItemStack(recipe.result(), recipe.result_count()));
				} else {
					output.grow(recipe.result_count());
				}
				progress = 0;
			}
		} else if (progress > 0) {
			progress = Math.max(0, progress - 2);
		}
		boolean now_lit = burn_left > 0;
		if (was_lit != now_lit) {
			level.setBlock(pos, state.setValue(ForgeBlock.lit, now_lit), 3);
		}
		setChanged();
	}

	private boolean fits(ForgingRecipes.Recipe recipe) {
		ItemStack output = stacks.get(output_slot);
		if (output.isEmpty()) {
			return true;
		}
		return output.is(recipe.result()) && output.getCount() + recipe.result_count() <= output.getMaxStackSize();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		ContainerHelper.loadAllItems(input, stacks);
		burn_left = input.getIntOr("burn_left", 0);
		burn_total = input.getIntOr("burn_total", 0);
		progress = input.getIntOr("progress", 0);
		progress_total = input.getIntOr("progress_total", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, stacks);
		output.putInt("burn_left", burn_left);
		output.putInt("burn_total", burn_total);
		output.putInt("progress", progress);
		output.putInt("progress_total", progress_total);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, this);
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
