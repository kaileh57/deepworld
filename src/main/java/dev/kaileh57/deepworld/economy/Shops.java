package dev.kaileh57.deepworld.economy;

import com.mojang.serialization.Codec;
import dev.kaileh57.deepworld.DeepWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class Shops extends SavedData {
	public Map<String, List<String>> listings = new HashMap<>();
	public static final Codec<Shops> codec = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).xmap(map -> {
		Shops s = new Shops();
		map.forEach((key, value) -> s.listings.put(key, new ArrayList<>(value)));
		return s;
	}, s -> s.listings);
	public static final SavedDataType<Shops> type = new SavedDataType<>(DeepWorld.id("shops"), Shops::new, codec, null);

	public static Shops get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(type);
	}

	public static String key(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private static String describe(ItemStack stack) {
		return stack.getCount() + " " + stack.getHoverName().getString().toLowerCase();
	}

	public static void record(MinecraftServer server, BlockPos pos, ShopBlockEntity shop) {
		List<String> lines = new ArrayList<>();
		lines.add(shop.owner_name);
		for (int trade = 0; trade < ShopBlockEntity.trades; trade++) {
			ItemStack sell = shop.offer(trade);
			ItemStack price = shop.price(trade);
			if (!sell.isEmpty() && !price.isEmpty()) {
				lines.add(describe(sell) + " for " + describe(price));
			}
		}
		Shops shops = get(server);
		shops.listings.put(key(pos), lines);
		shops.setDirty();
	}

	public static void forget(MinecraftServer server, BlockPos pos) {
		Shops shops = get(server);
		if (shops.listings.remove(key(pos)) != null) {
			shops.setDirty();
		}
	}
}
