package dev.kaileh57.deepworld.alchemy;

import dev.kaileh57.deepworld.guild.Guild;
import dev.kaileh57.deepworld.smithing.Smithing;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class Blasting {
	public static final int fuse = 60;
	public static final List<BiConsumer<ServerLevel, BlockPos>> after_blast = new ArrayList<>();
	private record Pending(ServerLevel level, BlockPos target, Direction face, String kind, ServerPlayer owner, PrimedTnt visual, long fire_at) {
	}

	private static final List<Pending> pending = new ArrayList<>();

	private Blasting() {
	}

	public static void arm(ServerLevel level, BlockPos target, Direction face, String kind, ServerPlayer owner) {
		BlockPos at = target.relative(face);
		PrimedTnt visual = new PrimedTnt(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, owner);
		visual.setFuse(fuse + 40);
		visual.setNoGravity(true);
		visual.setDeltaMovement(0, 0, 0);
		level.addFreshEntity(visual);
		level.playSound(null, at, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.2F);
		pending.add(new Pending(level, target.immutable(), face, kind, owner, visual, level.getGameTime() + fuse));
	}

	public static void tick(MinecraftServer server) {
		Iterator<Pending> iterator = pending.iterator();
		while (iterator.hasNext()) {
			Pending blast = iterator.next();
			if (blast.level().getGameTime() < blast.fire_at()) {
				continue;
			}
			iterator.remove();
			blast.visual().discard();
			detonate(blast.level(), blast.target(), blast.face(), blast.kind(), blast.owner());
		}
	}

	private static boolean breakable(BlockState state) {
		return !state.isAir() && !state.is(Blocks.BEDROCK) && state.getBlock().defaultDestroyTime() >= 0 && state.getBlock().getExplosionResistance() < 1200;
	}

	private static void destroy(ServerLevel level, BlockPos pos, boolean drop) {
		BlockState state = level.getBlockState(pos);
		if (drop) {
			Block.dropResources(state, level, pos, level.getBlockEntity(pos), null, net.minecraft.world.item.ItemStack.EMPTY);
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
	}

	public static void detonate(ServerLevel level, BlockPos target, Direction face, String kind, ServerPlayer owner) {
		boolean focused = Guild.focused(owner, "alchemist");
		var random = level.getRandom();
		switch (kind) {
			case "black_powder" -> {
				for (BlockPos pos : BlockPos.betweenClosed(target.offset(-1, -1, -1), target.offset(1, 1, 1))) {
					BlockState state = level.getBlockState(pos);
					if (breakable(state) && state.getBlock().getExplosionResistance() < 50) {
						destroy(level, pos, random.nextFloat() < (focused ? 0.75F : 0.5F));
					}
				}
			}
			case "shaped" -> {
				Direction into = face.getOpposite();
				for (int step = 0; step < 12; step++) {
					BlockPos pos = target.relative(into, step);
					BlockState state = level.getBlockState(pos);
					if (breakable(state) && state.getBlock().getExplosionResistance() < 50) {
						destroy(level, pos, true);
					}
				}
			}
			case "mining" -> {
				for (BlockPos pos : BlockPos.betweenClosed(target.offset(-1, -1, -1), target.offset(1, 1, 1))) {
					BlockState state = level.getBlockState(pos);
					if (breakable(state) && state.getBlock().getExplosionResistance() < 50) {
						destroy(level, pos, state.is(Smithing.dowsable));
					}
				}
			}
			case "demolition" -> {
				for (BlockPos pos : BlockPos.betweenClosed(target.offset(-2, -1, -2), target.offset(2, 1, 2))) {
					if (breakable(level.getBlockState(pos))) {
						destroy(level, pos, false);
					}
				}
			}
			default -> {
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1, 0, 0, 0, 0);
		level.playSound(null, target, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.9F);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(target).inflate(3.5))) {
			entity.hurtServer(level, level.damageSources().explosion(null, owner), 8.0F);
		}
		Guild.add(owner, "charges_used", 1);
		for (BiConsumer<ServerLevel, BlockPos> hook : after_blast) {
			hook.accept(level, target);
		}
	}
}
