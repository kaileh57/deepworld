package dev.kaileh57.deepworld.exploration;

import dev.kaileh57.deepworld.DeepWorld;
import dev.kaileh57.deepworld.content.Content;
import dev.kaileh57.deepworld.cooking.CookData;
import dev.kaileh57.deepworld.guild.Guild;
import dev.kaileh57.deepworld.smithing.Smithing;
import dev.kaileh57.deepworld.world.Layers;
import eu.pb4.sgui.api.elements.BookElementBuilder;
import eu.pb4.sgui.api.gui.BookGui;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class Exploration {
	private Exploration() {
	}

	public static void register() {
		net.minecraft.core.Registry.register(BuiltInRegistries.FEATURE_TYPE, DeepWorld.id("landmark"), LandmarkFeature.codec);
		ExplorationTests.register();
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer server_player)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(Content.item("survey_kit"))) {
				survey(server_player);
				return InteractionResult.SUCCESS_SERVER;
			}
			if (stack.is(Content.item("logbook"))) {
				open_logbook(server_player);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer server_player) || !(level instanceof ServerLevel server_level)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (stack.is(Content.item("dowsing_rod")) && state.is(Smithing.dowsable)) {
				var tag = CookData.tag(stack);
				tag.putString("target", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
				CookData.set_tag(stack, tag);
				server_player.sendOverlayMessage(Component.literal("the rod now seeks " + state.getBlock().getName().getString().toLowerCase()));
				return InteractionResult.SUCCESS_SERVER;
			}
			if (state.is(Blocks.DISPENSER) && is_landmark(server_level.getServer(), "pump", pos)) {
				if (!stack.is(Items.COPPER_INGOT) || stack.getCount() < 4) {
					server_player.sendOverlayMessage(Component.literal("the pump needs 4 copper ingots to repair"));
					return InteractionResult.SUCCESS_SERVER;
				}
				stack.shrink(4);
				int drained = drain(server_level, pos, 22);
				dev.kaileh57.deepworld.hud.Hud.notice(server_player, Component.literal("the pump shudders back to life. " + drained + " blocks drained"));
				Logbook.add(server_player, "repaired a drowned pump station");
				forget(server_level.getServer(), "pump", pos);
				return InteractionResult.SUCCESS_SERVER;
			}
			if (state.is(Blocks.NOTE_BLOCK)) {
				Landmarks.Entry cathedral = near(server_level.getServer(), "cathedral", pos, 8);
				if (cathedral != null) {
					server_level.getServer().execute(() -> check_cathedral(server_level, server_player, cathedral.pos()));
				}
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Layers.Layer layer = Layers.at(player.getBlockY());
				Logbook.add(player, "died at y " + player.getBlockY() + (layer == null ? "" : " in the " + layer.name()) + ": " + source.getLocalizedDeathMessage(player).getString());
			}
		});
	}

	public static boolean is_landmark(MinecraftServer server, String kind, BlockPos pos) {
		return Landmarks.all(server).stream().anyMatch(e -> e.kind().equals(kind) && e.pos().equals(pos));
	}

	private static Landmarks.Entry near(MinecraftServer server, String kind, BlockPos pos, int radius) {
		for (Landmarks.Entry entry : Landmarks.all(server)) {
			if (entry.kind().equals(kind) && entry.pos().distSqr(pos) <= radius * radius) {
				return entry;
			}
		}
		return null;
	}

	private static void forget(MinecraftServer server, String kind, BlockPos pos) {
		Landmarks landmarks = Landmarks.get(server);
		landmarks.entries.removeIf(e -> e.kind().equals(kind) && e.pos().equals(pos));
		landmarks.setDirty();
	}

	public static int drain(ServerLevel level, BlockPos center, int radius) {
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -12, -radius), center.offset(radius, 12, radius))) {
			if (level.getFluidState(pos).isEmpty()) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.WATER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				count++;
			}
		}
		return count;
	}

	public static int[] cathedral_notes(BlockPos origin) {
		long hash = origin.asLong() * 0x9E3779B97F4A7C15L;
		int[] notes = new int[4];
		for (int i = 0; i < 4; i++) {
			notes[i] = (int) Math.floorMod(hash >> (i * 8), 25);
		}
		return notes;
	}

	public static void check_cathedral(ServerLevel level, ServerPlayer player, BlockPos origin) {
		int[][] spots = {{-2, -3}, {2, -3}, {-2, 3}, {2, 3}};
		int[] targets = cathedral_notes(origin);
		for (int i = 0; i < 4; i++) {
			BlockState state = level.getBlockState(origin.offset(spots[i][0], 0, spots[i][1]));
			if (!state.is(Blocks.NOTE_BLOCK) || state.getValue(NoteBlock.NOTE) != targets[i]) {
				return;
			}
		}
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, 5), origin.offset(1, 2, 5))) {
			level.destroyBlock(pos, false);
		}
		level.playSound(null, origin, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.6F);
		dev.kaileh57.deepworld.hud.Hud.notice(player, Component.literal("the crystals ring together and the vault wall shatters").withStyle(ChatFormatting.LIGHT_PURPLE));
		Logbook.add(player, "opened the crystal cathedral vault");
		forget(level.getServer(), "cathedral", origin);
	}

	public static void survey(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		Layers.Layer layer = Layers.at(pos.getY());
		String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath().replace('_', ' ')).orElse("?");
		player.sendSystemMessage(Component.literal("survey: y " + pos.getY() + ", " + (layer == null ? "surface" : layer.name() + " (layer " + layer.number() + ")") + ", " + biome).withStyle(ChatFormatting.AQUA));
		int range = Guild.focused(player, "archivist") ? 1000 : 500;
		int shown = 0;
		for (Landmarks.Entry entry : Landmarks.all(level.getServer())) {
			if (entry.kind().equals("pump") || entry.kind().equals("cathedral")) {
				continue;
			}
			double distance = Math.sqrt(entry.pos().distSqr(pos));
			if (distance > range) {
				continue;
			}
			Vec3 delta = Vec3.atCenterOf(entry.pos()).subtract(player.position());
			String direction = (Math.abs(delta.z) > Math.abs(delta.x) ? (delta.z < 0 ? "north" : "south") : (delta.x < 0 ? "west" : "east"));
			player.sendSystemMessage(Component.literal("  " + Landmarks.display(entry.kind()) + ": " + (int) distance + " blocks " + direction + ", y " + entry.pos().getY()).withStyle(ChatFormatting.GRAY));
			if (++shown >= 6) {
				break;
			}
		}
		if (shown == 0) {
			player.sendSystemMessage(Component.literal("  no known landmarks nearby").withStyle(ChatFormatting.GRAY));
		}
		Guild.add(player, "surveys", 1);
	}

	private static void open_logbook(ServerPlayer player) {
		List<String> list = Logbook.of(player);
		BookElementBuilder book = new BookElementBuilder().setTitle("logbook").setAuthor(player.getGameProfile().name());
		StringBuilder page = new StringBuilder();
		int lines = 0;
		if (list.isEmpty()) {
			page.append("nothing yet. go deeper.");
		}
		for (String entry : list) {
			page.append(entry).append("\n\n");
			lines += 3;
			if (lines >= 11) {
				book.addPage(Component.literal(page.toString()));
				page = new StringBuilder();
				lines = 0;
			}
		}
		if (!page.isEmpty()) {
			book.addPage(Component.literal(page.toString()));
		}
		new BookGui(player, book).open();
	}

	public static void tick(MinecraftServer server, long tick) {
		if (tick % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack rod = player.getMainHandItem();
			if (rod.is(Content.item("dowsing_rod"))) {
				dowse(player, rod);
			}
			if (tick % 100 == 0) {
				count_pages(player);
			}
		}
	}

	private static void dowse(ServerPlayer player, ItemStack rod) {
		String target = CookData.tag(rod).getStringOr("target", "");
		if (target.isEmpty()) {
			player.sendOverlayMessage(Component.literal("right-click an ore with the rod to tune it"));
			return;
		}
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(target));
		int range = Guild.focused(player, "archivist") ? 48 : 24;
		ServerLevel level = player.level();
		BlockPos center = player.blockPosition();
		BlockPos best = null;
		double best_distance = Double.MAX_VALUE;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = -range; x <= range; x += 1) {
			for (int y = -range / 2; y <= range / 2; y++) {
				for (int z = -range; z <= range; z += 1) {
					cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					if (level.getBlockState(cursor).is(block)) {
						double distance = x * x + y * y + z * z;
						if (distance < best_distance) {
							best_distance = distance;
							best = cursor.immutable();
						}
					}
				}
			}
		}
		if (best == null) {
			player.sendOverlayMessage(Component.literal("the rod is silent").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		Vec3 look = player.getLookAngle().normalize();
		Vec3 to = Vec3.atCenterOf(best).subtract(player.getEyePosition()).normalize();
		double alignment = look.dot(to);
		double distance = Math.sqrt(best_distance);
		float volume = (float) Math.max(0.1, (alignment + 1) / 2) * (float) Math.max(0.2, 1 - distance / range);
		level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, volume * 1.5F, (float) (0.6 + alignment * 0.8));
		String word = alignment > 0.9 ? "it pulls hard" : alignment > 0.5 ? "warmer" : alignment > 0 ? "faint" : "colder";
		player.sendOverlayMessage(Component.literal(word + " · " + (int) distance + "m").withStyle(alignment > 0.9 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
	}

	private static void count_pages(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (content == null) {
				continue;
			}
			String title = content.title().raw();
			if (!title.startsWith("deepline journal, page ")) {
				continue;
			}
			String number = title.substring("deepline journal, page ".length());
			if (Guild.counter(player, "page_" + number) == 0) {
				Guild.add(player, "page_" + number, 1);
				Guild.add(player, "pages_found", 1);
				Logbook.add(player, "found journal page " + number);
			}
		}
	}
}
