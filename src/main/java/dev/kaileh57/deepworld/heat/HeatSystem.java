package dev.kaileh57.deepworld.heat;

import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.hud.Hud;
import dev.kaileh57.deepworld.world.Layers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;

public final class HeatSystem {
	public static final ResourceKey<DamageType> heat_damage = ResourceKey.create(Registries.DAMAGE_TYPE, DeepWorld.id("heat"));
	public static final int max_heat = 5;
	private static final List<ToIntFunction<ServerPlayer>> protection_sources = new ArrayList<>();
	private static final EquipmentSlot[] armor_slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private HeatSystem() {
	}

	public static void add_protection(ToIntFunction<ServerPlayer> source) {
		protection_sources.add(source);
	}

	public static int ambient_heat(ServerPlayer player) {
		if (player.level().dimension() != Level.OVERWORLD) {
			return 0;
		}
		Layers.Layer layer = Layers.at(player.getBlockY());
		if (layer == null) {
			return 0;
		}
		return Math.max(0, Math.min(max_heat, 5 - layer.number()));
	}

	public static int protection(ServerPlayer player) {
		double armor = 0;
		for (EquipmentSlot slot : armor_slots) {
			armor += Content.armor_heat.getOrDefault(player.getItemBySlot(slot).getItem(), 0.0);
		}
		int total = (int) Math.floor(armor + 0.001);
		if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
			total += 2;
		}
		for (ToIntFunction<ServerPlayer> source : protection_sources) {
			total += source.applyAsInt(player);
		}
		return total;
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				Hud.set(player, "heat", null);
				continue;
			}
			int heat = ambient_heat(player);
			if (heat == 0) {
				Hud.set(player, "heat", null);
				continue;
			}
			int guard = protection(player);
			int stage = Math.max(0, heat - guard);
			apply(player, stage, tick);
			Hud.set(player, "heat", meter(heat, guard, stage));
		}
	}

	private static void apply(ServerPlayer player, int stage, long tick) {
		if (stage >= 1) {
			player.causeFoodExhaustion(0.5F * stage);
		}
		if (stage >= 2) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 0, true, false, true));
		}
		if (stage >= 3 && (stage >= 4 || tick % 40 == 0)) {
			ServerLevel level = player.level();
			player.hurtServer(level, level.damageSources().source(heat_damage), stage - 2);
		}
	}

	private static Component meter(int heat, int guard, int stage) {
		MutableComponent bar = Component.literal("heat ").withStyle(ChatFormatting.GRAY);
		for (int index = 1; index <= max_heat; index++) {
			ChatFormatting color;
			if (index > heat) {
				color = ChatFormatting.DARK_GRAY;
			} else if (index <= guard) {
				color = ChatFormatting.AQUA;
			} else {
				color = stage >= 3 ? ChatFormatting.RED : ChatFormatting.GOLD;
			}
			bar.append(Component.literal("■").withStyle(color));
		}
		return bar;
	}
}
