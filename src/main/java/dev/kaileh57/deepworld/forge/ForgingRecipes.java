package dev.kaileh57.deepworld.forge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kaileh57.deepworld.DeepWorld;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ForgingRecipes {
	public record Input(Item item, int count) {
	}

	public record Recipe(Identifier id, String station, List<Input> inputs, Item result, int result_count, int time) {
		public boolean matches(Container container, int first_slot, int slot_count) {
			for (Input input : inputs) {
				int found = 0;
				for (int slot = first_slot; slot < first_slot + slot_count; slot++) {
					ItemStack stack = container.getItem(slot);
					if (stack.is(input.item())) {
						found += stack.getCount();
					}
				}
				if (found < input.count()) {
					return false;
				}
			}
			return true;
		}

		public void consume(Container container, int first_slot, int slot_count) {
			for (Input input : inputs) {
				int left = input.count();
				for (int slot = first_slot; slot < first_slot + slot_count && left > 0; slot++) {
					ItemStack stack = container.getItem(slot);
					if (stack.is(input.item())) {
						int take = Math.min(left, stack.getCount());
						stack.shrink(take);
						left -= take;
					}
				}
			}
		}
	}

	private static List<Recipe> recipes = List.of();

	private ForgingRecipes() {
	}

	public static List<Recipe> all() {
		return recipes;
	}

	public static void reload(MinecraftServer server) {
		List<Recipe> loaded = new ArrayList<>();
		Map<Identifier, Resource> found = server.getResourceManager().listResources("forging", path -> path.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
			try (Reader reader = entry.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				List<Input> inputs = new ArrayList<>();
				for (JsonElement element : json.getAsJsonArray("inputs")) {
					JsonObject input = element.getAsJsonObject();
					inputs.add(new Input(BuiltInRegistries.ITEM.getValue(Identifier.parse(input.get("item").getAsString())), input.get("count").getAsInt()));
				}
				JsonObject result = json.getAsJsonObject("result");
				loaded.add(new Recipe(entry.getKey(), json.get("station").getAsString(), inputs, BuiltInRegistries.ITEM.getValue(Identifier.parse(result.get("item").getAsString())), result.get("count").getAsInt(), json.get("time").getAsInt()));
			} catch (Exception error) {
				DeepWorld.logger.error("bad forging recipe {}", entry.getKey(), error);
			}
		}
		recipes = List.copyOf(loaded);
		DeepWorld.logger.info("loaded {} forging recipes", recipes.size());
	}

	public static Recipe find(String station, Container container, int first_slot, int slot_count) {
		for (Recipe recipe : recipes) {
			if (recipe.station().equals(station) && recipe.matches(container, first_slot, slot_count)) {
				return recipe;
			}
		}
		return null;
	}
}
