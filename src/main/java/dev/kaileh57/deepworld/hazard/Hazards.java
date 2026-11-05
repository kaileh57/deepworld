package dev.kaileh57.deepworld.hazard;

import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.alchemy.Blasting;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.cooking.Meals;
import dev.kaileh57.deepworld.cooking.Spoilage;
import dev.kaileh57.deepworld.heat.HeatSystem;
import dev.kaileh57.deepworld.hud.Hud;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class Hazards {
	public static final TagKey<Block> supports = TagKey.create(Registries.BLOCK, DeepWorld.id("supports"));
	public static final int tide_base = 490;
	public static final int tide_amplitude = 5;
	public static final long tide_period = 48000L;
	private record Scheduled(ServerLevel level, BlockPos pos, long at, String kind) {
	}

	private static final List<Scheduled> scheduled = new ArrayList<>();
	private static final Set<BlockPos> tide_added = new HashSet<>();
	private static final Set<BlockPos> tide_removed = new HashSet<>();

	private Hazards() {
	}

	public static HazardConfig config() {
		return HazardConfig.current;
	}

	public static void register() {
		HazardConfig.load();
		HazardTests.register();
		Spoilage.enabled = config().spoilage;
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (!(level instanceof ServerLevel server_level) || !(player instanceof ServerPlayer server_player)) {
				return;
			}
			if (config().cave_ins && pos.getY() >= 600 && pos.getY() < 1000) {
				maybe_cave_in(server_level, pos, 0.12F);
			}
			if (config().echoes && pos.getY() >= 700 && pos.getY() < 800 && !hushed(server_player) && !CookData.tag(server_player.getMainHandItem()).getStringOr("socket", "").equals("echo")) {
				echo(server_level, server_player, 40);
			}
		});
		Blasting.after_blast.add((level, pos) -> {
			if (config().cave_ins && pos.getY() >= 600 && pos.getY() < 1000) {
				maybe_cave_in(level, pos, 0.6F);
			}
			if (config().firedamp && gassy(level, pos)) {
				ignite(level, pos);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server_level) || !(player instanceof ServerPlayer)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			BlockPos placed = hit.getBlockPos().relative(hit.getDirection());
			if (config().firedamp && is_flame(stack) && gassy(server_level, placed)) {
				scheduled.add(new Scheduled(server_level, placed, server_level.getGameTime() + 8, "firedamp"));
			}
			if (config().darkness && stack.is(Items.TORCH) && placed.getY() >= 300 && placed.getY() < 450) {
				scheduled.add(new Scheduled(server_level, placed, server_level.getGameTime() + 6000, "burnout"));
			}
			return InteractionResult.PASS;
		});
		HeatSystem.add_protection(player -> 0);
	}

	private static boolean is_flame(ItemStack stack) {
		return stack.is(Items.TORCH) || stack.is(Items.LANTERN) || stack.is(Items.CAMPFIRE) || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.SOUL_TORCH) || stack.is(Items.SOUL_LANTERN) || stack.is(Items.SOUL_CAMPFIRE);
	}

	public static boolean hushed(ServerPlayer player) {
		if (Meals.strength(player, "hushed") >= 1) {
			return true;
		}
		return CookData.tag(player.getItemBySlot(EquipmentSlot.FEET)).getBooleanOr("hushed", false);
	}

	public static boolean vented(ServerLevel level, BlockPos pos) {
		Block vent = Content.blocks.get("vent");
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = -12; x <= 12; x += 1) {
			for (int y = -6; y <= 6; y++) {
				for (int z = -12; z <= 12; z += 1) {
					cursor.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
					if (level.getBlockState(cursor).is(vent)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static boolean gassy(ServerLevel level, BlockPos pos) {
		if (pos.getY() < 800 || pos.getY() >= 900) {
			return false;
		}
		String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("");
		if (!biome.equals("deepworld:coal_seams")) {
			return false;
		}
		long cell = BlockPos.asLong(pos.getX() >> 3, pos.getY() >> 3, pos.getZ() >> 3);
		long hash = (cell ^ level.getSeed()) * 0x9E3779B97F4A7C15L;
		hash ^= hash >>> 29;
		boolean gas = Math.floorMod(hash, 100) < 30;
		return gas && !vented(level, pos);
	}

	private static void ignite(ServerLevel level, BlockPos pos) {
		level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.5F, true, Level.ExplosionInteraction.BLOCK);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(16))) {
			dev.kaileh57.deepworld.hud.Hud.notice(player, Component.literal("firedamp! the gas caught.").withStyle(ChatFormatting.RED));
		}
	}

	public static boolean supported(ServerLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = -4; x <= 4; x++) {
			for (int y = -3; y <= 4; y++) {
				for (int z = -4; z <= 4; z++) {
					cursor.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
					if (level.getBlockState(cursor).is(supports)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static int span(ServerLevel level, BlockPos pos) {
		int best = 0;
		for (Direction axis : new Direction[] {Direction.EAST, Direction.SOUTH}) {
			int count = 1;
			for (Direction direction : new Direction[] {axis, axis.getOpposite()}) {
				for (int step = 1; step <= 8; step++) {
					if (!level.getBlockState(pos.relative(direction, step)).isAir()) {
						break;
					}
					count++;
				}
			}
			best = Math.max(best, count);
		}
		return best;
	}

	public static int maybe_cave_in(ServerLevel level, BlockPos pos, float chance) {
		int width = span(level, pos);
		if (width < 5 || supported(level, pos)) {
			return 0;
		}
		float roll = chance + 0.08F * (width - 5);
		if (level.getRandom().nextFloat() > roll) {
			return 0;
		}
		level.playSound(null, pos, SoundEvents.POINTED_DRIPSTONE_FALL, SoundSource.BLOCKS, 2.0F, 0.5F);
		level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 3, pos.getZ() + 0.5, 40, 2, 0.5, 2, 0.01);
		scheduled.add(new Scheduled(level, pos.immutable(), level.getGameTime() + 30, "cave_in"));
		return width;
	}

	public static int collapse(ServerLevel level, BlockPos pos) {
		int dropped = 0;
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int up = 1; up <= 6; up++) {
					BlockPos ceiling = pos.offset(x, up, z);
					BlockState state = level.getBlockState(ceiling);
					if (state.isAir()) {
						continue;
					}
					if (state.is(supports) || state.getBlock().defaultDestroyTime() < 0 || level.getBlockEntity(ceiling) != null) {
						break;
					}
					FallingBlockEntity falling = FallingBlockEntity.fall(level, ceiling, state);
					falling.setHurtsEntities(1.5F, 20);
					dropped++;
					break;
				}
			}
		}
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(12))) {
			dev.kaileh57.deepworld.hud.Hud.notice(player, Component.literal("cave-in! shore up wide ceilings with timber or iron").withStyle(ChatFormatting.GOLD));
		}
		return dropped;
	}

	private static void echo(ServerLevel level, ServerPlayer player, int radius) {
		for (Monster monster : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(radius))) {
			if (monster.getTarget() == null) {
				monster.getNavigation().moveTo(player, 1.0);
			}
		}
	}

	public static int tide_level(long time) {
		return tide_base + (int) Math.round(tide_amplitude * Math.sin(2 * Math.PI * time / tide_period));
	}

	public static void tick(MinecraftServer server, long tick) {
		Iterator<Scheduled> iterator = scheduled.iterator();
		List<Scheduled> due = new ArrayList<>();
		while (iterator.hasNext()) {
			Scheduled entry = iterator.next();
			if (entry.level().getGameTime() >= entry.at()) {
				due.add(entry);
				iterator.remove();
			}
		}
		for (Scheduled entry : due) {
			switch (entry.kind()) {
				case "cave_in" -> collapse(entry.level(), entry.pos());
				case "firedamp" -> ignite(entry.level(), entry.pos());
				case "burnout" -> {
					BlockState state = entry.level().getBlockState(entry.pos());
					if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
						entry.level().removeBlock(entry.pos(), false);
						entry.level().sendParticles(ParticleTypes.SMOKE, entry.pos().getX() + 0.5, entry.pos().getY() + 0.6, entry.pos().getZ() + 0.5, 6, 0.05, 0.05, 0.05, 0.01);
					}
				}
				default -> {
				}
			}
		}
		if (tick % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			BlockPos pos = player.blockPosition();
			Hud.set(player, "hazard", null);
			if (config().cold_water && pos.getY() >= 900 && pos.getY() < 1000 && player.isInWater()) {
				boolean warm = Meals.strength(player, "warming") >= 1 || wears_leather(player);
				if (!warm) {
					player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 20, player.getTicksFrozen() + 30));
					player.causeFoodExhaustion(1.0F);
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false, true));
					Hud.set(player, "hazard", Component.literal("cold water").withStyle(ChatFormatting.AQUA));
				}
			}
			if (config().fumes && pos.getY() >= 600 && pos.getY() < 700 && tick % 100 == 0) {
				String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("");
				if (biome.equals("deepworld:sulfur_springs") && !vented(level, pos) && Meals.strength(player, "bitter") < 1) {
					player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0, true, false, true));
					player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, true, false, true));
					Hud.set(player, "hazard", Component.literal("fumes").withStyle(ChatFormatting.GREEN));
				}
			}
			if (config().firedamp && pos.getY() >= 800 && pos.getY() < 900 && (player.getMainHandItem().is(Content.item("safety_lamp")) || player.getOffhandItem().is(Content.item("safety_lamp")))) {
				if (gassy(level, pos)) {
					Hud.set(player, "hazard", Component.literal("firedamp! no open flames").withStyle(ChatFormatting.RED));
					level.sendParticles(player, ParticleTypes.SOUL_FIRE_FLAME, true, false, player.getX(), player.getY() + 1.2, player.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
				} else {
					Hud.set(player, "hazard", Component.literal("air is clean").withStyle(ChatFormatting.DARK_GREEN));
				}
			}
			if (config().magma_tides && pos.getY() >= 450 && pos.getY() < 600) {
				int tide = tide_level(level.getGameTime());
				Hud.set(player, "hazard", Component.literal("tide " + (tide > tide_base ? "rising " : "low ") + tide).withStyle(ChatFormatting.GOLD));
				if (tick % 100 == 0) {
					apply_tide(level, pos, tide);
				}
			}
		}
	}

	private static boolean wears_leather(ServerPlayer player) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS)) {
				return true;
			}
		}
		return false;
	}

	public static void apply_tide(ServerLevel level, BlockPos around, int tide) {
		int radius = 24;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				for (int y = tide_base - tide_amplitude; y < tide; y++) {
					cursor.set(around.getX() + x, y, around.getZ() + z);
					if (level.getBlockState(cursor).isAir() && level.getBlockState(cursor.below()).is(Blocks.LAVA)) {
						level.setBlock(cursor, Blocks.LAVA.defaultBlockState(), 2);
						BlockPos fixed = cursor.immutable();
						if (!tide_removed.remove(fixed)) {
							tide_added.add(fixed);
						}
					}
				}
				for (int y = tide_base + tide_amplitude; y >= tide; y--) {
					cursor.set(around.getX() + x, y, around.getZ() + z);
					BlockState state = level.getBlockState(cursor);
					if (state.is(Blocks.LAVA) && state.getFluidState().isSource() && level.getBlockState(cursor.above()).isAir()) {
						level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
						BlockPos fixed = cursor.immutable();
						if (!tide_added.remove(fixed)) {
							tide_removed.add(fixed);
						}
					}
				}
			}
		}
	}
}
