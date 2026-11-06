package dev.kaileh57.deepworld.transit;

import com.mojang.serialization.Codec;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.hud.Hud;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class Carts {
	public static final AttachmentType<String> destination = AttachmentRegistry.create(DeepWorld.id("destination"), builder -> builder.persistent(Codec.STRING));
	public static final AttachmentType<String> ticket = AttachmentRegistry.create(DeepWorld.id("ticket"), builder -> builder.persistent(Codec.STRING).copyOnDeath());
	public static final AttachmentType<UUID> leader = AttachmentRegistry.create(DeepWorld.id("leader"), builder -> builder.persistent(UUIDUtil.CODEC));
	public static final double spacing = 1.625;
	public static final int max_train = 6;
	private static final Map<UUID, ArrayDeque<Vec3>> history = new HashMap<>();
	private static final Map<UUID, UUID> picking = new HashMap<>();
	private static final Set<UUID> followers = new HashSet<>();

	private Carts() {
	}

	public static String destination_of(AbstractMinecart cart) {
		String own = cart.getAttached(destination);
		if (own != null && !own.isEmpty()) {
			return own;
		}
		for (Entity passenger : cart.getPassengers()) {
			if (passenger instanceof Player player) {
				String held = player.getAttached(ticket);
				if (held != null && !held.isEmpty()) {
					return held;
				}
			}
		}
		AbstractMinecart lead = lead_of(cart);
		return lead != cart ? destination_of(lead) : "";
	}

	public static void arrived(AbstractMinecart cart, String station) {
		cart.removeAttached(destination);
		for (Entity passenger : cart.getPassengers()) {
			if (passenger instanceof ServerPlayer player && station.equals(player.getAttached(ticket))) {
				player.removeAttached(ticket);
				Hud.notice(player, Component.literal("arrived at " + station));
			}
		}
	}

	public static AbstractMinecart leader_of(AbstractMinecart cart) {
		UUID id = cart.getAttached(leader);
		if (id == null || !(cart.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getEntity(id) instanceof AbstractMinecart found && !found.isRemoved() ? found : null;
	}

	public static AbstractMinecart lead_of(AbstractMinecart cart) {
		AbstractMinecart current = cart;
		for (int depth = 0; depth < max_train; depth++) {
			AbstractMinecart next = leader_of(current);
			if (next == null) {
				return current;
			}
			current = next;
		}
		return current;
	}

	public static int length_from(AbstractMinecart lead) {
		return 1 + train_behind(lead).size();
	}

	public static List<AbstractMinecart> train_behind(AbstractMinecart lead) {
		List<AbstractMinecart> out = new ArrayList<>();
		if (!(lead.level() instanceof ServerLevel level)) {
			return out;
		}
		AbstractMinecart current = lead;
		for (int depth = 0; depth < max_train; depth++) {
			AbstractMinecart next = null;
			for (AbstractMinecart other : level.getEntitiesOfClass(AbstractMinecart.class, current.getBoundingBox().inflate(4))) {
				if (current.getUUID().equals(other.getAttached(leader))) {
					next = other;
					break;
				}
			}
			if (next == null) {
				break;
			}
			out.add(next);
			current = next;
		}
		return out;
	}

	public static boolean same_train(AbstractMinecart a, AbstractMinecart b) {
		return lead_of(a) == lead_of(b);
	}

	public static String couple(AbstractMinecart front, AbstractMinecart back) {
		if (front == back) {
			return "pick a second cart";
		}
		if (front.distanceTo(back) > 4) {
			return "too far apart";
		}
		if (same_train(front, back)) {
			return "already in this train";
		}
		if (back.getAttached(leader) != null) {
			return "that cart is already coupled";
		}
		AbstractMinecart lead = lead_of(front);
		if (length_from(lead) + length_from(back) > max_train) {
			return "trains are at most " + max_train + " carts";
		}
		AbstractMinecart tail = front;
		List<AbstractMinecart> behind = train_behind(front);
		if (!behind.isEmpty()) {
			tail = behind.get(behind.size() - 1);
		}
		back.setAttached(leader, tail.getUUID());
		return "coupled";
	}

	public static void uncouple(AbstractMinecart cart) {
		cart.removeAttached(leader);
		history.remove(cart.getUUID());
	}

	public static boolean cabin_lever(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
		return level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LEVER) && level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS) && level.getBlockState(pos.above(2)).is(net.minecraft.world.level.block.Blocks.LANTERN);
	}

	public static void register() {
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer server_player) || !cabin_lever(level, hit.getBlockPos())) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.IRON_INGOT) || stack.getCount() < 4) {
				Hud.notice(server_player, Component.literal("the old switch is rusted solid. 4 iron ingots would fix it"));
				return InteractionResult.SUCCESS_SERVER;
			}
			stack.shrink(4);
			level.setBlockAndUpdate(hit.getBlockPos(), dev.kaileh57.deepworld.content.Content.blocks.get("switch_box").defaultBlockState());
			level.playSound(null, hit.getBlockPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
			Hud.notice(server_player, Component.literal("the cabin switch works again"));
			return InteractionResult.SUCCESS_SERVER;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer server_player) || !(entity instanceof AbstractMinecart cart)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(Items.IRON_CHAIN)) {
				UUID first = picking.remove(player.getUUID());
				if (first == null || !(((ServerLevel) level).getEntity(first) instanceof AbstractMinecart front)) {
					picking.put(player.getUUID(), cart.getUUID());
					Hud.notice(server_player, Component.literal("now click the cart that goes behind it"));
					return InteractionResult.SUCCESS_SERVER;
				}
				String result = couple(front, cart);
				if (result.equals("coupled")) {
					if (!player.isCreative()) {
						stack.shrink(1);
					}
					level.playSound(null, cart.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
				Hud.notice(server_player, Component.literal(result));
				return InteractionResult.SUCCESS_SERVER;
			}
			if (stack.is(Items.SHEARS) && cart.getAttached(leader) != null) {
				uncouple(cart);
				if (!player.isCreative()) {
					player.getInventory().add(new ItemStack(Items.IRON_CHAIN));
				}
				level.playSound(null, cart.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
				Hud.notice(server_player, Component.literal("uncoupled"));
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
	}

	public static void record(AbstractMinecart lead) {
		ArrayDeque<Vec3> trail = history.computeIfAbsent(lead.getUUID(), id -> new ArrayDeque<>());
		Vec3 here = lead.position();
		if (trail.isEmpty() || trail.peekFirst().distanceToSqr(here) > 1.0E-4) {
			trail.addFirst(here);
			while (trail.size() > 200) {
				trail.removeLast();
			}
		}
	}

	public static Vec3 behind(AbstractMinecart front, Vec3 fallback) {
		ArrayDeque<Vec3> trail = history.get(front.getUUID());
		Vec3 previous = front.position();
		double walked = 0;
		if (trail != null) {
			for (Vec3 point : trail) {
				double step = previous.distanceTo(point);
				if (walked + step >= spacing && step > 0) {
					double t = (spacing - walked) / step;
					return previous.add(point.subtract(previous).scale(t));
				}
				walked += step;
				previous = point;
			}
		}
		Vec3 offset = fallback.subtract(front.position());
		if (offset.lengthSqr() < 1.0E-4) {
			return fallback;
		}
		return front.position().add(offset.normalize().scale(spacing));
	}

	public static void tick(MinecraftServer server) {
		followers.clear();
		for (ServerLevel level : server.getAllLevels()) {
			List<AbstractMinecart> coupled = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof AbstractMinecart cart && cart.getAttached(leader) != null) {
					coupled.add(cart);
				}
			}
			for (AbstractMinecart cart : coupled) {
				AbstractMinecart front = leader_of(cart);
				if (front == null || front.distanceTo(cart) > 8) {
					uncouple(cart);
					continue;
				}
				record(front);
				followers.add(cart.getUUID());
			}
			coupled.sort((a, b) -> Integer.compare(depth(a), depth(b)));
			for (AbstractMinecart cart : coupled) {
				AbstractMinecart front = leader_of(cart);
				if (front == null) {
					continue;
				}
				Vec3 target = behind(front, cart.position());
				cart.setPos(target.x, target.y, target.z);
				cart.setDeltaMovement(front.getDeltaMovement());
				record(cart);
			}
		}
		Iterator<UUID> keys = history.keySet().iterator();
		while (keys.hasNext()) {
			UUID id = keys.next();
			boolean used = false;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(id) != null) {
					used = true;
					break;
				}
			}
			if (!used) {
				keys.remove();
			}
		}
	}

	private static int depth(AbstractMinecart cart) {
		int depth = 0;
		AbstractMinecart current = cart;
		while (depth < max_train && (current = leader_of(current)) != null) {
			depth++;
		}
		return depth;
	}

	public static boolean follower(AbstractMinecart cart) {
		return followers.contains(cart.getUUID());
	}
}
