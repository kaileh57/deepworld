package dev.kaileh57.deepworld.smithing;

import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.guild.Guild;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

public final class Smithing {
	public static final TagKey<Block> dowsable = TagKey.create(Registries.BLOCK, DeepWorld.id("dowsable"));

	private Smithing() {
	}

	public static String quench_of(ItemStack modifier) {
		if (modifier.is(Items.WATER_BUCKET)) {
			return "standard";
		}
		if (modifier.is(Content.item("rendered_fat"))) {
			return "tough";
		}
		if (modifier.is(Content.item("brine"))) {
			return "keen";
		}
		if (modifier.is(Items.LAVA_BUCKET)) {
			return "tempered";
		}
		if (CookData.tag(modifier).getStringOr("dish", "").equals("ghast_tear_cordial")) {
			return "pristine";
		}
		return "";
	}

	public static String socket_of(ItemStack modifier) {
		if (modifier.is(Content.item("resonant_shard"))) {
			return "resonant";
		}
		if (modifier.is(Items.LAPIS_LAZULI)) {
			return "lapis";
		}
		if (modifier.is(Items.DIAMOND)) {
			return "diamond";
		}
		if (modifier.is(Items.QUARTZ)) {
			return "quartz";
		}
		if (modifier.is(Items.ECHO_SHARD)) {
			return "echo";
		}
		return "";
	}

	public static String apply(ServerPlayer player, ItemStack tool, ItemStack modifier) {
		if (tool.isEmpty() || !tool.isDamageableItem()) {
			return "put a tool on the bench";
		}
		CompoundTag tag = CookData.tag(tool);
		String quench = quench_of(modifier);
		String socket = socket_of(modifier);
		if (quench.isEmpty() && socket.isEmpty()) {
			return "that can't quench or socket";
		}
		if (!quench.isEmpty()) {
			if (!tag.getStringOr("quench", "").isEmpty()) {
				return "already quenched";
			}
			tag.putString("quench", quench);
			switch (quench) {
				case "tough" -> tool.set(DataComponents.MAX_DAMAGE, (int) (tool.getMaxDamage() * 1.5));
				case "keen" -> {
					Tool component = tool.get(DataComponents.TOOL);
					if (component != null) {
						List<Tool.Rule> rules = new ArrayList<>();
						for (Tool.Rule rule : component.rules()) {
							rules.add(new Tool.Rule(rule.blocks(), rule.speed().map(speed -> speed * 1.25F), rule.correctForDrops()));
						}
						tool.set(DataComponents.TOOL, new Tool(rules, component.defaultMiningSpeed() * 1.25F, component.damagePerBlock(), component.canDestroyBlocksInCreative()));
					}
				}
				case "tempered" -> tool.set(DataComponents.DAMAGE_RESISTANT, new DamageResistant(player.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.IS_FIRE)));
				default -> {
				}
			}
		} else {
			if (!tag.getStringOr("socket", "").isEmpty()) {
				return "socket already filled";
			}
			tag.putString("socket", socket);
			switch (socket) {
				case "diamond" -> tool.set(DataComponents.MAX_DAMAGE, (int) (tool.getMaxDamage() * 1.25));
				case "quartz" -> {
					ItemAttributeModifiers modifiers = tool.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
					tool.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers.withModifierAdded(Attributes.MINING_EFFICIENCY, new AttributeModifier(DeepWorld.id("quartz_socket"), 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
				}
				default -> {
				}
			}
		}
		CookData.set_tag(tool, tag);
		List<Component> lines = new ArrayList<>();
		ItemLore lore = tool.get(DataComponents.LORE);
		if (lore != null) {
			lines.addAll(lore.lines());
		}
		lines.add(Component.literal(quench.isEmpty() ? "socket: " + socket : "quench: " + quench).withStyle(ChatFormatting.AQUA));
		tool.set(DataComponents.LORE, new ItemLore(lines));
		if (modifier.is(Items.WATER_BUCKET) || modifier.is(Items.LAVA_BUCKET)) {
			modifier.shrink(1);
			ItemStack bucket = new ItemStack(Items.BUCKET);
			if (!player.getInventory().add(bucket)) {
				player.drop(bucket, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		} else {
			modifier.shrink(1);
		}
		Guild.add(player, "quenched", 1);
		player.level().playSound(null, player.blockPosition(), quench.isEmpty() ? SoundEvents.ANVIL_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.2F);
		return quench.isEmpty() ? "socketed " + socket : "quenched " + quench;
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (!(level instanceof ServerLevel server_level) || !state.is(dowsable)) {
				return;
			}
			if (CookData.tag(player.getMainHandItem()).getStringOr("socket", "").equals("lapis")) {
				ExperienceOrb.award(server_level, net.minecraft.world.phys.Vec3.atCenterOf(pos), 3);
			}
		});
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 40 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ItemStack held = player.getMainHandItem();
				if (!CookData.tag(held).getStringOr("socket", "").equals("resonant")) {
					continue;
				}
				int range = Guild.focused(player, "archivist") ? 16 : 8;
				double nearest = nearest_ore(player.level(), player.blockPosition(), range);
				if (nearest >= 0) {
					float pitch = (float) (2.0 - 1.5 * nearest / range);
					player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, pitch);
				}
			}
		}
		if (tick % 600 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				var inventory = player.getInventory();
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					ItemStack stack = inventory.getItem(slot);
					if (stack.isDamaged() && CookData.tag(stack).getStringOr("quench", "").equals("pristine")) {
						stack.setDamageValue(stack.getDamageValue() - 1);
					}
				}
			}
		}
	}

	public static double nearest_ore(ServerLevel level, BlockPos center, int range) {
		double best = -1;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = -range; x <= range; x++) {
			for (int y = -range; y <= range; y++) {
				for (int z = -range; z <= range; z++) {
					cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					if (level.getBlockState(cursor).is(dowsable)) {
						double distance = Math.sqrt(x * x + y * y + z * z);
						if (best < 0 || distance < best) {
							best = distance;
						}
					}
				}
			}
		}
		return best;
	}
}
