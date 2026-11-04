package dev.kaileh57.deepworld.farming;

import com.mojang.serialization.Codec;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.guild.Guild;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.kaileh57.deepworld.cooking.CookData;

public final class Husbandry {
	public static final List<String> traits = List.of("plump", "rich_milk", "fast", "hardy", "calm");
	public static final AttachmentType<Map<String, Integer>> attachment = AttachmentRegistry.create(DeepWorld.id("traits"), builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).initializer(HashMap::new));

	private Husbandry() {
	}

	public static Map<String, Integer> of(Entity entity) {
		return entity.getAttachedOrCreate(attachment);
	}

	public static void on_breed(ServerLevel level, Animal parent, Animal partner, AgeableMob child) {
		if (child == null) {
			return;
		}
		RandomSource random = level.getRandom();
		Map<String, Integer> a = of(parent);
		Map<String, Integer> b = of(partner);
		Map<String, Integer> result = new HashMap<>();
		for (String trait : traits) {
			int value = (int) Math.round((a.getOrDefault(trait, 0) + b.getOrDefault(trait, 0)) / 2.0);
			if (random.nextFloat() < 0.15F) {
				value += random.nextBoolean() ? 1 : -1;
			}
			value = Math.max(0, Math.min(3, value));
			if (value > 0) {
				result.put(trait, value);
			}
		}
		if (random.nextFloat() < 0.1F) {
			result.merge(traits.get(random.nextInt(traits.size())), 1, (x, y) -> Math.min(3, x + y));
		}
		child.setAttached(attachment, result);
		int fast = result.getOrDefault("fast", 0);
		AttributeInstance speed = child.getAttribute(Attributes.MOVEMENT_SPEED);
		if (fast > 0 && speed != null) {
			speed.setBaseValue(speed.getBaseValue() * (1.0 + 0.06 * fast));
		}
		int hardy = result.getOrDefault("hardy", 0);
		AttributeInstance health = child.getAttribute(Attributes.MAX_HEALTH);
		if (hardy > 0 && health != null) {
			health.setBaseValue(health.getBaseValue() + 4 * hardy);
		}
		ServerPlayer cause = parent.getLoveCause();
		Guild.add(cause, "animals_bred", 1);
	}

	private static Item meat(Entity entity) {
		var type = entity.getType();
		if (type == EntityTypes.COW || type == EntityTypes.MOOSHROOM) {
			return Items.BEEF;
		}
		if (type == EntityTypes.PIG) {
			return Items.PORKCHOP;
		}
		if (type == EntityTypes.SHEEP) {
			return Items.MUTTON;
		}
		if (type == EntityTypes.CHICKEN) {
			return Items.CHICKEN;
		}
		if (type == EntityTypes.RABBIT) {
			return Items.RABBIT;
		}
		if (type == EntityTypes.HOGLIN) {
			return Content.item("hoglin_chop");
		}
		return null;
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			int plump = of(entity).getOrDefault("plump", 0);
			Item meat = meat(entity);
			if (plump > 0 && meat != null) {
				entity.spawnAtLocation(level, new ItemStack(meat, plump));
			}
			if (source.getEntity() instanceof ServerPlayer player && Guild.focused(player, "hunter") && entity.getY() < 1000 && meat != null) {
				entity.spawnAtLocation(level, new ItemStack(meat, 1));
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer server_player) || !(entity instanceof Animal animal)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(Content.item("guild_journal"))) {
				Map<String, Integer> found = of(animal);
				server_player.sendSystemMessage(Component.literal(found.isEmpty() ? "no notable traits" : "traits: " + found.entrySet().stream().map(e -> e.getKey().replace('_', ' ') + " " + e.getValue()).reduce((x, y) -> x + ", " + y).orElse("")));
				return InteractionResult.SUCCESS_SERVER;
			}
			int rich = of(animal).getOrDefault("rich_milk", 0);
			if (rich > 0 && stack.is(Items.BUCKET) && (animal.getType() == EntityTypes.COW || animal.getType() == EntityTypes.GOAT) && !animal.isBaby()) {
				stack.consume(1, player);
				ItemStack milk = new ItemStack(Items.MILK_BUCKET);
				var tag = CookData.tag(milk);
				tag.putInt("rich_milk", rich);
				CookData.set_tag(milk, tag);
				milk.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.literal("rich milk"));
				if (!player.getInventory().add(milk)) {
					player.drop(milk, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
	}
}
