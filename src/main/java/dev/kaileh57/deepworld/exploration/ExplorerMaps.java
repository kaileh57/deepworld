package dev.kaileh57.deepworld.exploration;

import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.hud.Hud;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class ExplorerMaps {
	public static final List<BlockPos> terminals = List.of(new BlockPos(0, 1212, 0), new BlockPos(250, 1212, 0), new BlockPos(-450, 1212, 0), new BlockPos(0, 1212, 1000), new BlockPos(0, 1212, -2000));

	private ExplorerMaps() {
	}

	public static BlockPos target(ServerLevel level, BlockPos from, String kind) {
		if (kind.equals("terminal")) {
			BlockPos best = null;
			for (BlockPos pos : terminals) {
				if (best == null || pos.distSqr(from) < best.distSqr(from)) {
					best = pos;
				}
			}
			return best;
		}
		Landmarks.Entry entry = Landmarks.nearest(level.getServer(), from, kind);
		return entry == null ? null : entry.pos();
	}

	public static ItemStack draw(ServerLevel level, BlockPos target, String kind) {
		ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, target, "+", MapDecorationTypes.RED_X);
		String name = kind.equals("terminal") ? "deepline station" : Landmarks.display(kind);
		map.set(DataComponents.CUSTOM_NAME, Component.literal("map to the " + name).withStyle(style -> style.withItalic(false)));
		map.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("x " + target.getX() + ", y " + target.getY() + ", z " + target.getZ()))));
		return map;
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer server_player) || !(level instanceof ServerLevel server_level)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Content.item("landmark_map"))) {
				return InteractionResult.PASS;
			}
			String kind = CookData.tag(stack).getStringOr("map", "terminal");
			BlockPos target = target(server_level.getServer().overworld(), player.blockPosition(), kind);
			if (target == null) {
				Hud.notice(server_player, Component.literal("the map is blank. nobody has come near one yet"));
				return InteractionResult.SUCCESS_SERVER;
			}
			ItemStack map = draw(server_level.getServer().overworld(), target, kind);
			stack.shrink(1);
			if (!player.getInventory().add(map)) {
				player.drop(map, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			Hud.notice(server_player, Component.literal("the map unfolds"));
			return InteractionResult.SUCCESS_SERVER;
		});
	}
}
