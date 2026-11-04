package dev.kaileh57.deepworld.farming;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.PlantBlock;
import dev.kaileh57.deepworld.guild.Guild;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class Farming {
	public record Plant(String id, Item seed, List<String> biomes, int min_y, int max_y, boolean deep) {
	}

	public static final Map<String, Plant> plants = new HashMap<>();
	public static final Map<Item, Plant> by_seed = new HashMap<>();

	private Farming() {
	}

	public static void register() {
		for (JsonElement element : Content.data().getAsJsonArray("plants")) {
			JsonObject p = element.getAsJsonObject();
			List<String> biomes = new ArrayList<>();
			if (p.has("biomes")) {
				p.getAsJsonArray("biomes").forEach(b -> biomes.add(b.getAsString()));
			}
			boolean deep = p.has("deep");
			int min_y = deep ? p.getAsJsonArray("y").get(0).getAsInt() - 20 : 1100;
			int max_y = deep ? p.getAsJsonArray("y").get(1).getAsInt() + 20 : 1600;
			Item seed = BuiltInRegistries.ITEM.getValue(Identifier.parse(p.get("drop").getAsString()));
			Plant plant = new Plant(p.get("id").getAsString(), seed, biomes, min_y, max_y, deep);
			plants.put(plant.id(), plant);
			by_seed.put(seed, plant);
		}
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer server_player) || !(level instanceof ServerLevel server_level)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof PlantBlock plant_block) {
				int growth = fertilizer_growth(stack, plant_block);
				if (growth > 0 && state.getValue(PlantBlock.age) < 3) {
					server_level.setBlock(pos, state.setValue(PlantBlock.age, Math.min(3, state.getValue(PlantBlock.age) + growth)), 2);
					server_level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
					stack.consume(1, player);
					Guild.add(server_player, "fertilized", 1);
					return InteractionResult.SUCCESS_SERVER;
				}
				if (state.getValue(PlantBlock.age) >= 3) {
					harvest(server_player, server_level, pos, state);
					return InteractionResult.SUCCESS_SERVER;
				}
				return InteractionResult.PASS;
			}
			Plant plant = by_seed.get(stack.getItem());
			if (plant != null && hit.getDirection() == Direction.UP && level.getBlockState(pos.above()).isAir()) {
				Block block = Content.blocks.get(plant.id());
				BlockState planted = block.defaultBlockState().setValue(PlantBlock.age, 0);
				if (planted.canSurvive(level, pos.above())) {
					level.setBlockAndUpdate(pos.above(), planted);
					level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
					stack.consume(1, player);
					if (!can_grow((PlantBlock) block, server_level, pos.above())) {
						server_player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(plant.deep() ? "this won't grow at this depth" : "this won't grow in this biome"));
					}
					return InteractionResult.SUCCESS_SERVER;
				}
			}
			return InteractionResult.PASS;
		});
	}

	private static int fertilizer_growth(ItemStack stack, PlantBlock block) {
		Plant plant = plants.get(block.id());
		if (stack.is(Content.item("compost"))) {
			return 1;
		}
		if (stack.is(Content.item("sulfur_fertilizer"))) {
			return plant != null && plant.deep() ? 2 : 1;
		}
		if (stack.is(Content.item("growth_tonic"))) {
			return 3;
		}
		return 0;
	}

	public static void harvest(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		Plant plant = plants.get(((PlantBlock) state.getBlock()).id());
		if (plant == null) {
			return;
		}
		int count = 1 + level.getRandom().nextInt(2);
		if (Guild.focused(player, "grower")) {
			count *= 2;
		}
		Block.popResource(level, pos, new ItemStack(plant.seed(), count));
		level.setBlock(pos, state.setValue(PlantBlock.age, 1), 2);
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
		Guild.add(player, "crops_harvested", 1);
		if (plant.deep()) {
			Guild.add(player, "cave_crops", 1);
		}
	}

	public static boolean can_grow(PlantBlock block, ServerLevel level, BlockPos pos) {
		Plant plant = plants.get(block.id());
		if (plant == null) {
			return true;
		}
		if (pos.getY() < plant.min_y() || pos.getY() > plant.max_y()) {
			return false;
		}
		if (plant.deep()) {
			return true;
		}
		String biome = level.getBiome(pos).unwrapKey().map(key -> key.identifier().toString()).orElse("");
		return plant.biomes().contains(biome) && level.getRawBrightness(pos, 0) >= 8;
	}
}
