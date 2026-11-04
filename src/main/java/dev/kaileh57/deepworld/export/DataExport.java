package dev.kaileh57.deepworld.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.kaileh57.deepworld.DeepWorld;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class DataExport {
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private DataExport() {
	}

	public static void register() {
		String target = System.getProperty("deepworld.export");
		if (target == null) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				write_all(Path.of(target));
				DeepWorld.logger.info("exported data to {}", target);
			} catch (IOException error) {
				DeepWorld.logger.error("export failed", error);
			}
			server.halt(false);
		});
	}

	private static void write_all(Path out_dir) throws IOException {
		Files.createDirectories(out_dir);
		write(out_dir.resolve("items.json"), export_items(out_dir));
		write(out_dir.resolve("blocks.json"), export_blocks(out_dir));
		JsonObject meta = new JsonObject();
		meta.addProperty("mod_version", FabricLoader.getInstance().getModContainer(DeepWorld.mod_id).orElseThrow().getMetadata().getVersion().getFriendlyString());
		meta.addProperty("minecraft_version", SharedConstants.getCurrentVersion().name());
		write(out_dir.resolve("meta.json"), meta);
	}

	private static JsonArray export_items(Path out_dir) throws IOException {
		JsonArray entries = new JsonArray();
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier item_id = BuiltInRegistries.ITEM.getKey(item);
			if (!item_id.getNamespace().equals(DeepWorld.mod_id)) {
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("id", item_id.toString());
			entry.addProperty("name", new ItemStack(item).getHoverName().getString());
			JsonObject info = new JsonObject();
			info.addProperty("stack size", item.getDefaultMaxStackSize());
			entry.add("info", info);
			copy_icon(out_dir, "items", "item", item_id).ifPresent(icon -> entry.addProperty("icon", icon));
			entries.add(entry);
		}
		return entries;
	}

	private static JsonArray export_blocks(Path out_dir) throws IOException {
		JsonArray entries = new JsonArray();
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier block_id = BuiltInRegistries.BLOCK.getKey(block);
			if (!block_id.getNamespace().equals(DeepWorld.mod_id)) {
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("id", block_id.toString());
			entry.addProperty("name", block.getName().getString());
			copy_icon(out_dir, "blocks", "block", block_id).ifPresent(icon -> entry.addProperty("icon", icon));
			entries.add(entry);
		}
		return entries;
	}

	private static Optional<String> copy_icon(Path out_dir, String kind, String texture_dir, Identifier entry_id) throws IOException {
		String texture_path = "assets/" + entry_id.getNamespace() + "/textures/" + texture_dir + "/" + entry_id.getPath() + ".png";
		Optional<Path> source = FabricLoader.getInstance().getModContainer(DeepWorld.mod_id).orElseThrow().findPath(texture_path);
		if (source.isEmpty()) {
			return Optional.empty();
		}
		String icon = "icons/" + kind + "/" + entry_id.getPath() + ".png";
		Path target = out_dir.resolve(icon);
		Files.createDirectories(target.getParent());
		Files.copy(source.get(), target, StandardCopyOption.REPLACE_EXISTING);
		return Optional.of(icon);
	}

	private static void write(Path file, Object value) throws IOException {
		Files.writeString(file, gson.toJson(value) + "\n");
	}
}
