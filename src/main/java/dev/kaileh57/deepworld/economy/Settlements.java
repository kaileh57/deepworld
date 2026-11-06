package dev.kaileh57.deepworld.economy;

import com.mojang.serialization.Codec;
import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;

public class Settlements extends SavedData {
	public Map<Long, Integer> minutes = new HashMap<>();
	public static final Codec<Settlements> codec = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(map -> {
		Settlements s = new Settlements();
		map.forEach((key, value) -> s.minutes.put(Long.parseLong(key), value));
		return s;
	}, s -> {
		Map<String, Integer> out = new HashMap<>();
		s.minutes.forEach((key, value) -> out.put(String.valueOf(key), value));
		return out;
	});
	public static final SavedDataType<Settlements> type = new SavedDataType<>(DeepWorld.id("settlements"), Settlements::new, codec, null);
	public static final int settle_minutes = 90;

	public static Settlements get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(type);
	}

	public static long key(BlockPos pos) {
		return BlockPos.asLong(pos.getX() >> 5, pos.getY() >> 5, pos.getZ() >> 5);
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 1200 == 0) {
			Settlements s = get(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.level() == server.overworld()) {
					s.minutes.merge(key(player.blockPosition()), 1, Integer::sum);
				}
			}
			s.setDirty();
		}
		if (tick % 12000 == 6000) {
			grow(server);
			Commissions.get(server).tick_npc(server, server.overworld().getRandom());
		}
	}

	public static void grow(MinecraftServer server) {
		Settlements s = get(server);
		ServerLevel level = server.overworld();
		for (Map.Entry<Long, Integer> entry : s.minutes.entrySet()) {
			if (entry.getValue() < settle_minutes) {
				continue;
			}
			BlockPos cell = BlockPos.of(entry.getKey());
			BlockPos center = new BlockPos(cell.getX() * 32 + 16, cell.getY() * 32 + 16, cell.getZ() * 32 + 16);
			if (!level.isLoaded(center)) {
				continue;
			}
			boolean deep = center.getY() < 1100;
			if (level.getEntitiesOfClass(WanderingTrader.class, new AABB(center).inflate(48)).isEmpty()) {
				BlockPos spot = deep ? floor(level, center) : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
				if (spot != null) {
					WanderingTrader trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
					if (trader != null) {
						trader.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
						trader.setDespawnDelay(24000);
						if (deep) {
							deep_offers(trader);
						}
						level.addFreshEntity(trader);
					}
				}
			}
			if (level.getRandom().nextInt(3) == 0) {
				decorate(level, center, deep);
			}
		}
	}

	private static BlockPos floor(ServerLevel level, BlockPos center) {
		for (int dy = 0; dy < 16; dy++) {
			for (int sign : new int[] {1, -1}) {
				BlockPos pos = center.offset(0, dy * sign, 0);
				if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).isSolid()) {
					return pos;
				}
			}
		}
		return null;
	}

	public static void deep_offers(WanderingTrader trader) {
		var offers = trader.getOffers();
		offers.clear();
		offers.add(new MerchantOffer(new ItemCost(Items.TORCH, 32), new ItemStack(Content.item("raw_tin"), 6), 8, 4, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.ICE, 16), new ItemStack(Content.item("ember_root"), 4), 6, 6, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.BREAD, 12), new ItemStack(Content.item("glowcap"), 4), 8, 4, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Content.item("salt"), 12), new ItemStack(Content.item("raw_cobalt"), 2), 4, 8, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.PACKED_ICE, 8), new ItemStack(Content.item("mantle_iron_chunk"), 3), 4, 8, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.COOKED_BEEF, 16), new ItemStack(Content.item("hush_moss"), 3), 4, 8, 0.05F));
	}

	public static boolean clear_area(ServerLevel level, BlockPos spot, int radius, int height) {
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				if (!level.getBlockState(spot.offset(x, -1, z)).isSolid()) {
					return false;
				}
				for (int y = 0; y < height; y++) {
					if (!level.getBlockState(spot.offset(x, y, z)).isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public static boolean stall(ServerLevel level, BlockPos spot) {
		if (!clear_area(level, spot, 1, 4)) {
			return false;
		}
		for (int x = -1; x <= 1; x += 2) {
			for (int z = -1; z <= 1; z += 2) {
				level.setBlockAndUpdate(spot.offset(x, 0, z), Blocks.SPRUCE_FENCE.defaultBlockState());
				level.setBlockAndUpdate(spot.offset(x, 1, z), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlockAndUpdate(spot.offset(x, 2, z), (x + z) % 2 == 0 ? Blocks.WOOL.red().defaultBlockState() : Blocks.WOOL.white().defaultBlockState());
			}
		}
		level.setBlockAndUpdate(spot, Blocks.BARREL.defaultBlockState());
		level.setBlockAndUpdate(spot.offset(0, 0, 1), Blocks.SPRUCE_SLAB.defaultBlockState());
		return true;
	}

	public static boolean well(ServerLevel level, BlockPos spot) {
		if (!clear_area(level, spot, 1, 4)) {
			return false;
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				boolean middle = x == 0 && z == 0;
				level.setBlockAndUpdate(spot.offset(x, -1, z), middle ? Blocks.WATER.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
				if (!middle) {
					level.setBlockAndUpdate(spot.offset(x, 0, z), Math.abs(x) + Math.abs(z) == 2 ? Blocks.COBBLESTONE_WALL.defaultBlockState() : Blocks.COBBLESTONE_SLAB.defaultBlockState());
				}
				level.setBlockAndUpdate(spot.offset(x, 2, z), Blocks.SPRUCE_SLAB.defaultBlockState());
			}
		}
		for (int x = -1; x <= 1; x += 2) {
			for (int z = -1; z <= 1; z += 2) {
				level.setBlockAndUpdate(spot.offset(x, 1, z), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		return true;
	}

	private static void decorate(ServerLevel level, BlockPos center, boolean deep) {
		var random = level.getRandom();
		BlockPos spot = deep ? floor(level, center.offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8)) : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8));
		if (spot == null || !level.getBlockState(spot).isAir() || !level.getBlockState(spot.below()).isSolid()) {
			return;
		}
		int roll = random.nextInt(10);
		if (roll == 0 && well(level, spot) || roll == 1 && stall(level, spot)) {
			return;
		}
		switch (random.nextInt(3)) {
			case 0 -> {
				level.setBlockAndUpdate(spot, Blocks.SPRUCE_FENCE.defaultBlockState());
				level.setBlockAndUpdate(spot.above(), Blocks.SPRUCE_FENCE.defaultBlockState());
				level.setBlockAndUpdate(spot.above(2), Blocks.LANTERN.defaultBlockState());
			}
			case 1 -> level.setBlockAndUpdate(spot, Blocks.SPRUCE_STAIRS.defaultBlockState());
			default -> {
				level.setBlockAndUpdate(spot, Blocks.BARREL.defaultBlockState());
			}
		}
	}
}
