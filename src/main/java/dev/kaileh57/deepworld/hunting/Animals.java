package dev.kaileh57.deepworld.hunting;

import com.mojang.serialization.Codec;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.hud.Hud;
import dev.kaileh57.deepworld.mixin.AbstractHorseAccessor;
import dev.kaileh57.deepworld.mixin.LlamaAccessor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Animals {
	public static final AttachmentType<Integer> training = AttachmentRegistry.create(DeepWorld.id("training"), builder -> builder.persistent(Codec.INT));
	public static final String truffle_pig = "dw_truffle_pig";
	public static final String pack_mule = "dw_pack_mule";
	public static final String cave_goat = "dw_cave_goat";
	public static final int treats_needed = 6;
	private record Cast(BlockPos spot, long done) {
	}
	private static final Map<UUID, Cast> casts = new HashMap<>();

	private Animals() {
	}

	public static void make_cave_goat(Mob goat) {
		goat.addTag(cave_goat);
		if (goat.getAttribute(Attributes.STEP_HEIGHT) != null) {
			goat.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.1);
		}
	}

	public static String train(Mob pig, ItemStack treat) {
		if (pig.entityTags().contains(truffle_pig)) {
			return "this pig already hunts truffles";
		}
		int progress = pig.getAttachedOrElse(training, 0) + 1;
		treat.shrink(1);
		if (progress >= treats_needed) {
			pig.addTag(truffle_pig);
			pig.removeAttached(training);
			return "the pig has learned to sniff out truffles";
		}
		pig.setAttached(training, progress);
		return "the pig sniffs the mushroom (" + progress + "/" + treats_needed + ")";
	}

	public static String harness(Llama llama, ItemStack stack) {
		if (!llama.isTamed()) {
			return "tame it first";
		}
		if (llama.entityTags().contains(pack_mule)) {
			return "it already wears a harness";
		}
		((LlamaAccessor) llama).deepworld_set_strength(5);
		((AbstractHorseAccessor) llama).deepworld_create_inventory();
		llama.addTag(pack_mule);
		stack.shrink(1);
		return "pack mule ready: 15 slots once it carries a chest";
	}

	public static String honey_kind(ServerLevel level, BlockPos hive) {
		int cherry = 0;
		int sun = 0;
		int glow = 0;
		for (BlockPos pos : BlockPos.betweenClosed(hive.offset(-6, -4, -6), hive.offset(6, 4, 6))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.CHERRY_LEAVES) || state.is(Blocks.PINK_PETALS)) {
				cherry++;
			} else if (state.is(Blocks.SUNFLOWER)) {
				sun++;
			} else if (state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) {
				glow++;
			}
		}
		int best = Math.max(cherry, Math.max(sun, glow));
		if (best < 3) {
			return "";
		}
		return best == glow ? "glow" : best == cherry ? "cherry blossom" : "sunflower";
	}

	public static ItemStack honey(String kind) {
		ItemStack stack = new ItemStack(Items.HONEY_BOTTLE);
		if (kind.isEmpty()) {
			return stack;
		}
		CompoundTag tag = CookData.tag(stack);
		CompoundTag props = new CompoundTag();
		switch (kind) {
			case "cherry blossom" -> props.putInt("fresh", 2);
			case "sunflower" -> props.putInt("sweet", 2);
			default -> props.putInt("glowing", 2);
		}
		tag.put("props", props);
		tag.putString("honey", kind);
		CookData.set_tag(stack, tag);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(kind + " honey").withStyle(style -> style.withItalic(false)));
		return stack;
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer server_player) || !(level instanceof ServerLevel server_level)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (entity.getType() == EntityTypes.PIG && stack.is(Items.BROWN_MUSHROOM) && entity instanceof Mob pig) {
				Hud.notice(server_player, Component.literal(train(pig, stack)));
				server_level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pig.getX(), pig.getY() + 0.8, pig.getZ(), 5, 0.3, 0.3, 0.3, 0);
				return InteractionResult.SUCCESS_SERVER;
			}
			if (entity instanceof Llama llama && stack.is(Content.item("pack_harness"))) {
				Hud.notice(server_player, Component.literal(harness(llama, stack)));
				return InteractionResult.SUCCESS_SERVER;
			}
			if (entity.getType() == EntityTypes.GOAT && entity.entityTags().contains(cave_goat) && stack.is(Items.BUCKET)) {
				stack.shrink(1);
				ItemStack milk = new ItemStack(Content.item("cave_milk"));
				if (!player.getInventory().add(milk)) {
					player.drop(milk, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				level.playSound(null, entity.blockPosition(), SoundEvents.GOAT_MILK, SoundSource.NEUTRAL, 1.0F, 1.0F);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer) || !(level instanceof ServerLevel server_level)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			ItemStack stack = player.getItemInHand(hand);
			if (!(state.getBlock() instanceof BeehiveBlock hive) || !stack.is(Items.GLASS_BOTTLE) || state.getValue(BeehiveBlock.HONEY_LEVEL) < 5) {
				return InteractionResult.PASS;
			}
			String kind = honey_kind(server_level, pos);
			if (kind.isEmpty()) {
				return InteractionResult.PASS;
			}
			stack.shrink(1);
			ItemStack honey = honey(kind);
			if (!player.getInventory().add(honey)) {
				player.drop(honey, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
			hive.releaseBeesAndResetHoneyLevel(level, state, pos, player, net.minecraft.world.level.block.entity.BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
			return InteractionResult.SUCCESS_SERVER;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer server_player) || !(level instanceof ServerLevel server_level)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Content.item("ember_rod"))) {
				return InteractionResult.PASS;
			}
			Vec3 eye = player.getEyePosition();
			BlockHitResult target = level.clip(new ClipContext(eye, eye.add(player.getViewVector(1.0F).scale(12)), ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
			if (target.getType() != HitResult.Type.BLOCK || !level.getFluidState(target.getBlockPos()).is(FluidTags.LAVA)) {
				Hud.notice(server_player, Component.literal("cast it into lava"));
				return InteractionResult.SUCCESS_SERVER;
			}
			if (!player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
				Hud.notice(server_player, Component.literal("the heat off the lava is too much. you need fire resistance"));
				return InteractionResult.SUCCESS_SERVER;
			}
			casts.put(player.getUUID(), new Cast(target.getBlockPos(), level.getGameTime() + 100 + player.getRandom().nextInt(300)));
			level.playSound(null, target.getBlockPos(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 0.6F);
			Hud.notice(server_player, Component.literal("the line glows in the lava. hold the rod and wait"));
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	public static ItemStack lava_catch(int y) {
		if (y < 0) {
			return new ItemStack(Content.item("magma_eel"));
		}
		if (y < 600) {
			return new ItemStack(Content.item("lava_fish"));
		}
		return ItemStack.EMPTY;
	}

	public static void tick(MinecraftServer server, long tick) {
		Iterator<Map.Entry<UUID, Cast>> entries = casts.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<UUID, Cast> entry = entries.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Cast cast = entry.getValue();
			if (player == null || !player.getMainHandItem().is(Content.item("ember_rod")) || player.distanceToSqr(Vec3.atCenterOf(cast.spot())) > 400) {
				entries.remove();
				continue;
			}
			ServerLevel level = (ServerLevel) player.level();
			if (tick % 10 == 0) {
				level.sendParticles(ParticleTypes.LAVA, cast.spot().getX() + 0.5, cast.spot().getY() + 1, cast.spot().getZ() + 0.5, 1, 0.1, 0, 0.1, 0);
			}
			if (level.getGameTime() >= cast.done()) {
				entries.remove();
				ItemStack fish = lava_catch(cast.spot().getY());
				if (fish.isEmpty()) {
					Hud.notice(player, Component.literal("nothing lives in lava this high up"));
					continue;
				}
				if (!player.getInventory().add(fish)) {
					player.drop(fish, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				player.getMainHandItem().hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
				level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.8F);
				Hud.notice(player, Component.literal("caught a " + fish.getHoverName().getString().toLowerCase()).withStyle(ChatFormatting.GOLD));
			}
		}
		if (tick % 400 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (Entity entity : level.getAllEntities()) {
			if (entity.getType() == EntityTypes.PIG && entity.entityTags().contains(truffle_pig) && entity.getY() < 1100 && level.getBlockState(entity.blockPosition().below()).is(Blocks.MOSS_BLOCK) && level.getRandom().nextFloat() < 0.25F && level.getNearestPlayer(entity, 16) != null) {
				entity.spawnAtLocation(level, new ItemStack(Content.item("moss_truffle")));
				level.sendParticles(ParticleTypes.COMPOSTER, entity.getX(), entity.getY() + 0.3, entity.getZ(), 8, 0.3, 0.1, 0.3, 0);
				level.playSound(null, entity.blockPosition(), SoundEvents.SNIFFER_DIGGING, SoundSource.NEUTRAL, 1.0F, 1.3F);
			}
		}
	}
}
