package dev.kaileh57.deepworld.cooking;

import dev.kaileh57.deepworld.content.Content;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CookBlockEntity extends BlockEntity implements Container {
	public static final int output_slot = 6;
	public static final int window_every = 160;
	public static final int window_length = 50;
	public static final int burn_after = 600;
	private final NonNullList<ItemStack> stacks = NonNullList.withSize(7, ItemStack.EMPTY);
	public String status = "idle";
	public int progress;
	public int total;
	public int hits;
	public int misses;
	public int window_open = -1;
	public int done_ticks;
	public String dish = "";
	public String generic_name = "";
	public CompoundTag generic = new CompoundTag();
	public int base_quality;
	public String cook = "";
	public String cook_uuid = "";
	public String message = "";

	public CookBlockEntity(BlockPos pos, BlockState state) {
		super(Cooking.cook_entity, pos, state);
	}

	public CookData.Station station() {
		return ((StationBlock) getBlockState().getBlock()).station();
	}

	public boolean stirs() {
		return station().max_heat() > 0;
	}

	public boolean window_active() {
		return window_open >= 0 && progress - window_open < window_length;
	}

	public void start(ServerPlayer player) {
		if (!status.equals("idle")) {
			return;
		}
		if (!stacks.get(output_slot).isEmpty()) {
			message = "take the last dish out first";
			return;
		}
		CookData.Station station = station();
		List<ItemStack> inputs = new ArrayList<>();
		for (int slot = 0; slot < station.slots(); slot++) {
			if (!stacks.get(slot).isEmpty()) {
				inputs.add(stacks.get(slot));
			}
		}
		if (inputs.isEmpty()) {
			message = "add ingredients";
			return;
		}
		for (ItemStack input : inputs) {
			if (!CookData.is_ingredient(input)) {
				message = BuiltInRegistries.ITEM.getKey(input.getItem()).getPath().replace('_', ' ') + " is not an ingredient";
				return;
			}
		}
		Heat.Source heat = Heat.below(level, worldPosition);
		if (station.max_heat() > 0 && heat.level() == 0) {
			message = "needs heat below";
			return;
		}
		if (station.max_heat() > 0 && heat.level() > station.max_heat()) {
			message = "too hot for this cookware";
			return;
		}
		CookData.Dish match = Dishes.match(station.station(), inputs);
		boolean exact_heat = false;
		if (match != null) {
			int needed = CookData.heat_level(match.heat());
			if (station.max_heat() > 0 && heat.level() < needed) {
				message = match.name() + " needs " + match.heat() + " heat";
				return;
			}
			if (match.heat().equals("soul") && !heat.soul()) {
				message = match.name() + " needs soul fire";
				return;
			}
			exact_heat = heat.level() == needed;
		} else if (inputs.size() < 2) {
			message = "unknown recipe, add more";
			return;
		}
		double fresh_total = 0;
		for (ItemStack input : inputs) {
			fresh_total += Spoilage.fraction(input);
		}
		double fresh_avg = fresh_total / inputs.size();
		CookData.Dish result = match != null ? match : Dishes.generic(station.station(), inputs);
		dish = match != null ? match.id() : "";
		generic = new CompoundTag();
		if (match == null) {
			generic.putString("name", result.name());
			generic.putString("id", result.id());
			generic.putInt("nutrition", result.nutrition());
			generic.putBoolean("preserved", result.preserved());
			generic.putString("kind", result.kind());
			CompoundTag buffs = new CompoundTag();
			result.buffs().forEach(buffs::putInt);
			generic.put("buffs", buffs);
		}
		base_quality = (fresh_avg >= 0.6 ? 1 : 0) + (exact_heat ? 1 : 0) + (match != null ? 0 : -1);
		for (int slot = 0; slot < station.slots(); slot++) {
			ItemStack stack = stacks.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack remainder = remainder(stack);
			stack.shrink(1);
			if (!remainder.isEmpty() && !player.getInventory().add(remainder)) {
				player.drop(remainder, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		float heat_speed = station.max_heat() > 0 ? Heat.speed(heat.level()) : 1.0F;
		total = Math.max(100, Math.round(station.time() * 20 * station.speed() * heat_speed));
		progress = 0;
		hits = 0;
		misses = 0;
		window_open = -1;
		cook = player.getGameProfile().name();
		cook_uuid = player.getUUID().toString();
		status = "cooking";
		message = "cooking " + result.name();
		level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 1.2F);
		setChanged();
	}

	private static ItemStack remainder(ItemStack stack) {
		if (stack.is(Items.MILK_BUCKET) || stack.is(Items.WATER_BUCKET)) {
			return new ItemStack(Items.BUCKET);
		}
		if (stack.is(Items.HONEY_BOTTLE)) {
			return new ItemStack(Items.GLASS_BOTTLE);
		}
		return ItemStack.EMPTY;
	}

	public void stir(ServerPlayer player) {
		if (!status.equals("cooking") || !stirs()) {
			return;
		}
		if (window_active()) {
			hits++;
			window_open = -1;
			message = "nice stir";
			level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.5F, 1.6F);
		} else {
			message = "not yet";
		}
	}

	public void server_tick(ServerLevel level, BlockPos pos) {
		if (status.equals("cooking")) {
			progress++;
			if (stirs()) {
				if (window_open >= 0 && progress - window_open >= window_length) {
					misses++;
					window_open = -1;
					message = "missed a stir";
				}
				if (progress % window_every == window_every / 2 && progress + window_length < total) {
					window_open = progress;
					message = "stir now";
					level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6F, 1.4F);
				}
			}
			if (progress % 20 == 0) {
				level.sendParticles(stirs() ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
			}
			if (progress >= total) {
				finish(level, pos);
			}
			setChanged();
		} else if (status.equals("done")) {
			done_ticks++;
			if (stirs() && !station().no_burn() && done_ticks > burn_after && !stacks.get(output_slot).isEmpty() && !CookData.tag(stacks.get(output_slot)).getStringOr("dish", "").isEmpty() && CookData.tag(stacks.get(output_slot)).getIntOr("quality", 0) > 0) {
				CookData.Dish def = output_def();
				if (def != null) {
					ItemStack burnt = Dishes.make(def, 0, "");
					burnt.setCount(stacks.get(output_slot).getCount());
					stacks.set(output_slot, burnt);
					message = "it burnt";
					level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 0.8F);
				}
			}
			if (stacks.get(output_slot).isEmpty()) {
				status = "idle";
				done_ticks = 0;
			}
			setChanged();
		}
	}

	private CookData.Dish output_def() {
		if (!dish.isEmpty()) {
			return CookData.dishes.get(dish);
		}
		return generic_def();
	}

	private CookData.Dish generic_def() {
		java.util.Map<String, Integer> buffs = new java.util.LinkedHashMap<>();
		CompoundTag buff_tag = generic.getCompoundOrEmpty("buffs");
		for (String key : buff_tag.keySet()) {
			buffs.put(key, buff_tag.getIntOr(key, 1));
		}
		return new CookData.Dish(generic.getStringOr("id", "generic_stew"), generic.getStringOr("name", "plain stew"), station().station(), List.of(), buffs, generic.getIntOr("nutrition", 4), 0.6F, 0, 300, generic.getBooleanOr("preserved", false), "low", generic.getStringOr("kind", "dish"), "", "", 1);
	}

	private void finish(ServerLevel level, BlockPos pos) {
		ServerPlayer owner = cook_uuid.isEmpty() ? null : level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(cook_uuid));
		int quality = stirs() ? 1 + hits - misses + base_quality : 2 + base_quality;
		if (dev.kaileh57.deepworld.guild.Guild.focused(owner, "cook")) {
			quality++;
		}
		quality = Math.max(1, Math.min(stirs() ? 4 : 3, quality));
		CookData.Dish def = output_def();
		if (!def.result().isEmpty()) {
			int count = def.count() + (dev.kaileh57.deepworld.guild.Guild.focused(owner, "alchemist") ? 1 : 0);
			stacks.set(output_slot, new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(def.result())), count));
			status = "done";
			done_ticks = 0;
			window_open = -1;
			message = def.name() + " is ready";
			dev.kaileh57.deepworld.guild.Guild.add(owner, "extracts", 1);
			level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 1.6F);
			return;
		}
		if (def.drink() && station().station().equals("still")) {
			dev.kaileh57.deepworld.guild.Guild.add(owner, "extracts", 1);
		}
		dev.kaileh57.deepworld.guild.Guild.add(owner, "dishes_cooked", 1);
		if (quality >= 4) {
			dev.kaileh57.deepworld.guild.Guild.add(owner, "masterworks", 1);
		}
		if (owner != null && !dish.isEmpty() && dev.kaileh57.deepworld.guild.Guild.counter(owner, "cooked_" + dish) == 0) {
			dev.kaileh57.deepworld.guild.Guild.add(owner, "cooked_" + dish, 1);
			dev.kaileh57.deepworld.guild.Guild.add(owner, "signatures_found", 1);
		}
		stacks.set(output_slot, Dishes.make(def, quality, cook));
		status = "done";
		done_ticks = 0;
		window_open = -1;
		message = Dishes.quality_names[quality] + " " + def.name() + " is ready";
		level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.4F, 1.8F);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		ContainerHelper.loadAllItems(input, stacks);
		status = input.getStringOr("status", "idle");
		progress = input.getIntOr("progress", 0);
		total = input.getIntOr("total", 0);
		hits = input.getIntOr("hits", 0);
		misses = input.getIntOr("misses", 0);
		dish = input.getStringOr("dish", "");
		base_quality = input.getIntOr("base_quality", 0);
		cook = input.getStringOr("cook", "");
		cook_uuid = input.getStringOr("cook_uuid", "");
		generic = input.read("generic", CompoundTag.CODEC).orElse(new CompoundTag());
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, stacks);
		output.putString("status", status);
		output.putInt("progress", progress);
		output.putInt("total", total);
		output.putInt("hits", hits);
		output.putInt("misses", misses);
		output.putString("dish", dish);
		output.putInt("base_quality", base_quality);
		output.putString("cook", cook);
		output.putString("cook_uuid", cook_uuid);
		output.store("generic", CompoundTag.CODEC, generic);
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
