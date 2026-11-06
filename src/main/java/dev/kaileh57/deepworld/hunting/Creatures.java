package dev.kaileh57.deepworld.hunting;

import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.guild.Guild;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

public final class Creatures {
	public static final String checked = "dw_checked";
	public static final String matriarch = "dw_matriarch";
	public static final String armored = "dw_armored";
	public static final String crystal = "dw_crystal";
	public static final String sea_ghast = "dw_sea_ghast";

	private Creatures() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				setup(mob, level);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Creeper creeper && creeper.entityTags().contains(crystal) && creeper.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
				shatter(level, creeper);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				drops(level, entity, source.getEntity() instanceof ServerPlayer player ? player : null);
			}
		});
		Animals.register();
		HuntingTests.register();
	}

	private static void scale(Mob mob, double scale) {
		if (mob.getAttribute(Attributes.SCALE) != null) {
			mob.getAttribute(Attributes.SCALE).setBaseValue(scale);
		}
	}

	public static void setup(Mob mob, ServerLevel level) {
		if (mob.entityTags().contains(checked)) {
			if (mob.entityTags().contains(crystal)) {
				adorn(mob);
			}
			return;
		}
		mob.addTag(checked);
		RandomSource random = mob.getRandom();
		int y = mob.getBlockY();
		if (mob.getType() == EntityTypes.CAVE_SPIDER && y >= 900 && y < 1000 && random.nextFloat() < 0.15F) {
			make_matriarch(mob);
		} else if (mob.getType() == EntityTypes.HUSK && y >= 800 && y < 900 && random.nextFloat() < 0.6F) {
			make_armored(mob);
		} else if (mob.getType() == EntityTypes.CREEPER && y >= 700 && y < 800 && random.nextFloat() < 0.35F) {
			make_crystal(mob);
		} else if (mob.getType() == EntityTypes.GHAST && y >= 450 && y < 600) {
			mob.addTag(sea_ghast);
		} else if (mob.getType() == EntityTypes.GOAT && y < 1100) {
			Animals.make_cave_goat(mob);
		}
	}

	public static void make_matriarch(Mob mob) {
		mob.addTag(checked);
		mob.addTag(matriarch);
		scale(mob, 1.8);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(32);
		mob.setHealth(32);
		if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
			mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5);
		}
	}

	public static void make_armored(Mob mob) {
		mob.addTag(checked);
		mob.addTag(armored);
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
		mob.setDropChance(EquipmentSlot.CHEST, 0.0F);
	}

	public static void make_crystal(Mob mob) {
		mob.addTag(checked);
		mob.addTag(crystal);
		scale(mob, 1.15);
		adorn(mob);
	}

	public static void adorn(Mob mob) {
		ElementHolder holder = new ElementHolder();
		for (int side = -1; side <= 1; side += 2) {
			ItemDisplayElement element = new ItemDisplayElement(new ItemStack(Items.AMETHYST_CLUSTER));
			element.setItemDisplayContext(ItemDisplayContext.FIXED);
			element.setScale(new Vector3f(0.5F));
			element.setTranslation(new Vector3f(0.22F * side, 1.95F, 0.0F));
			holder.addElement(element);
		}
		EntityAttachment.ofTicking(holder, mob);
	}

	private static void shatter(ServerLevel level, Creeper creeper) {
		RandomSource random = creeper.getRandom();
		creeper.spawnAtLocation(level, new ItemStack(Items.AMETHYST_SHARD, 4 + random.nextInt(5)));
		if (random.nextFloat() < 0.3F) {
			creeper.spawnAtLocation(level, new ItemStack(Content.item("crystal_heart")));
		}
		level.sendParticles(ParticleTypes.END_ROD, creeper.getX(), creeper.getY() + 1, creeper.getZ(), 30, 1, 1, 1, 0.05);
		level.playSound(null, creeper.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2.0F, 0.8F);
	}

	private static void trophy(ServerLevel level, LivingEntity entity, ServerPlayer player, String id, float chance) {
		if (player != null && entity.getRandom().nextFloat() < chance) {
			entity.spawnAtLocation(level, new ItemStack(Content.item(id)));
			Guild.add(player, "trophies", 1);
		}
	}

	public static void drops(ServerLevel level, LivingEntity entity, ServerPlayer player) {
		RandomSource random = entity.getRandom();
		var tags = entity.entityTags();
		if (tags.contains(matriarch)) {
			entity.spawnAtLocation(level, new ItemStack(Items.STRING, 4 + random.nextInt(5)));
			trophy(level, entity, player, "matriarch_fang", 1.0F);
		} else if (tags.contains(armored)) {
			entity.spawnAtLocation(level, new ItemStack(Items.IRON_NUGGET, 4 + random.nextInt(6)));
			trophy(level, entity, player, "rusted_helm", 0.15F);
		} else if (tags.contains(crystal)) {
			entity.spawnAtLocation(level, new ItemStack(Items.AMETHYST_SHARD, 2 + random.nextInt(3)));
			trophy(level, entity, player, "crystal_heart", 0.2F);
		} else if (tags.contains(sea_ghast)) {
			entity.spawnAtLocation(level, new ItemStack(Items.GHAST_TEAR));
			trophy(level, entity, player, "ghast_trophy", 0.25F);
		} else if (entity.getType() == EntityTypes.WARDEN) {
			entity.spawnAtLocation(level, new ItemStack(Items.ECHO_SHARD, 2));
			trophy(level, entity, player, "warden_trophy", 1.0F);
		} else if (entity.getType() == EntityTypes.HOGLIN && entity.getY() < 300) {
			trophy(level, entity, player, "hoglin_tusk", 0.2F);
		}
	}
}
