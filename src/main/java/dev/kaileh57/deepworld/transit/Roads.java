package dev.kaileh57.deepworld.transit;

import dev.kaileh57.deepworld.DeepWorld;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class Roads {
	public static final TagKey<Block> paved = TagKey.create(Registries.BLOCK, DeepWorld.id("paved"));
	public static final int wear_steps = 24;
	private static final Map<BlockPos, Integer> wear = new LinkedHashMap<>(4096, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<BlockPos, Integer> eldest) {
			return size() > 20000;
		}
	};
	private static final Map<java.util.UUID, BlockPos> last = new java.util.HashMap<>();

	private Roads() {
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 10 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			Entity mount = player.getVehicle();
			Entity mover = mount != null ? mount : player;
			BlockPos under = mover.blockPosition().below();
			if (level.getBlockState(under).is(paved)) {
				if (mover instanceof AbstractHorse horse) {
					horse.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1, true, false, false));
				} else if (mount == null) {
					player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 0, true, false, false));
				}
			}
			if (mount != null || player.isCreative() || player.isSpectator()) {
				continue;
			}
			BlockPos previous = last.put(player.getUUID(), under);
			if (under.equals(previous) || !level.getBlockState(under).is(Blocks.GRASS_BLOCK)) {
				continue;
			}
			int count = wear.merge(under, 1, Integer::sum);
			if (count >= wear_steps) {
				wear.remove(under);
				level.setBlockAndUpdate(under, Blocks.DIRT_PATH.defaultBlockState());
				if (level.getBlockState(under.above()).canBeReplaced()) {
					level.setBlockAndUpdate(under.above(), Blocks.AIR.defaultBlockState());
				}
			}
		}
	}
}
